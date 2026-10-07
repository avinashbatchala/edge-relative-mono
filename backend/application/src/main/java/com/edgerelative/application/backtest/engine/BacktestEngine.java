package com.edgerelative.application.backtest.engine;

import com.edgerelative.application.backtest.domain.BacktestRejection;
import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.domain.EquityPoint;
import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.history.query.HistoricalDataReader;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.risk.domain.ConstraintStatus;
import com.edgerelative.application.strategy.domain.DependencyStatus;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.risk.domain.RiskCandidate;
import com.edgerelative.application.risk.domain.RiskContext;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.risk.domain.RiskDecisionType;
import com.edgerelative.application.risk.domain.RiskEvaluator;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.risk.domain.RiskReasonCode;
import com.edgerelative.application.risk.domain.RiskState;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.SetupState;
import com.edgerelative.application.strategy.domain.StrategyEngine;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import com.edgerelative.application.strategy.context.DecisionContextAssembler;
import com.edgerelative.application.strategy.context.DecisionContextAssembler.DecisionContext;
import com.edgerelative.application.strategy.context.DecisionContextPolicy;
import com.edgerelative.application.strategy.domain.StrategyIdentity;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.tradeplan.domain.PlanLineage;
import com.edgerelative.application.tradeplan.domain.TradePlan;
import com.edgerelative.application.tradeplan.domain.TradePlanFactory;
import com.edgerelative.application.tradeplan.domain.TradePlanPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Chronological, deterministic replay that reuses the production pure engines: {@link FeatureEngine},
 * {@link StrategyEngine}, {@link RiskEvaluator}, and {@link TradePlanFactory}. Only data delivery,
 * clock, execution, and run-scoped accounting differ. No simulated activity touches production
 * tables or a broker.
 */
public final class BacktestEngine {

    public static final String ENGINE_REVISION = "er-backtest-engine-v1";

    /** Completed candles supplied to the decision-context assembler at each anchor. */
    private static final int CONTEXT_HISTORY_BARS = 60;

    private final HistoricalDataReader reader;
    private final FeatureEngine featureEngine;
    private final StrategyEngine strategyEngine;
    private final RiskEvaluator riskEvaluator;
    private final NseTradingCalendar calendar;

    public BacktestEngine(
            HistoricalDataReader reader,
            FeatureEngine featureEngine,
            StrategyEngine strategyEngine,
            RiskEvaluator riskEvaluator,
            NseTradingCalendar calendar) {
        this.reader = reader;
        this.featureEngine = featureEngine;
        this.strategyEngine = strategyEngine;
        this.riskEvaluator = riskEvaluator;
        this.calendar = calendar;
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(long processed, long total, Instant through);
    }

    /**
     * Optional per-anchor hook used by the forensic timeline. It observes the exact feature snapshot
     * and long/short strategy results the run used at that anchor; it cannot influence the decision.
     */
    @FunctionalInterface
    public interface AnchorListener {
        void onAnchor(
                long instrumentId,
                Instant anchor,
                AggregatedCandle bar,
                FeatureSnapshot snapshot,
                StrategyEvaluationResult longResult,
                StrategyEvaluationResult shortResult);
    }

    public BacktestResult run(BacktestSpec spec, ProgressListener listener) {
        return run(spec, listener, null);
    }

    public BacktestResult run(BacktestSpec spec, ProgressListener listener, AnchorListener anchorListener) {
        DecisionContextPolicy contextPolicy = spec.contextSource() == BacktestSpec.ContextSource.DERIVED_RESEARCH
                ? DecisionContextPolicy.research()
                : DecisionContextPolicy.strict();
        DecisionContextAssembler assembler = new DecisionContextAssembler(contextPolicy);
        Map<Long, List<AggregatedCandle>> series = loadSeries(spec, spec.timeframe());
        Map<Long, List<AggregatedCandle>> dailySeries = loadSeries(spec, spec.dailyTimeframe());
        List<AggregatedCandle> marketCandles = spec.marketInstrumentId() == null
                ? List.of()
                : series.getOrDefault(spec.marketInstrumentId(), List.of());
        List<AggregatedCandle> dailyMarket = spec.marketInstrumentId() == null
                ? List.of()
                : dailySeries.getOrDefault(spec.marketInstrumentId(), List.of());
        Map<Long, NavigableMap<Instant, FeatureSnapshot>> m5 = features(spec, series, marketCandles, spec.timeframe());
        Map<Long, NavigableMap<Instant, FeatureSnapshot>> d1 =
                features(spec, dailySeries, dailyMarket, spec.dailyTimeframe());
        Map<Long, NavigableMap<Instant, DecisionContextAssembler.EmaStructure>> ema =
                emaStructures(spec, series, contextPolicy);
        // Pre-index every loaded series once so per-anchor lookups are O(1) and history slicing is
        // O(window) rather than an O(n) rescan (which made long M1 replays quadratic).
        Map<Long, CandleIndex> candlesByInstrument = new HashMap<>();
        series.forEach((id, bars) -> candlesByInstrument.put(id, new CandleIndex(bars)));

        Set<Instant> timeline = new TreeSet<>();
        m5.values().forEach(byAnchor -> timeline.addAll(byAnchor.keySet()));

        // Warm-up candles initialise features but must not produce trades or performance before the
        // requested window. Only anchors inside [start, end] are processed and mark equity.
        Instant windowStart = spec.startDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant windowEndExclusive =
                spec.endDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        Map<String, long[]> counts = new LinkedHashMap<>();
        Portfolio portfolio = new Portfolio(spec, counts, calendar);
        // Run-scoped setup lifecycle, keyed by instrument and direction. Passing a prior state lets
        // the production engine apply legal transitions and age/trigger expiry instead of cold-starting
        // every bar, so FORMING -> NEAR_TRIGGER -> VALID accumulates across the replay.
        Map<String, StrategyEvaluationInput.PriorSetup> priorState = new HashMap<>();
        long total = (long) timeline.size() * Math.max(1, spec.instrumentIds().size());
        long processed = 0;
        List<EquityPoint> equity = new ArrayList<>();
        for (Instant anchor : timeline) {
            if (anchor.isBefore(windowStart) || !anchor.isBefore(windowEndExclusive)) {
                continue;
            }
            List<Long> ordered = new ArrayList<>(spec.instrumentIds());
            ordered.sort(Comparator.naturalOrder());
            for (Long instrumentId : ordered) {
                processSymbol(spec, assembler, portfolio, instrumentId, anchor, m5.get(instrumentId),
                        d1.get(instrumentId), ema.get(instrumentId), candlesByInstrument.get(instrumentId),
                        counts, priorState, anchorListener);
                processed++;
            }
            portfolio.markEquity(anchor, equity);
            if (listener != null) {
                listener.onProgress(processed, total, anchor);
            }
        }
        portfolio.finish(spec, series, timeline, equity);
        return new BacktestResult(
                portfolio.trades(), equity, portfolio.rejections(), processed, total, snapshot(counts));
    }

