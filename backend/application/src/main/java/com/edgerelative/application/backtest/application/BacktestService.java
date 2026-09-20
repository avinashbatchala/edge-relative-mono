package com.edgerelative.application.backtest.application;

import com.edgerelative.application.backtest.domain.BacktestMetrics;
import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.domain.EquityPoint;
import com.edgerelative.application.backtest.engine.BacktestContextProvider;
import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.backtest.persistence.BacktestRepository;
import com.edgerelative.application.feature.policy.FeatureProperties;
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
    private final StrategyParametersProvider strategyParameters;
    private final RiskPolicyRepository riskPolicies;
    private final FeatureProperties featureProperties;
    private final JsonMapper json;
    private final BacktestExecutor executor;
    private final Clock clock;

    public BacktestService(
            BacktestRepository repository,
            BacktestEngine engine,
            StrategyParametersProvider strategyParameters,
            RiskPolicyRepository riskPolicies,
            FeatureProperties featureProperties,
            JsonMapper json,
            BacktestExecutor backtestExecutor,
            Clock clock) {
        this.repository = repository;
        this.engine = engine;
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
                "operator", datasetVersionId);
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
        StrategyParameters parameters = strategyParameters.parameters()
                .orElseThrow(() -> new BacktestValidationException(
                        "Strategy parameters are not configured; the backtest would be unsupported."));
        RiskPolicyRepository.ResolvedPolicy policy = riskPolicies.resolve(request.riskPolicyCode())
                .orElseThrow(() -> new BacktestValidationException(
                        "Risk policy '" + request.riskPolicyCode() + "' is not available."));
        Long marketInstrumentId = resolveSingle(request.marketSymbol());
        Long sectorInstrumentId = resolveSingle(request.sectorSymbol());

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("symbols", request.symbols());
        canonical.put("start", request.startDate());
        canonical.put("end", request.endDate());
        canonical.put("timeframe", request.timeframe());
        canonical.put("capital", request.startingCapital());
        canonical.put("currency", request.currency());
        canonical.put("strategy", parameters.parameterSetId() + "/" + parameters.parameterVersion());
        canonical.put("riskPolicy", policy.policy().code() + "/" + policy.policy().version());
        canonical.put("strict", request.strictProducers());
        canonical.put("warmup", request.warmupBars());
        canonical.put("seed", request.seed());
        String canonicalJson = json.writeValueAsString(canonical);
        String runKey = UUID.nameUUIDFromBytes(("backtest:" + canonicalJson).getBytes(StandardCharsets.UTF_8)).toString();

        BacktestSpec spec = new BacktestSpec(
                runKey, instrumentIds, request.symbols(), request.startDate(), request.endDate(),
                request.timeframe(), request.dailyTimeframe(), request.startingCapital(), request.currency(),
                request.strictProducers(), parameters, policy.policy(), featureProperties.toPolicy(),
                execution(request.execution()), costs(request.costs()),
                BacktestSpec.EndOfRunPolicy.valueOf(request.endOfRun()), request.warmupBars(), request.seed(),
                BacktestEngine.ENGINE_REVISION, marketInstrumentId, sectorInstrumentId, "CANONICAL_M5",
                datasetChecksum(request, instrumentIds));
        canonical.put("runKey", runKey);
        return new Resolved(spec, canonical);
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

    private record Resolved(BacktestSpec spec, Map<String, Object> canonicalSpec) {
    }

    /** Thrown when the requested configuration cannot be run as specified. */
    public static class BacktestValidationException extends RuntimeException {
        public BacktestValidationException(String message) {
            super(message);
        }
    }
}
