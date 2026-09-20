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

    public BacktestResult run(BacktestSpec spec, BacktestContextProvider provider, ProgressListener listener) {
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

        Set<Instant> timeline = new TreeSet<>();
        m5.values().forEach(byAnchor -> timeline.addAll(byAnchor.keySet()));

        // Warm-up candles initialise features but must not produce trades or performance before the
        // requested window. Only anchors inside [start, end] are processed and mark equity.
        Instant windowStart = spec.startDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant windowEndExclusive =
                spec.endDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        Portfolio portfolio = new Portfolio(spec, provider);
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
                processSymbol(spec, provider, portfolio, instrumentId, anchor, m5.get(instrumentId), d1.get(instrumentId), series);
                processed++;
            }
            portfolio.markEquity(anchor, equity);
            if (listener != null) {
                listener.onProgress(processed, total, anchor);
            }
        }
        portfolio.finish(spec, series, timeline, equity);
        return new BacktestResult(
                portfolio.trades(), equity, portfolio.rejections(), processed, total);
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
        Instant from = spec.startDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                .minus(Duration.ofDays(Math.max(1, spec.warmupBars())));
        Instant to = spec.endDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        for (Long id : ids) {
            List<AggregatedCandle> candles = reader.candles(id, timeframe, from, to, 100_000).stream()
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
            FeatureContext context = new FeatureContext(
                    instrumentId, timeframe, subject, marketCandles, List.of(), benchmark,
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

    private void processSymbol(
            BacktestSpec spec,
            BacktestContextProvider provider,
            Portfolio portfolio,
            long instrumentId,
            Instant anchor,
            NavigableMap<Instant, FeatureSnapshot> m5,
            NavigableMap<Instant, FeatureSnapshot> d1,
            Map<Long, List<AggregatedCandle>> series) {
        List<AggregatedCandle> candles = series.getOrDefault(instrumentId, List.of());
        AggregatedCandle bar = candleAt(candles, anchor);
        if (bar == null) {
            return;
        }
        portfolio.onBar(spec, instrumentId, bar);
        if (m5 == null || d1 == null) {
            return;
        }
        FeatureSnapshot snapshot = m5.get(anchor);
        if (snapshot == null) {
            return;
        }
        if (portfolio.hasPositionOrPending(instrumentId)) {
            return; // no averaging/pyramiding in the baseline
        }
        Map.Entry<Instant, FeatureSnapshot> dailyEntry = d1.floorEntry(anchor);
        FeatureSnapshot daily = dailyEntry == null ? null : dailyEntry.getValue();

        StrategyEvaluationResult longResult = strategyEngine.evaluate(
                input(spec, provider, instrumentId, anchor, snapshot, daily, bar), spec.strategyParameters(), Direction.LONG);
        StrategyEvaluationResult shortResult = strategyEngine.evaluate(
                input(spec, provider, instrumentId, anchor, snapshot, daily, bar), spec.strategyParameters(), Direction.SHORT);
        StrategyEvaluationResult chosen = longResult.setupState() == SetupState.VALID
                ? longResult
                : shortResult.setupState() == SetupState.VALID ? shortResult : null;
        if (chosen == null) {
            return;
        }
        RiskDecisionProposal proposal = riskEvaluator.evaluate(
                candidate(spec, provider, instrumentId, anchor, snapshot, chosen, bar),
                portfolio.context(spec, anchor, snapshot),
                spec.riskPolicy());
        if (!proposal.decision().authorizesNewRisk()) {
            portfolio.recordRejection(new BacktestRejection(
                    anchor, instrumentId, chosen.direction(),
                    proposal.primaryReason() == null ? "UNKNOWN" : proposal.primaryReason().name(),
                    proposal.explanation()));
            return;
        }
        TradePlan plan = TradePlanFactory.create(
                proposal, 0L,
                lineage(spec, provider, instrumentId, anchor, snapshot, chosen, bar, proposal),
                new TradePlanPolicy("backtest", 1, "backtest", null, null, null, "REFERENCE_PRICE",
                        proposal.structuralInvalidation() == null ? null : "STRUCTURAL_UNRESOLVED", "TICK_BUFFER"),
                anchor, null);
        portfolio.submit(spec, instrumentId, chosen.direction(), plan);
    }

    private StrategyEvaluationInput input(
            BacktestSpec spec,
            BacktestContextProvider provider,
            long instrumentId,
            Instant anchor,
            FeatureSnapshot snapshot,
            FeatureSnapshot daily,
            AggregatedCandle bar) {
        BacktestContextProvider.MarketInput market = provider.market(instrumentId, anchor);
        BacktestContextProvider.StockInput stock = provider.stock(instrumentId, anchor);
        LocalDate sessionDate = calendar.sessionDate(anchor);
        boolean tradingDay = calendar.isTradingDay(sessionDate);
        List<DependencyStatus> dependencies = new ArrayList<>();
        dependencies.add(dep("market.bias", market.available()));
        dependencies.add(dep("market.regime", market.available()));
        dependencies.add(dep("stock.dailyStructure", stock.available() && stock.dailyStructure() != null));
        dependencies.add(dep("stock.liquidity", stock.available() && stock.medianTradedValue() != null));
        dependencies.add(dep("stock.technicalVoid", stock.available() && stock.technicalVoidAtr() != null));
        dependencies.add(dep("stock.eventRisk", stock.available() && stock.eventRiskKnown()));
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
                new StrategyEvaluationInput.SessionContext(anchor, tradingDay, tradingDay, false, false, NseTradingCalendar.VERSION),
                new StrategyEvaluationInput.MarketContext(
                        market.bias(), market.regime(), market.phase(), anchor, market.available()),
                sector(snapshot, anchor),
                new StrategyEvaluationInput.StockContext(
                        stock.dailyStructure(),
                        numberOrLabel(daily, FeatureKeys.RRS_RAW),
                        number(snapshot, FeatureKeys.RRS_RAW),
                        number(snapshot, FeatureKeys.RRS_FAST),
                        number(snapshot, FeatureKeys.RRS_SLOW),
                        number(snapshot, FeatureKeys.RRS_PERSISTENCE),
                        label(snapshot, FeatureKeys.RRS_TREND_STATE),
                        number(snapshot, FeatureKeys.RVOL_D1),
                        number(snapshot, FeatureKeys.RVOL_INTERVAL),
                        number(snapshot, FeatureKeys.RVOL_CUMULATIVE),
                        number(snapshot, FeatureKeys.RVE),
                        number(snapshot, FeatureKeys.ATR),
                        bar.close() == null ? null : bar.close().doubleValue(),
                        null,
                        stock.liquidityState(),
                        stock.medianTradedValue(),
                        null,
                        stock.technicalVoidAtr(),
                        stock.available() && stock.eventRiskKnown() ? stock.eventRiskBlocked() : null,
                        structure(stock)),
                new StrategyEvaluationInput.CompletedCandle(
                        bar.openTime(), bar.closeTime(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()),
                dependencies,
                StrategyEvaluationInput.PriorSetup.none());
    }

    private RiskCandidate candidate(
            BacktestSpec spec,
            BacktestContextProvider provider,
            long instrumentId,
            Instant anchor,
            FeatureSnapshot snapshot,
            StrategyEvaluationResult result,
            AggregatedCandle bar) {
        BacktestContextProvider.MarketInput market = provider.market(instrumentId, anchor);
        return new RiskCandidate(
                "bt-" + instrumentId + "-" + anchor,
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
                provider.stock(instrumentId, anchor).eventRiskKnown(),
                provider.stock(instrumentId, anchor).eventRiskBlocked(),
                sectorId(snapshot),
                sectorCode(snapshot),
                null,
                5.0,
                1_000_000.0,
                5_000_000.0,
                null,
                null,
                true,
                "VALID",
                snapshot.featureSchemaVersion(),
                "backtest");
    }

    private PlanLineage lineage(
            BacktestSpec spec,
            BacktestContextProvider provider,
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
                provider.market(instrumentId, anchor).regime(),
                sectorCode(snapshot),
                result.setupInstanceId(),
                anchor,
                result.trigger() == null ? null : result.trigger().triggerType(),
                result.trigger() == null ? null : result.trigger().triggerLevel(),
                result.trigger() == null ? null : result.trigger().entryExtensionAtr(),
                result.invalidation() == null ? null : result.invalidation().invalidationType(),
                proposal.structuralInvalidation(),
                result.invalidation() == null ? null : result.invalidation().basis());
    }

    private static StrategyEvaluationInput.StructureContext structure(BacktestContextProvider.StockInput stock) {
        BacktestContextProvider.StructureInput input = stock.structure();
        if (input == null) {
            return StrategyEvaluationInput.StructureContext.empty();
        }
        boolean present = input.ema3() != null && input.ema8() != null
                && input.ema3Previous() != null && input.ema8Previous() != null;
        return new StrategyEvaluationInput.StructureContext(
                false, null, null, null, null, false, null, present,
                input.ema3(), input.ema8(), input.ema3Previous(), input.ema8Previous());
    }

    private static StrategyEvaluationInput.SectorContext sector(FeatureSnapshot snapshot, Instant anchor) {
        if (snapshot.sector() == null) {
            return new StrategyEvaluationInput.SectorContext(null, null, null, null, anchor, false);
        }
        Double rrs = number(snapshot.sector().features().get(FeatureKeys.SECTOR_RRS_RAW));
        return new StrategyEvaluationInput.SectorContext(
                snapshot.sector().sectorId(), snapshot.sector().referenceCode(), rrs,
                snapshot.sector().quality() == null ? null : snapshot.sector().quality().name(),
                snapshot.sector().anchorTimestamp(), rrs != null);
    }

    private static Long sectorId(FeatureSnapshot snapshot) {
        return snapshot.sector() == null ? null : snapshot.sector().sectorId();
    }

    private static String sectorCode(FeatureSnapshot snapshot) {
        return snapshot.sector() == null ? null : snapshot.sector().referenceCode();
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

    private static Double number(FeatureValue value) {
        return value != null && value.availability() == FeatureAvailability.VALID ? value.value() : null;
    }

    private static Double numberOrLabel(FeatureSnapshot snapshot, String key) {
        return number(snapshot, key);
    }

    private static String label(FeatureSnapshot snapshot, String key) {
        FeatureValue value = snapshot == null ? null : snapshot.feature(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }

    private static AggregatedCandle candleAt(List<AggregatedCandle> candles, Instant anchor) {
        for (AggregatedCandle candle : candles) {
            if (candle.closeTime() != null && !candle.closeTime().isAfter(anchor)
                    && !candle.openTime().isAfter(anchor)) {
                if (candle.closeTime().equals(anchor) || (candle.openTime().isBefore(anchor) && candle.closeTime().isAfter(anchor))) {
                    return candle;
                }
            }
        }
        for (AggregatedCandle candle : candles) {
            if (anchor.equals(candle.closeTime())) {
                return candle;
            }
        }
        return null;
    }

    /** Run-scoped portfolio: cash, one position per symbol, pending orders, reservations. */
    private static final class Portfolio {
        private final BigDecimal startingCapital;
        private BigDecimal realized = BigDecimal.ZERO;
        private final Map<Long, OpenPosition> positions = new LinkedHashMap<>();
        private final Map<Long, PendingOrder> pending = new LinkedHashMap<>();
        private final List<BacktestTrade> trades = new ArrayList<>();
        private final List<BacktestRejection> rejections = new ArrayList<>();
        private final BacktestContextProvider provider;

        private Portfolio(BacktestSpec spec, BacktestContextProvider provider) {
            this.startingCapital = spec.startingCapital();
            this.provider = provider;
        }

        boolean hasPositionOrPending(long instrumentId) {
            return positions.containsKey(instrumentId) || pending.containsKey(instrumentId);
        }

        void submit(BacktestSpec spec, long instrumentId, Direction direction, TradePlan plan) {
            pending.put(instrumentId, new PendingOrder(direction, plan, 0));
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
                    pending.remove(instrumentId);
                } else {
                    fillEntry(spec, instrumentId, order, bar);
                    pending.remove(instrumentId);
                }
            }
            OpenPosition position = positions.get(instrumentId);
            if (position != null) {
                if (bar.close() != null) {
                    position.markPrice = bar.close();
                }
                manage(spec, instrumentId, position, bar);
            }
        }

        private void fillEntry(BacktestSpec spec, long instrumentId, PendingOrder order, AggregatedCandle bar) {
            BigDecimal reference = order.direction().isLong() ? bar.open() : bar.open();
            if (reference == null) {
                return;
            }
            BigDecimal fill = adjustedFill(spec, order.direction(), reference, true);
            long quantity = order.plan().plannedQuantity();
            BigDecimal initialRisk = order.plan().plannedRisk()
                    .divide(BigDecimal.valueOf(Math.max(1, quantity)), 8, RoundingMode.HALF_UP);
            positions.put(instrumentId, new OpenPosition(
                    instrumentId, order.direction(), quantity, fill, bar.openTime(), order.plan().protectiveStop(),
                    order.plan().targetReference(), initialRisk, order.plan().planKey(), order.plan().decisionKey(),
                    order.plan().entryPattern(), 0));
        }

        private void manage(BacktestSpec spec, long instrumentId, OpenPosition position, AggregatedCandle bar) {
            if (bar.high() == null || bar.low() == null) {
                return;
            }
            boolean longSide = position.direction.isLong();
            BigDecimal stop = position.stop;
            BigDecimal target = position.target;
            boolean stopHit = longSide ? bar.low().compareTo(stop) <= 0 : bar.high().compareTo(stop) >= 0;
            boolean targetHit = target != null && (longSide ? bar.high().compareTo(target) >= 0 : bar.low().compareTo(target) <= 0);
            if (stopHit && targetHit) {
                position.ambiguousBars++;
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
            trades.add(new BacktestTrade(
                    UUID.nameUUIDFromBytes((position.planKey + ":" + bar.closeTime()).getBytes(StandardCharsets.UTF_8)).toString(),
                    instrumentId, String.valueOf(instrumentId), position.direction, position.entryPattern,
                    position.entryAt, position.entryPrice, bar.closeTime(), exitPrice, position.quantity,
                    position.initialRiskPerUnit, gross.setScale(2, RoundingMode.HALF_UP), explicit.setScale(2, RoundingMode.HALF_UP),
                    net.setScale(2, RoundingMode.HALF_UP), realizedR,
                    Duration.between(position.entryAt, bar.closeTime()).getSeconds(), reason,
                    position.ambiguousBars, costs, position.planKey, position.decisionKey));
            positions.remove(instrumentId);
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
            for (OpenPosition position : positions.values()) {
                List<AggregatedCandle> candles = series.getOrDefault(position.instrumentId, List.of());
                BigDecimal last = candles.isEmpty() ? position.markPrice : candles.get(candles.size() - 1).close();
                BigDecimal gross = grossPnl(position, last, position.quantity);
                trades.add(new BacktestTrade(
                        UUID.nameUUIDFromBytes((position.planKey + ":open").getBytes(StandardCharsets.UTF_8)).toString(),
                        position.instrumentId, String.valueOf(position.instrumentId), position.direction, position.entryPattern,
                        position.entryAt, position.entryPrice, null, null, position.quantity, position.initialRiskPerUnit,
                        gross.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2), gross.setScale(2, RoundingMode.HALF_UP),
                        null, null, "OPEN_MARKED_TO_MARKET", position.ambiguousBars, Map.of(), position.planKey,
                        position.decisionKey));
            }
        }

        RiskContext context(BacktestSpec spec, Instant anchor, FeatureSnapshot snapshot) {
            BigDecimal equity = startingCapital.add(realized);
            return RiskContext.builder("bt-" + anchor, 1, anchor, calendarSession(spec, anchor))
                    .available()
                    .equity(equity, equity)
                    .funding(equity, equity, BigDecimal.ZERO)
                    .exposures(grossExposure(), netExposure())
                    .risk(reservedRisk(), BigDecimal.ZERO, reservedRisk(), reservedNotional())
                    .losses(realized.min(BigDecimal.ZERO).abs(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    .state(RiskState.NORMAL)
                    .counters(positions.size(), 0)
                    .health("HEALTHY", "HEALTHY", "MATCHED", true)
                    .session(new RiskContext.SessionWindow(anchor, true, true, false, false, false, NseTradingSessionVersion.VERSION))
                    .build();
        }

        private LocalDate calendarSession(BacktestSpec spec, Instant anchor) {
            return anchor.atZone(NseTradingCalendar.EXCHANGE_ZONE).toLocalDate();
        }

        private BigDecimal reservedRisk() {
            return positions.values().stream()
                    .map(p -> p.initialRiskPerUnit.multiply(BigDecimal.valueOf(p.quantity)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal reservedNotional() {
            return positions.values().stream()
                    .map(p -> p.entryPrice.multiply(BigDecimal.valueOf(p.quantity)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal grossExposure() {
            return reservedNotional();
        }

        private BigDecimal netExposure() {
            return positions.values().stream()
                    .map(p -> p.direction.isLong()
                            ? p.entryPrice.multiply(BigDecimal.valueOf(p.quantity))
                            : p.entryPrice.multiply(BigDecimal.valueOf(p.quantity)).negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static final class NseTradingSessionVersion {
        private static final String VERSION = "nse-session-v1";
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