    private static void increment(Map<String, long[]> counts, String key) {
        counts.computeIfAbsent(key, ignored -> new long[1])[0]++;
    }

    private static Map<String, Long> snapshot(Map<String, long[]> counts) {
        Map<String, Long> result = new TreeMap<>();
        counts.forEach((key, value) -> result.put(key, value[0]));
        return result;
    }

    private Map<Long, List<AggregatedCandle>> loadSeries(BacktestSpec spec, String timeframe) {
        Map<Long, List<AggregatedCandle>> result = new LinkedHashMap<>();
        Set<Long> ids = new HashSet<>(spec.instrumentIds());
        if (spec.marketInstrumentId() != null) {
            ids.add(spec.marketInstrumentId());
        }
        if (spec.sectorInstrumentId() != null) {
            ids.add(spec.sectorInstrumentId());
        }
        Instant from = com.edgerelative.application.backtest.domain.BacktestWindow.warmupStart(
                spec.startDate(), spec.warmupSessions(), calendar);
        Instant to = com.edgerelative.application.backtest.domain.BacktestWindow.endExclusive(spec);
        for (Long id : ids) {
            List<AggregatedCandle> candles = reader.replayCandles(id, timeframe, from, to, 2_000_000).stream()
                    .filter(AggregatedCandle::complete)
                    .toList();
            result.put(id, candles);
        }
        return result;
    }

    private Map<Long, NavigableMap<Instant, FeatureSnapshot>> features(
            BacktestSpec spec,
            Map<Long, List<AggregatedCandle>> series,
            List<AggregatedCandle> marketCandles,
            String timeframe) {
        Map<Long, NavigableMap<Instant, FeatureSnapshot>> result = new LinkedHashMap<>();
        for (Long instrumentId : spec.instrumentIds()) {
            List<AggregatedCandle> subject = series.getOrDefault(instrumentId, List.of());
            if (subject.isEmpty()) {
                result.put(instrumentId, new TreeMap<>());
                continue;
            }
            BenchmarkIdentity benchmark = new BenchmarkIdentity(
                    spec.marketInstrumentId(), "NIFTY50", null, null, null,
                    spec.sectorInstrumentId(), null);
            List<AggregatedCandle> sectorCandles = spec.sectorInstrumentId() == null
                    ? List.of()
                    : series.getOrDefault(spec.sectorInstrumentId(), List.of());
            FeatureContext context = new FeatureContext(
                    instrumentId, timeframe, subject, marketCandles, sectorCandles, benchmark,
                    spec.featurePolicy(), new com.edgerelative.application.feature.policy.FeatureVersions(spec.featurePolicy()),
                    calendar, "NIFTY50", null);
            NavigableMap<Instant, FeatureSnapshot> byAnchor = new TreeMap<>();
            for (FeatureSnapshot snapshot : featureEngine.snapshots(context)) {
                if (snapshot.anchorTimestamp() != null) {
                    byAnchor.put(snapshot.anchorTimestamp(), snapshot);
                }
            }
            result.put(instrumentId, byAnchor);
        }
        return result;
    }

    private Map<Long, NavigableMap<Instant, DecisionContextAssembler.EmaStructure>> emaStructures(
            BacktestSpec spec,
            Map<Long, List<AggregatedCandle>> series,
            DecisionContextPolicy policy) {
        Map<Long, NavigableMap<Instant, DecisionContextAssembler.EmaStructure>> result = new LinkedHashMap<>();
        for (Long instrumentId : spec.instrumentIds()) {
            DecisionContextAssembler.EmaTracker tracker =
                    new DecisionContextAssembler.EmaTracker(policy.emaFastLength(), policy.emaSlowLength());
            NavigableMap<Instant, DecisionContextAssembler.EmaStructure> byAnchor = new TreeMap<>();
            for (AggregatedCandle candle : series.getOrDefault(instrumentId, List.of())) {
                DecisionContextAssembler.EmaStructure structure = tracker.update(candle.close());
                if (candle.closeTime() != null) {
                    byAnchor.put(candle.closeTime(), structure);
                }
            }
            result.put(instrumentId, byAnchor);
        }
        return result;
    }

