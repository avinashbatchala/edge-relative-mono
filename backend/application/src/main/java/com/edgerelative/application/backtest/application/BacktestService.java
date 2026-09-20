package com.edgerelative.application.backtest.application;

import com.edgerelative.application.backtest.domain.BacktestMetrics;
import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.domain.EquityPoint;
import com.edgerelative.application.backtest.engine.BacktestContextProvider;
import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.backtest.persistence.BacktestRepository;
import com.edgerelative.application.catalog.application.CatalogNotFoundException;
import com.edgerelative.application.catalog.application.StrategyCatalogService;
import com.edgerelative.application.feature.policy.FeatureProperties;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.risk.persistence.RiskPolicyRepository;
import com.edgerelative.application.strategy.application.StrategyParametersProvider;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts, runs, and reads backtests. A run faithful to production supplies no market/stock context
 * (the producers are not wired), so the strategy fails closed and the run yields no trades; that is
 * reported rather than fabricated. Equivalent specifications map to the same run key, making start
 * requests idempotent.
 */
@Service
public class BacktestService {

    private static final String STRICT_CONTEXT = "STRICT_PRODUCTION";

    private final BacktestRepository repository;
    private final BacktestEngine engine;
    private final StrategyCatalogService strategyCatalog;
    private final StrategyParametersProvider strategyParameters;
    private final RiskPolicyRepository riskPolicies;
    private final FeatureProperties featureProperties;
    private final JsonMapper json;
    private final BacktestExecutor executor;
    private final Clock clock;

    public BacktestService(
            BacktestRepository repository,
            BacktestEngine engine,
            StrategyCatalogService strategyCatalog,
            StrategyParametersProvider strategyParameters,
            RiskPolicyRepository riskPolicies,
            FeatureProperties featureProperties,
            JsonMapper json,
            BacktestExecutor backtestExecutor,
            Clock clock) {
        this.repository = repository;
        this.engine = engine;
        this.strategyCatalog = strategyCatalog;
        this.strategyParameters = strategyParameters;
        this.riskPolicies = riskPolicies;
        this.featureProperties = featureProperties;
        this.json = json;
        this.executor = backtestExecutor;
        this.clock = clock;
    }

    public BacktestRunRow start(BacktestRunRequest request) {
        Resolved resolved = resolve(request);
        long datasetVersionId = repository.ensureDataset(resolved.spec.datasetCode(), resolved.spec.datasetChecksum());
        String specJson = json.writeValueAsString(resolved.canonicalSpec);
        BacktestRepository.RunIds ids = repository.createRun(
                resolved.spec.runKey(), specJson, json.writeValueAsString(costModel(resolved.spec)),
                resolved.spec.startDate(), resolved.spec.endDate(), resolved.spec.instrumentIds().size(),
                resolved.spec.startingCapital(), resolved.spec.currency(), resolved.spec.seed(),
                "operator", datasetVersionId, resolved.strategyVersionId(), resolved.riskPolicyVersionId());
        BacktestRunRow run = repository.findRun(resolved.spec.runKey()).orElseThrow();
        if ("CREATED".equals(run.status())) {
            executor.submit(() -> execute(resolved.spec, ids));
        }
        return run;
    }

    public boolean cancel(String runKey) {
        return repository.cancel(runKey);
    }

    public List<BacktestRunRow> list(int limit) {
        return repository.listRuns(Math.clamp(limit, 1, 200));
    }

    public Optional<BacktestRunRow> get(String runKey) {
        return repository.findRun(runKey);
    }

    public List<BacktestTrade> trades(String runKey, String symbol, int limit, int offset) {
        return repository.findTrades(runKey, symbol == null || symbol.isBlank() ? null : symbol,
                Math.clamp(limit, 1, 500), Math.max(0, offset));
    }

    public List<EquityPoint> equity(String runKey) {
        return repository.findEquity(runKey);
    }