    private void processSymbol(
            BacktestSpec spec,
            DecisionContextAssembler assembler,
            Portfolio portfolio,
            long instrumentId,
            Instant anchor,
            NavigableMap<Instant, FeatureSnapshot> m5,
            NavigableMap<Instant, FeatureSnapshot> d1,
            NavigableMap<Instant, DecisionContextAssembler.EmaStructure> ema,
            CandleIndex index,
            Map<String, long[]> counts,
            Map<String, StrategyEvaluationInput.PriorSetup> priorState,
            AnchorListener anchorListener) {
        AggregatedCandle bar = index == null ? null : index.barAt(anchor);
        if (bar == null) {
            return;
        }
        increment(counts, "anchorsProcessed");
        portfolio.onBar(spec, instrumentId, bar);
        if (m5 == null || d1 == null) {
            return;
        }
        FeatureSnapshot snapshot = m5.get(anchor);
        if (snapshot == null) {
            return;
        }
        if (portfolio.hasPositionOrPending(instrumentId)) {
            // No averaging/pyramiding: the strategy is not re-evaluated while a position or order is
            // live, but the forensic timeline still observes the anchor.
            if (anchorListener != null) {
                anchorListener.onAnchor(instrumentId, anchor, bar, snapshot, null, null);
            }
            return;
        }
        Map.Entry<Instant, FeatureSnapshot> dailyEntry = d1.floorEntry(anchor);
        FeatureSnapshot daily = dailyEntry == null ? null : dailyEntry.getValue();
        List<AggregatedCandle> history = index.history(anchor, CONTEXT_HISTORY_BARS);
        DecisionContextAssembler.EmaStructure emaStructure = ema == null
                ? DecisionContextAssembler.EmaStructure.absent()
                : ema.getOrDefault(anchor, DecisionContextAssembler.EmaStructure.absent());
        DecisionContext context = assembler.assemble(snapshot, daily, bar, history, emaStructure);

        String longKey = instrumentId + ":LONG";
        String shortKey = instrumentId + ":SHORT";
        StrategyEvaluationInput.PriorSetup priorLong =
                priorState.getOrDefault(longKey, StrategyEvaluationInput.PriorSetup.none());
        StrategyEvaluationInput.PriorSetup priorShort =
                priorState.getOrDefault(shortKey, StrategyEvaluationInput.PriorSetup.none());
        StrategyEvaluationResult longResult = strategyEngine.evaluate(
                input(spec, context, instrumentId, anchor, snapshot, bar, priorLong),
                spec.strategyParameters(), Direction.LONG);
        StrategyEvaluationResult shortResult = strategyEngine.evaluate(
                input(spec, context, instrumentId, anchor, snapshot, bar, priorShort),
                spec.strategyParameters(), Direction.SHORT);
        priorState.put(longKey, advance(priorLong, longResult));
        priorState.put(shortKey, advance(priorShort, shortResult));
        increment(counts, "setup_" + longResult.setupState().name());
        increment(counts, "setup_" + shortResult.setupState().name());
        if (longResult.setupState() != SetupState.VALID) {
            longResult.reasonCodes().forEach(code -> increment(counts, "longReason_" + code.name()));
        }
        if (shortResult.setupState() != SetupState.VALID) {
            shortResult.reasonCodes().forEach(code -> increment(counts, "shortReason_" + code.name()));
        }
        if (anchorListener != null) {
            anchorListener.onAnchor(instrumentId, anchor, bar, snapshot, longResult, shortResult);
        }
        StrategyEvaluationResult chosen = longResult.setupState() == SetupState.VALID
                ? longResult
                : shortResult.setupState() == SetupState.VALID ? shortResult : null;
        if (chosen == null) {
            return;
        }
        increment(counts, "setupValid");
        RiskDecisionProposal proposal = riskEvaluator.evaluate(
                candidate(spec, context, instrumentId, anchor, snapshot, chosen, bar),
                portfolio.context(spec, anchor, snapshot),
                spec.riskPolicy());
        increment(counts, "risk_" + proposal.decision().name());
        if (!proposal.decision().authorizesNewRisk()) {
            portfolio.recordRejection(new BacktestRejection(
                    anchor, instrumentId, chosen.direction(),
                    proposal.primaryReason() == null ? "UNKNOWN" : proposal.primaryReason().name(),
                    proposal.explanation()));
            return;
        }
        TradePlan plan = TradePlanFactory.create(
                proposal, 0L,
                lineage(spec, context, instrumentId, anchor, snapshot, chosen, bar, proposal),
                new TradePlanPolicy("backtest", 1, "backtest", null, null, null,
                        spec.execution().entryMethod() == BacktestSpec.ExecutionPolicy.EntryMethod.TRIGGER_LIMIT
                                ? "TRIGGER_LIMIT" : "REFERENCE_PRICE",
                        spec.execution().targetMethod() == BacktestSpec.ExecutionPolicy.TargetMethod.R_MULTIPLE
                                ? "R_MULTIPLE" : "STRUCTURAL_UNRESOLVED",
                        spec.execution().targetR(),
                        "TICK_BUFFER"),
                anchor, null);
        increment(counts, "plansCreated");
        portfolio.submit(spec, instrumentId, chosen.direction(), plan);
        // The setup is consumed by the entry; clear its lifecycle so the next setup starts fresh.
        priorState.remove(longKey);
        priorState.remove(shortKey);
    }

    /** Advances a run-scoped setup lifecycle from one evaluation result (DD-02 setup lifecycle). */
    private static StrategyEvaluationInput.PriorSetup advance(
            StrategyEvaluationInput.PriorSetup prior, StrategyEvaluationResult result) {
        SetupState state = result.setupState();
        if (state.isTerminal()) {
            // A terminated setup is consumed; the next active state starts a fresh instance.
            return StrategyEvaluationInput.PriorSetup.none();
        }
        // NONE is the absence of a setup, not an ageing one, so its counter must not accumulate
        // (otherwise the engine's age-expiry would trap the state at NONE forever).
        int barsInState = state == SetupState.NONE
                ? 0
                : (prior.state() == state ? prior.barsInState() + 1 : 1);
        Instant triggerTime = prior.triggerTime();
        if (triggerTime == null && result.trigger() != null
                && (state == SetupState.VALID || state == SetupState.MISSED)) {
            triggerTime = result.evaluationTimestamp();
        }
        int barsSinceTrigger = triggerTime == null ? 0 : prior.barsSinceTrigger() + 1;
        return new StrategyEvaluationInput.PriorSetup(
                result.setupInstanceId(),
                state,
                result.setupFamily(),
                result.direction(),
                barsInState,
                barsSinceTrigger,
                triggerTime,
                result.trigger() == null ? null : result.trigger().triggerLevel(),
                result.invalidation() == null ? null : result.invalidation().invalidationLevel());
    }