    private void execute(BacktestSpec spec, BacktestRepository.RunIds ids) {
        try {
            repository.markRunning(ids.experimentRunId(), ids.backtestRunId());
            BacktestResult result = engine.run(spec, BacktestContextProvider.strict(), (processed, total, through) -> {
                if (repository.isCancelled(spec.runKey())) {
                    throw new java.util.concurrent.CancellationException("run cancelled");
                }
                repository.updateProgress(ids.experimentRunId(), processed, through, total);
            });
            repository.insertTrades(ids.backtestRunId(), result.trades());
            repository.insertEquity(ids.backtestRunId(), result.equityPoints());
            repository.insertRejections(ids.backtestRunId(), result.rejections());
            Map<String, Object> metrics = BacktestMetrics.compute(
                    result, spec.startingCapital(), barsPerYear(spec.timeframe()));
            repository.complete(ids.experimentRunId(), ids.backtestRunId(), metrics);
        } catch (java.util.concurrent.CancellationException cancelled) {
            // Status already set by cancel(); leave partial ledger, never label SUCCEEDED.
        } catch (RuntimeException failure) {
            repository.fail(ids.experimentRunId(), ids.backtestRunId(),
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    private Resolved resolve(BacktestRunRequest request) {
        if (request.symbols() == null || request.symbols().isEmpty()) {
            throw new BacktestValidationException("Select at least one symbol.");
        }
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw new BacktestValidationException("Choose a valid start and end date.");
        }
        if (request.timeframe() == null || request.timeframe().isBlank()) {
            throw new BacktestValidationException("Choose a supported timeframe.");
        }
        if (request.startingCapital() == null || request.startingCapital().signum() <= 0) {
            throw new BacktestValidationException("Starting capital must be positive.");
        }
        List<String> missing = new ArrayList<>();
        List<Long> instrumentIds = repository.findInstrumentIds(request.symbols());
        if (instrumentIds.size() != request.symbols().size()) {
            for (String symbol : request.symbols()) {
                if (repository.findInstrumentIds(List.of(symbol)).isEmpty()) {
                    missing.add(symbol);
                }
            }
            throw new BacktestValidationException("Unknown instruments: " + missing);
        }
        // Prefer an explicit catalog version (reproducible), then a research preset, then configured
        // production parameters. Fail closed with a clear reason otherwise.
        Long strategyVersionId = request.strategyVersionId();
        StrategyParameters parameters;
        if (strategyVersionId != null) {
            try {
                parameters = strategyCatalog.resolveVersion(strategyVersionId).parameters();
            } catch (CatalogNotFoundException notFound) {
                throw new BacktestValidationException(notFound.getMessage());
            }
        } else {
            parameters = BacktestPresets.strategy(request.strategyPreset())
                    .or(() -> strategyParameters.parameters())
                    .orElseThrow(() -> new BacktestValidationException(
                            "Select a strategy version or research preset (or configure production strategy parameters)."));
            strategyVersionId = repository.strategyVersionId() == 0L ? null : repository.strategyVersionId();
        }
        ResolvedRisk resolvedRisk;
        if (request.riskPolicyVersionId() != null) {
            resolvedRisk = riskPolicies.resolveById(request.riskPolicyVersionId())
                    .map(policy -> new ResolvedRisk(policy.policy(), policy.riskPolicyVersionId()))
                    .orElseThrow(() -> new BacktestValidationException(
                            "Risk policy version " + request.riskPolicyVersionId() + " not found."));
        } else {
            resolvedRisk = BacktestPresets.risk(request.riskPreset())
                    .map(risk -> new ResolvedRisk(risk, null))
                    .or(() -> riskPolicies.resolve(request.riskPolicyCode())
                            .map(policy -> new ResolvedRisk(policy.policy(), policy.riskPolicyVersionId())))
                    .orElseThrow(() -> new BacktestValidationException(
                            "Select a risk policy version or research preset (or an available risk policy code)."));
        }
        RiskPolicy policy = resolvedRisk.policy();
        Long marketInstrumentId = resolveSingle(request.marketSymbol());
        Long sectorInstrumentId = resolveSingle(request.sectorSymbol());

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("symbols", request.symbols());
        canonical.put("start", request.startDate());
        canonical.put("end", request.endDate());
        canonical.put("timeframe", request.timeframe());
        canonical.put("capital", request.startingCapital());
        canonical.put("currency", request.currency());
        canonical.put("strategyVersionId", strategyVersionId);
        canonical.put("riskPolicyVersionId", resolvedRisk.riskPolicyVersionId());
        canonical.put("strategy", parameters.parameterSetId() + "/" + parameters.parameterVersion());
        // Include resolved content so a materially different preset produces a different run.
        canonical.put("strategyFamilies", parameters.enabledFamilies().stream().map(Enum::name).sorted().toList());
        canonical.put("riskPolicy", policy.code() + "/" + policy.version());
        canonical.put("riskBaseFraction", policy.trade() == null ? null : policy.trade().baseRiskFraction());
        canonical.put("contextSource", contextSource(request));
        canonical.put("strict", request.strictProducers());
        canonical.put("warmup", request.warmupBars());
        canonical.put("seed", request.seed());
        String canonicalJson = json.writeValueAsString(canonical);
        // Retries of a FAILED/CANCELLED run create a new linked run; in-flight and succeeded runs are
        // idempotent. Historical results are never overwritten.
        String runKey = nextRunKey(canonicalJson);

        BacktestSpec spec = new BacktestSpec(
                runKey, instrumentIds, request.symbols(), request.startDate(), request.endDate(),
                request.timeframe(), request.dailyTimeframe(), request.startingCapital(), request.currency(),
                request.strictProducers(), parameters, policy, featureProperties.toPolicy(),
                execution(request.execution()), costs(request.costs()),
                request.endOfRun() == null || request.endOfRun().isBlank()
                        ? BacktestSpec.EndOfRunPolicy.MARK_TO_MARKET
                        : BacktestSpec.EndOfRunPolicy.valueOf(request.endOfRun()),
                request.warmupBars(), request.seed(),
                BacktestEngine.ENGINE_REVISION, marketInstrumentId, sectorInstrumentId, "CANONICAL_M5",
                datasetChecksum(request, instrumentIds), contextSource(request));
        canonical.put("runKey", runKey);
        return new Resolved(spec, canonical, strategyVersionId, resolvedRisk.riskPolicyVersionId());
    }

    private String nextRunKey(String canonicalJson) {
        String base = UUID.nameUUIDFromBytes(("backtest:" + canonicalJson).getBytes(StandardCharsets.UTF_8)).toString();
        String candidate = base;
        for (int attempt = 2; attempt < 1000; attempt++) {
            java.util.Optional<String> status = repository.runStatus(candidate);
            if (status.isEmpty() || !("FAILED".equals(status.get()) || "CANCELLED".equals(status.get()))) {
                return candidate;
            }
            candidate = UUID.nameUUIDFromBytes(("backtest:" + canonicalJson + "#" + attempt).getBytes(StandardCharsets.UTF_8))
                    .toString();
        }
        return candidate;
    }

    private static BacktestSpec.ContextSource contextSource(BacktestRunRequest request) {
        String value = request.contextSource();
        if (value == null || value.isBlank()) {
            return BacktestSpec.ContextSource.STRICT_PRODUCTION;
        }
        try {
            return BacktestSpec.ContextSource.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw new BacktestValidationException("Unknown context source '" + value + "'.");
        }
    }

    private Long resolveSingle(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        List<Long> ids = repository.findInstrumentIds(List.of(symbol));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String datasetChecksum(BacktestRunRequest request, List<Long> instrumentIds) {
        return UUID.nameUUIDFromBytes((instrumentIds + ":" + request.startDate() + ":" + request.endDate() + ":"
                + request.timeframe()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static BacktestSpec.ExecutionPolicy execution(BacktestRunRequest.ExecutionRequest request) {
        if (request == null) {
            return new BacktestSpec.ExecutionPolicy("backtest-exec-v1", 0, new BigDecimal("2"), new BigDecimal("5"),
                    BigDecimal.ONE, 1, BacktestSpec.SessionCutoff.NEW_ENTRY_CUTOFF,
                    BacktestSpec.ExecutionPolicy.AmbiguityPolicy.STOP_FIRST_CONSERVATIVE, true);
        }
        return new BacktestSpec.ExecutionPolicy(
                request.version() == null ? "backtest-exec-v1" : request.version(),
                0L,
                bd(request.halfSpreadBps(), "2"),
                bd(request.adverseSlippageBps(), "5"),
                request.participationRate() == null ? BigDecimal.ONE : BigDecimal.valueOf(request.participationRate()),
                request.orderExpiryBars() == null ? 1 : request.orderExpiryBars(),
                BacktestSpec.SessionCutoff.NEW_ENTRY_CUTOFF,
                BacktestSpec.ExecutionPolicy.AmbiguityPolicy.valueOf(
                        request.ambiguityPolicy() == null ? "STOP_FIRST_CONSERVATIVE" : request.ambiguityPolicy()),
                request.allowOvernight() == null || request.allowOvernight());
    }

    /** A zero schedule is an explicit user assumption, never presented as a real fee model. */
    private static BacktestSpec.CostSchedule costs(BacktestRunRequest.CostRequest request) {
        String version = request == null || request.version() == null ? "USER_ZERO_ASSUMPTION" : request.version();
        if (request == null) {
            return new BacktestSpec.CostSchedule(version, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, true);
        }
        return new BacktestSpec.CostSchedule(version, bd(request.brokerageBuyBps(), "0"),
                bd(request.brokerageSellBps(), "0"), bd(request.sttSellBps(), "0"), bd(request.exchangeBps(), "0"),
                bd(request.gstBps(), "0"), bd(request.sebiBps(), "0"), bd(request.stampDutyBuyBps(), "0"),
                bd(request.otherBuyBps(), "0"), bd(request.otherSellBps(), "0"), true);
    }

    private static BigDecimal bd(Double value, String fallback) {
        return value == null ? new BigDecimal(fallback) : BigDecimal.valueOf(value);
    }

    private Map<String, Object> costModel(BacktestSpec spec) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", spec.costSchedule().version());
        value.put("isExplicitAssumption", "USER_ZERO_ASSUMPTION".equals(spec.costSchedule().version()));
        return value;
    }

    private double barsPerYear(String timeframe) {
        return switch (timeframe == null ? "" : timeframe) {
            case "M1" -> 252 * 375;
            case "M5" -> 252 * 75;
            case "M15" -> 252 * 25;
            case "D1" -> 252;
            default -> 0;
        };
    }

    private record Resolved(
            BacktestSpec spec,
            Map<String, Object> canonicalSpec,
            Long strategyVersionId,
            Long riskPolicyVersionId) {
    }

    private record ResolvedRisk(com.edgerelative.application.risk.domain.RiskPolicy policy, Long riskPolicyVersionId) {
    }

    /** Thrown when the requested configuration cannot be run as specified. */
    public static class BacktestValidationException extends RuntimeException {
        public BacktestValidationException(String message) {
            super(message);
        }
    }
}