    private StrategyEvaluationInput input(
            BacktestSpec spec,
            DecisionContext context,
            long instrumentId,
            Instant anchor,
            FeatureSnapshot snapshot,
            AggregatedCandle bar,
            StrategyEvaluationInput.PriorSetup prior) {
        StrategyEvaluationInput.MarketContext market = context.market();
        StrategyEvaluationInput.StockContext stock = context.stock();
        LocalDate sessionDate = calendar.sessionDate(anchor);
        boolean tradingDay = calendar.isTradingDay(sessionDate);
        StrategyParameters params = spec.strategyParameters();
        // Session windows come from the exchange calendar plus configured blackout/cutoff minutes;
        // they are never assumed open. Blackout/cutoff are empty when their configured minutes are 0.
        boolean inSession = calendar.isSessionMinute(anchor);
        boolean openingBlackout = false;
        boolean entryCutoffReached = false;
        if (tradingDay && inSession) {
            Instant open = calendar.sessionOpen(sessionDate);
            Instant close = calendar.sessionClose(sessionDate);
            openingBlackout = params.openingBlackoutMinutes() > 0
                    && anchor.isBefore(open.plus(Duration.ofMinutes(params.openingBlackoutMinutes())));
            entryCutoffReached = params.entryCutoffMinutesBeforeClose() > 0
                    && !anchor.isBefore(close.minus(Duration.ofMinutes(params.entryCutoffMinutesBeforeClose())));
        }
        boolean entryWindowOpen = inSession && !openingBlackout && !entryCutoffReached;
        List<DependencyStatus> dependencies = new ArrayList<>();
        dependencies.add(dep("market.bias", market.available() && market.bias() != null));
        dependencies.add(dep("market.regime", market.regime() != null));
        dependencies.add(dep("stock.dailyStructure", stock.dailyStructure() != null));
        dependencies.add(dep("stock.liquidity", stock.medianTradedValue() != null));
        dependencies.add(dep("stock.technicalVoid", stock.technicalVoidAtr() != null));
        dependencies.add(dep("stock.eventRisk", stock.eventRiskBlocked() != null));
        dependencies.add(dep("rrs.m5", number(snapshot, FeatureKeys.RRS_RAW) != null));
        dependencies.add(dep("atr.m5", number(snapshot, FeatureKeys.ATR) != null));

        return new StrategyEvaluationInput(
                UUID.nameUUIDFromBytes((spec.runKey() + ":" + instrumentId + ":" + anchor).getBytes(StandardCharsets.UTF_8)).toString(),
                anchor,
                sessionDate,
                StrategyIdentity.STRATEGY_VERSION,
                spec.strategyParameters().parameterSetId(),
                instrumentId,
                0L,
                "mo:" + anchor,
                new StrategyEvaluationInput.SessionContext(
                        anchor, tradingDay, entryWindowOpen, openingBlackout, entryCutoffReached,
                        NseTradingCalendar.VERSION),
                market,
                context.sector(),
                stock,
                new StrategyEvaluationInput.CompletedCandle(
                        bar.openTime(), bar.closeTime(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()),
                dependencies,
                prior);
    }

    private RiskCandidate candidate(
            BacktestSpec spec,
            DecisionContext context,
            long instrumentId,
            Instant anchor,
            FeatureSnapshot snapshot,
            StrategyEvaluationResult result,
            AggregatedCandle bar) {
        StrategyEvaluationInput.MarketContext market = context.market();
        StrategyEvaluationInput.StockContext stock = context.stock();
        return new RiskCandidate(
                // Run-scoped so replaying the same window never collides on persisted keys.
                spec.runKey() + ":cand:" + instrumentId + ":" + anchor,
                "bt",
                1L,
                1L,
                com.edgerelative.application.risk.domain.TradingMode.BACKTEST,
                result.setupInstanceId() == null ? null : result.setupInstanceId().toString(),
                0L,
                StrategyIdentity.STRATEGY_ID,
                StrategyIdentity.STRATEGY_VERSION,
                0,
                instrumentId,
                spec.symbols().isEmpty() ? String.valueOf(instrumentId) : symbol(spec, instrumentId),
                result.direction(),
                snapshot != null && number(snapshot, FeatureKeys.ATR) != null ? new BigDecimal("0.05") : null,
                1L,
                bar.close(),
                result.invalidation() == null ? null : result.invalidation().invalidationLevel(),
                result.invalidation() == null ? null : result.invalidation().basis(),
                anchor,
                market.regime(),
                market.available(),
                stock.eventRiskBlocked() != null,
                stock.eventRiskBlocked(),
                context.sector().sectorId(),
                context.sector().sectorCode(),
                null,
                5.0,
                1_000_000.0,
                5_000_000.0,
                null,
                null,
                true,
                "VALID",
                snapshot.featureSchemaVersion(),
                "backtest",
                number(snapshot, FeatureKeys.ATR));
    }

    private PlanLineage lineage(
            BacktestSpec spec,
            DecisionContext context,
            long instrumentId,
            Instant anchor,
            FeatureSnapshot snapshot,
            StrategyEvaluationResult result,
            AggregatedCandle bar,
            RiskDecisionProposal proposal) {
        return new PlanLineage(
                0L,
                result.setupInstanceId() == null ? null : result.setupInstanceId().toString(),
                result.setupFamily() == null ? null : result.setupFamily().name(),
                "VALID",
                1L,
                1L,
                0L,
                "backtest",
                "mo:" + anchor,
                instrumentId,
                spec.symbols().isEmpty() ? String.valueOf(instrumentId) : symbol(spec, instrumentId),
                result.direction(),
                StrategyIdentity.STRATEGY_ID,
                StrategyIdentity.STRATEGY_VERSION,
                0,
                new BigDecimal("0.05"),
                1L,
                context.market().regime(),
                context.sector().sectorCode(),
                result.setupInstanceId(),
                anchor,
                result.trigger() == null ? null : result.trigger().triggerType(),
                result.trigger() == null ? null : result.trigger().triggerLevel(),
                result.trigger() == null ? null : result.trigger().entryExtensionAtr(),
                result.invalidation() == null ? null : result.invalidation().invalidationType(),
                proposal.structuralInvalidation(),
                result.invalidation() == null ? null : result.invalidation().basis());
    }

    private static String symbol(BacktestSpec spec, long instrumentId) {
        int index = spec.instrumentIds().indexOf(instrumentId);
        return index >= 0 && index < spec.symbols().size() ? spec.symbols().get(index) : String.valueOf(instrumentId);
    }

    private static DependencyStatus dep(String code, boolean available) {
        return new DependencyStatus(code, true,
                available ? DependencyStatus.DependencyState.AVAILABLE : DependencyStatus.DependencyState.MISSING);
    }

    private static Double number(FeatureSnapshot snapshot, String key) {
        if (snapshot == null) {
            return null;
        }
        FeatureValue value = snapshot.feature(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.value() : null;
    }

    /**
     * Pre-indexed candle series for one instrument: O(1) bar lookup by anchor (close time) and an
     * O(window) history slice. Built once per run so the chronological replay stays linear.
     */
    private static final class CandleIndex {
        private final List<AggregatedCandle> bars;
        private final Map<Instant, Integer> positionByCloseTime;

        CandleIndex(List<AggregatedCandle> bars) {
            this.bars = bars;
            this.positionByCloseTime = new HashMap<>(Math.max(16, bars.size() * 2));
            for (int i = 0; i < bars.size(); i++) {
                Instant closeTime = bars.get(i).closeTime();
                if (closeTime != null) {
                    positionByCloseTime.put(closeTime, i);
                }
            }
        }

        AggregatedCandle barAt(Instant anchor) {
            Integer index = positionByCloseTime.get(anchor);
            return index == null ? null : bars.get(index);
        }

        List<AggregatedCandle> history(Instant anchor, int max) {
            Integer index = positionByCloseTime.get(anchor);
            if (index == null) {
                return List.of();
            }
            int from = Math.max(0, index - max + 1);
            return bars.subList(from, index + 1);
        }
    }

    /** Run-scoped portfolio: cash, one position per symbol, pending orders, reservations. */
    private static final class Portfolio {
        private final BigDecimal startingCapital;
        private BigDecimal realized = BigDecimal.ZERO;
        private final Map<Long, OpenPosition> positions = new LinkedHashMap<>();
        private final Map<Long, PendingOrder> pending = new LinkedHashMap<>();
        private final List<BacktestTrade> trades = new ArrayList<>();
        private final List<BacktestRejection> rejections = new ArrayList<>();
        private final Map<String, long[]> counts;
        private final NseTradingCalendar calendar;

        private Portfolio(
                BacktestSpec spec, Map<String, long[]> counts,
                NseTradingCalendar calendar) {
            this.startingCapital = spec.startingCapital();
            this.counts = counts;
            this.calendar = calendar;
        }

        boolean hasPositionOrPending(long instrumentId) {
            return positions.containsKey(instrumentId) || pending.containsKey(instrumentId);
        }

        void submit(BacktestSpec spec, long instrumentId, Direction direction, TradePlan plan) {
            pending.put(instrumentId, new PendingOrder(direction, plan, 0));
            increment(counts, "ordersSubmitted");
        }

        void recordRejection(BacktestRejection rejection) {
            rejections.add(rejection);
        }

        List<BacktestTrade> trades() {
            return trades;
        }

        List<BacktestRejection> rejections() {
            return rejections;
        }

        void onBar(BacktestSpec spec, long instrumentId, AggregatedCandle bar) {
            PendingOrder order = pending.get(instrumentId);
            if (order != null) {
                order.barsWaited++;
                if (order.barsWaited > spec.execution().orderExpiryBars()) {
                    increment(counts, "ordersExpired");
                    pending.remove(instrumentId);
                } else if (fillEntry(spec, instrumentId, order, bar)) {
                    increment(counts, "fills");
                    pending.remove(instrumentId);
                }
            }
            OpenPosition position = positions.get(instrumentId);
            if (position != null) {
                if (bar.close() != null) {
                    position.markPrice = bar.close();
                }
                manage(spec, instrumentId, position, bar);
                position = positions.get(instrumentId);
                // Intraday V1: no accidental overnight carry. Flatten before the session close.
                if (position != null && !spec.execution().allowOvernight()
                        && bar.closeTime() != null
                        && bar.closeTime().equals(calendar.sessionClose(calendar.sessionDate(bar.closeTime())))) {
                    close(spec, instrumentId, position, bar, adjustedFill(spec, position.direction, bar.close(), false),
                            "SESSION_FLATTEN");
                }
            }
        }

        private boolean fillEntry(BacktestSpec spec, long instrumentId, PendingOrder order, AggregatedCandle bar) {
            BigDecimal reference = bar.open();
            if (reference == null) {
                return false;
            }
            // Trigger entries only fill if the bar actually reaches the plan's entry trigger; a market
            // entry fills at the open. This separates "chase the open" from "wait for the breakout".
            if (spec.execution().entryMethod() == BacktestSpec.ExecutionPolicy.EntryMethod.TRIGGER_LIMIT) {
                BigDecimal trigger = order.plan().entryTriggerPrice();
                if (trigger == null || bar.high() == null || bar.low() == null
                        || !reachesTrigger(order.direction(), bar, trigger)) {
                    return false;
                }
                reference = order.direction().isLong() ? bar.open().max(trigger) : bar.open().min(trigger);
            }
            long executable = (long) Math.floor(bar.volume() * spec.execution().participationRate().doubleValue());
            long quantity = Math.min(order.plan().plannedQuantity(), Math.max(0, executable));
            if (quantity <= 0) {
                increment(counts, "zeroFills");
                return false;
            }
            BigDecimal fill = adjustedFill(spec, order.direction(), reference, true);
            BigDecimal initialRisk = order.plan().plannedRisk()
                    .divide(BigDecimal.valueOf(Math.max(1, quantity)), 8, RoundingMode.HALF_UP);
            positions.put(instrumentId, new OpenPosition(
                    instrumentId, order.direction(), quantity, fill, bar.openTime(), order.plan().protectiveStop(),
                    order.plan().targetReference(), initialRisk, order.plan().planKey(), order.plan().decisionKey(),
                    order.plan().entryPattern(), 0));
            return true;
        }

        private static boolean reachesTrigger(Direction direction, AggregatedCandle bar, BigDecimal trigger) {
            return direction.isLong()
                    ? bar.high().compareTo(trigger) >= 0
                    : bar.low().compareTo(trigger) <= 0;
        }

        private void manage(BacktestSpec spec, long instrumentId, OpenPosition position, AggregatedCandle bar) {
            if (bar.high() == null || bar.low() == null) {
                return;
            }
            boolean longSide = position.direction.isLong();
            BigDecimal stop = position.stop;
            BigDecimal target = position.target;
            // Excursions are measured over the whole holding period, independent of the chosen exit.
            BigDecimal favorable = longSide ? bar.high().subtract(position.entryPrice)
                    : position.entryPrice.subtract(bar.low());
            BigDecimal adverse = longSide ? position.entryPrice.subtract(bar.low())
                    : bar.high().subtract(position.entryPrice);
            position.mfePrice = position.mfePrice.max(favorable);
            position.maePrice = position.maePrice.max(adverse);
            boolean stopHit = longSide ? bar.low().compareTo(stop) <= 0 : bar.high().compareTo(stop) >= 0;
            boolean targetHit = target != null
                    && (longSide ? bar.high().compareTo(target) >= 0 : bar.low().compareTo(target) <= 0);
            if (stopHit && targetHit) {
                position.ambiguousBars++;
                if (spec.execution().ambiguityPolicy()
                        == BacktestSpec.ExecutionPolicy.AmbiguityPolicy.SKIP_AMBIGUOUS) {
                    // Unresolvable from OHLC; leave the position open and let a later bar decide.
                    return;
                }
                if (spec.execution().ambiguityPolicy()
                        == BacktestSpec.ExecutionPolicy.AmbiguityPolicy.TARGET_FIRST_OPTIMISTIC) {
                    BigDecimal reference = longSide ? bar.open().max(target) : bar.open().min(target);
                    close(spec, instrumentId, position, bar,
                            adjustedFill(spec, position.direction, reference, false), "TARGET");
                    return;
                }
                // STOP_FIRST_CONSERVATIVE (default) falls through to the stop branch.
            }
            if (stopHit) {
                BigDecimal reference = longSide ? bar.open().min(stop) : bar.open().max(stop);
                close(spec, instrumentId, position, bar, adjustedFill(spec, position.direction, reference, false), "STOP");
                return;
            }
            if (targetHit) {
                BigDecimal reference = longSide ? bar.open().max(target) : bar.open().min(target);
                close(spec, instrumentId, position, bar, adjustedFill(spec, position.direction, reference, false), "TARGET");
            }
        }

        private void close(
                BacktestSpec spec, long instrumentId, OpenPosition position, AggregatedCandle bar, BigDecimal exitPrice, String reason) {
            BigDecimal gross = grossPnl(position, exitPrice, position.quantity);
            BigDecimal entryNotional = position.entryPrice.multiply(BigDecimal.valueOf(position.quantity));
            BigDecimal exitNotional = exitPrice.multiply(BigDecimal.valueOf(position.quantity));
            Map<String, BigDecimal> costs = costs(spec.costSchedule(), entryNotional, exitNotional);
            BigDecimal explicit = costs.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal net = gross.subtract(explicit);
            realized = realized.add(net);
            BigDecimal initialRiskTotal = position.initialRiskPerUnit.multiply(BigDecimal.valueOf(position.quantity));
            BigDecimal realizedR = initialRiskTotal.signum() == 0
                    ? null
                    : net.divide(initialRiskTotal, 4, RoundingMode.HALF_UP);
            increment(counts, "exits");
            increment(counts, "exit_" + reason);
            trades.add(new BacktestTrade(
                    UUID.nameUUIDFromBytes((position.planKey + ":" + bar.closeTime()).getBytes(StandardCharsets.UTF_8)).toString(),
                    instrumentId, symbol(spec, instrumentId), position.direction, position.entryPattern,
                    position.entryAt, position.entryPrice, bar.closeTime(), exitPrice, position.quantity,
                    position.initialRiskPerUnit, gross.setScale(2, RoundingMode.HALF_UP), explicit.setScale(2, RoundingMode.HALF_UP),
                    net.setScale(2, RoundingMode.HALF_UP), realizedR,
                    Duration.between(position.entryAt, bar.closeTime()).getSeconds(), reason,
                    position.ambiguousBars, costs, position.planKey, position.decisionKey,
                    excursionR(position.mfePrice, position.initialRiskPerUnit),
                    excursionR(position.maePrice, position.initialRiskPerUnit)));
            positions.remove(instrumentId);
        }

        private static BigDecimal excursionR(BigDecimal excursion, BigDecimal riskPerUnit) {
            if (excursion == null || riskPerUnit == null || riskPerUnit.signum() == 0) {
                return null;
            }
            return excursion.divide(riskPerUnit, 4, RoundingMode.HALF_UP);
        }

        void markEquity(Instant at, List<EquityPoint> equity) {
            BigDecimal unrealized = BigDecimal.ZERO;
            BigDecimal grossExposure = BigDecimal.ZERO;
            BigDecimal netExposure = BigDecimal.ZERO;
            for (OpenPosition position : positions.values()) {
                BigDecimal notional = position.markPrice.multiply(BigDecimal.valueOf(position.quantity));
                grossExposure = grossExposure.add(notional);
                netExposure = netExposure.add(position.direction.isLong() ? notional : notional.negate());
                unrealized = unrealized.add(grossPnl(position, position.markPrice, position.quantity));
            }
            BigDecimal equityValue = startingCapital.add(realized).add(unrealized).setScale(2, RoundingMode.HALF_UP);
            BigDecimal highWater = equity.isEmpty()
                    ? startingCapital.max(equityValue)
                    : equity.get(equity.size() - 1).highWater().max(equityValue);
            BigDecimal drawdown = highWater.subtract(equityValue).max(BigDecimal.ZERO);
            Double drawdownPct = highWater.signum() == 0
                    ? null
                    : drawdown.multiply(BigDecimal.valueOf(100)).divide(highWater, 4, RoundingMode.HALF_UP).doubleValue();
            equity.add(new EquityPoint(
                    at, equityValue, startingCapital.add(realized), grossExposure, netExposure, highWater,
                    drawdown.setScale(2, RoundingMode.HALF_UP), drawdownPct, positions.size()));
        }

        void finish(BacktestSpec spec, Map<Long, List<AggregatedCandle>> series, Set<Instant> timeline, List<EquityPoint> equity) {
            List<OpenPosition> open = new ArrayList<>(positions.values());
            for (OpenPosition position : open) {
                List<AggregatedCandle> candles = series.getOrDefault(position.instrumentId, List.of());
                AggregatedCandle lastBar = candles.isEmpty() ? null : candles.get(candles.size() - 1);
                BigDecimal last = lastBar == null ? position.markPrice : lastBar.close();
                if (spec.endOfRun() == BacktestSpec.EndOfRunPolicy.LIQUIDATE_AT_CLOSE && lastBar != null) {
                    close(spec, position.instrumentId, position, lastBar,
                            adjustedFill(spec, position.direction, last, false), "END_OF_RUN_LIQUIDATION");
                    continue;
                }
                BigDecimal gross = grossPnl(position, last, position.quantity);
                increment(counts, "openAtEnd");
                trades.add(new BacktestTrade(
                        UUID.nameUUIDFromBytes((position.planKey + ":open").getBytes(StandardCharsets.UTF_8)).toString(),
                        position.instrumentId, symbol(spec, position.instrumentId), position.direction, position.entryPattern,
                        position.entryAt, position.entryPrice, null, null, position.quantity, position.initialRiskPerUnit,
                        gross.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2), gross.setScale(2, RoundingMode.HALF_UP),
                        null, null, "OPEN_MARKED_TO_MARKET", position.ambiguousBars, Map.of(), position.planKey,
                        position.decisionKey,
                        excursionR(position.mfePrice, position.initialRiskPerUnit),
                        excursionR(position.maePrice, position.initialRiskPerUnit)));
            }
        }

        RiskContext context(BacktestSpec spec, Instant anchor, FeatureSnapshot snapshot) {
            BigDecimal equity = startingCapital.add(realized).add(unrealized());
            LocalDate sessionDate = calendar.sessionDate(anchor);
            boolean tradingDay = calendar.isTradingDay(sessionDate);
            boolean inSession = calendar.isSessionMinute(anchor);
            boolean cutoffReached = tradingDay && !anchor.isBefore(calendar.sessionClose(sessionDate));
            return RiskContext.builder(spec.runKey() + ":ctx:" + anchor, 1, anchor, calendarSession(spec, anchor))
                    .available()
                    .equity(equity, equity)
                    .funding(equity, equity, BigDecimal.ZERO)
                    .exposures(grossExposure(), netExposure())
                    // Open-position risk and unfilled-order reserve are distinct capacity, never the
                    // same number passed twice (that would double-count against the open-risk limit).
                    .risk(openRisk(), BigDecimal.ZERO, pendingReservedRisk(), pendingReservedNotional())
                    .losses(realized.min(BigDecimal.ZERO).abs(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    .state(RiskState.NORMAL)
                    .counters(positions.size(), 0)
                    .health("HEALTHY", "HEALTHY", "MATCHED", true)
                    .session(new RiskContext.SessionWindow(
                            anchor, tradingDay, inSession, false, cutoffReached, false,
                            NseTradingCalendar.VERSION))
                    .build();
        }

        private LocalDate calendarSession(BacktestSpec spec, Instant anchor) {
            return anchor.atZone(NseTradingCalendar.EXCHANGE_ZONE).toLocalDate();
        }

        /** Risk committed by open positions (initial risk per unit times quantity). */
        private BigDecimal openRisk() {
            return positions.values().stream()
                    .map(p -> p.initialRiskPerUnit.multiply(BigDecimal.valueOf(p.quantity)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Risk reserved by orders that are still pending a next-bar fill. */
        private BigDecimal pendingReservedRisk() {
            return pending.values().stream()
                    .map(order -> order.plan().plannedRisk())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal pendingReservedNotional() {
            return pending.values().stream()
                    .map(order -> order.plan().plannedNotional())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Unrealized P&L of open positions at their last mark. */
        private BigDecimal unrealized() {
            return positions.values().stream()
                    .map(p -> grossPnl(p, p.markPrice, p.quantity))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal grossExposure() {
            return positions.values().stream()
                    .map(p -> p.markPrice.multiply(BigDecimal.valueOf(p.quantity)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal netExposure() {
            return positions.values().stream()
                    .map(p -> {
                        BigDecimal marked = p.markPrice.multiply(BigDecimal.valueOf(p.quantity));
                        return p.direction.isLong() ? marked : marked.negate();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** Mutable run-scoped position (mark price and ambiguity count change each bar). */
    private static final class OpenPosition {
        final long instrumentId;
        final Direction direction;
        final long quantity;
        final BigDecimal entryPrice;
        final Instant entryAt;
        final BigDecimal stop;
        final BigDecimal target;
        final BigDecimal initialRiskPerUnit;
        final String planKey;
        final String decisionKey;
        final String entryPattern;
        int ambiguousBars;
        BigDecimal markPrice;
        BigDecimal mfePrice = BigDecimal.ZERO;
        BigDecimal maePrice = BigDecimal.ZERO;

        OpenPosition(
                long instrumentId,
                Direction direction,
                long quantity,
                BigDecimal entryPrice,
                Instant entryAt,
                BigDecimal stop,
                BigDecimal target,
                BigDecimal initialRiskPerUnit,
                String planKey,
                String decisionKey,
                String entryPattern,
                int ambiguousBars) {
            this.instrumentId = instrumentId;
            this.direction = direction;
            this.quantity = quantity;
            this.entryPrice = entryPrice;
            this.entryAt = entryAt;
            this.stop = stop;
            this.target = target;
            this.initialRiskPerUnit = initialRiskPerUnit;
            this.planKey = planKey;
            this.decisionKey = decisionKey;
            this.entryPattern = entryPattern;
            this.ambiguousBars = ambiguousBars;
            this.markPrice = entryPrice;
        }
    }

    private static final class PendingOrder {
        private final Direction direction;
        private final TradePlan plan;
        private int barsWaited;

        private PendingOrder(Direction direction, TradePlan plan, int barsWaited) {
            this.direction = direction;
            this.plan = plan;
            this.barsWaited = barsWaited;
        }

        Direction direction() {
            return direction;
        }

        TradePlan plan() {
            return plan;
        }
    }

    private static BigDecimal adjustedFill(
            BacktestSpec spec, Direction direction, BigDecimal reference, boolean entry) {
        BacktestSpec.ExecutionPolicy policy = spec.execution();
        BigDecimal adverse = (policy.adverseSlippageBps() == null ? BigDecimal.ZERO : policy.adverseSlippageBps())
                .add(policy.halfSpreadBps() == null ? BigDecimal.ZERO : policy.halfSpreadBps());
        BigDecimal factor = adverse.divide(BigDecimal.valueOf(10_000), 10, RoundingMode.HALF_UP);
        boolean buy = entry == direction.isLong();
        BigDecimal price = buy
                ? reference.multiply(BigDecimal.ONE.add(factor))
                : reference.multiply(BigDecimal.ONE.subtract(factor));
        BigDecimal tick = new BigDecimal("0.05");
        return buy ? price.divide(tick, 0, RoundingMode.CEILING).multiply(tick)
                : price.divide(tick, 0, RoundingMode.FLOOR).multiply(tick);
    }

    private static BigDecimal grossPnl(OpenPosition position, BigDecimal exit, long quantity) {
        BigDecimal diff = position.direction.isLong()
                ? exit.subtract(position.entryPrice)
                : position.entryPrice.subtract(exit);
        return diff.multiply(BigDecimal.valueOf(quantity));
    }

    private static Map<String, BigDecimal> costs(
            BacktestSpec.CostSchedule schedule, BigDecimal entryNotional, BigDecimal exitNotional) {
        if (schedule == null) {
            return Map.of();
        }
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        BigDecimal buyRate = rate(schedule.brokerageBuyBps());
        BigDecimal sellRate = rate(schedule.brokerageSellBps());
        BigDecimal brokerage = entryNotional.multiply(buyRate).add(exitNotional.multiply(sellRate));
        costs.put("brokerage", money(brokerage));
        BigDecimal exchange = entryNotional.add(exitNotional).multiply(rate(schedule.exchangeBps()));
        costs.put("exchange", money(exchange));
        BigDecimal stt = exitNotional.multiply(rate(schedule.sttSellBps()));
        costs.put("stt", money(stt));
        BigDecimal gst = brokerage.add(exchange).multiply(rate(schedule.gstOnBrokerageAndExchangeBps()));
        costs.put("gst", money(gst));
        BigDecimal sebi = entryNotional.add(exitNotional).multiply(rate(schedule.sebiBps()));
        costs.put("sebi", money(sebi));
        BigDecimal stamp = entryNotional.multiply(rate(schedule.stampDutyBuyBps()));
        costs.put("stampDuty", money(stamp));
        BigDecimal other = entryNotional.multiply(rate(schedule.otherBuyBps())).add(exitNotional.multiply(rate(schedule.otherSellBps())));
        costs.put("other", money(other));
        return costs;
    }

    private static BigDecimal rate(BigDecimal bps) {
        return bps == null ? BigDecimal.ZERO : bps.divide(BigDecimal.valueOf(10_000), 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
