package com.edgerelative.application.strategy.context;

import com.edgerelative.application.feature.domain.ContextSnapshot;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.feature.math.PriceStructure;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.MarketContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.SectorContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.StockContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.StructureContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives the strategy's market/sector/stock decision context from canonical point-in-time feature
 * measurements plus the completed-candle history. Pure and deterministic; live, replay and backtest
 * all use this single derivation so their decisions agree.
 *
 * <p>Inputs are only ever read at or before the evaluation anchor. Categorical structure and
 * efficiency come from the feature engine's context snapshot; daily alignment, liquidity, technical
 * void and EMA relationship are derived under {@link DecisionContextPolicy}. Missing inputs remain
 * null (fail closed) rather than being substituted.
 */
public final class DecisionContextAssembler {

    private final DecisionContextPolicy policy;

    public DecisionContextAssembler(DecisionContextPolicy policy) {
        this.policy = policy;
    }

    public DecisionContext assemble(
            FeatureSnapshot snapshot,
            FeatureSnapshot daily,
            AggregatedCandle bar,
            List<AggregatedCandle> history,
            EmaStructure ema) {
        return new DecisionContext(market(snapshot), sector(snapshot), stock(snapshot, daily, bar, history, ema));
    }

    private MarketContext market(FeatureSnapshot snapshot) {
        ContextSnapshot market = snapshot == null ? null : snapshot.market();
        Instant anchor = market == null
                ? (snapshot == null ? null : snapshot.anchorTimestamp())
                : market.anchorTimestamp();
        if (market == null) {
            return new MarketContext(null, null, null, anchor, false);
        }
        String structure = label(market, FeatureKeys.MARKET_PRICE_STRUCTURE);
        if (structure == null) {
            return new MarketContext(null, null, null, anchor, false);
        }
        Double efficiency = number(market, FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY);
        String bias = switch (structure) {
            case PriceStructure.BULL -> "BULLISH";
            case PriceStructure.BEAR -> "BEARISH";
            default -> "NEUTRAL";
        };
        boolean trending = efficiency != null
                && efficiency >= policy.trendEfficiencyMin()
                && !PriceStructure.MIXED.equals(structure);
        String regime = trending ? "TREND" : "RANGE";
        return new MarketContext(bias, regime, null, anchor, true);
    }

    private SectorContext sector(FeatureSnapshot snapshot) {
        ContextSnapshot sector = snapshot == null ? null : snapshot.sector();
        if (sector == null) {
            return new SectorContext(null, null, null, null, null, false);
        }
        Double rrs = number(sector, FeatureKeys.SECTOR_RRS_RAW);
        String state = rrs == null ? null : rrs > 0 ? "STRONG" : rrs < 0 ? "WEAK" : "NEUTRAL";
        return new SectorContext(sector.sectorId(), sector.referenceCode(), rrs, state, sector.anchorTimestamp(), rrs != null);
    }

    private StockContext stock(
            FeatureSnapshot snapshot,
            FeatureSnapshot daily,
            AggregatedCandle bar,
            List<AggregatedCandle> history,
            EmaStructure ema) {
        Double rrsD1 = number(daily, FeatureKeys.RRS_RAW);
        String dailyStructure = rrsD1 == null ? null : rrsD1 > 0 ? "LONG_ALIGNED" : rrsD1 < 0 ? "SHORT_ALIGNED" : null;
        Double median = medianTradedValue(history, policy.liquidityWindow());
        String liquidityState = median == null ? null : median > 0 ? "VALID" : "INVALID";
        Double voidAtr = technicalVoid(history, policy.technicalVoidWindow(), number(snapshot, FeatureKeys.ATR));
        StructureContext structure = new StructureContext(
                false, null, null, null, null, false, null,
                ema.present(), ema.ema3(), ema.ema8(), ema.ema3Previous(), ema.ema8Previous());
        return new StockContext(
                dailyStructure,
                rrsD1,
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
                bar == null || bar.close() == null ? null : bar.close().doubleValue(),
                null,
                liquidityState,
                median,
                null,
                voidAtr,
                policy.assumeEventRiskClear() ? Boolean.FALSE : null,
                structure);
    }

    private static Double medianTradedValue(List<AggregatedCandle> history, int window) {
        List<Double> values = new ArrayList<>();
        for (int i = Math.max(0, history.size() - window); i < history.size(); i++) {
            AggregatedCandle candle = history.get(i);
            if (candle.close() != null) {
                values.add(candle.close().doubleValue() * candle.volume());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        values.sort(Double::compareTo);
        int mid = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(mid) : (values.get(mid - 1) + values.get(mid)) / 2.0;
    }

    private static Double technicalVoid(List<AggregatedCandle> history, int window, Double atr) {
        if (atr == null || atr <= 0 || history.isEmpty()) {
            return null;
        }
        double high = Double.NEGATIVE_INFINITY;
        double low = Double.POSITIVE_INFINITY;
        for (int i = Math.max(0, history.size() - window); i < history.size(); i++) {
            AggregatedCandle candle = history.get(i);
            if (candle.high() != null) {
                high = Math.max(high, candle.high().doubleValue());
            }
            if (candle.low() != null) {
                low = Math.min(low, candle.low().doubleValue());
            }
        }
        if (!Double.isFinite(high) || !Double.isFinite(low)) {
            return null;
        }
        return (high - low) / atr;
    }

    private static Double number(FeatureSnapshot snapshot, String key) {
        return snapshot == null ? null : number(snapshot.feature(key));
    }

    private static Double number(ContextSnapshot context, String key) {
        return context == null ? null : number(context.features().get(key));
    }

    private static Double number(FeatureValue value) {
        return value != null && value.availability() == FeatureAvailability.VALID ? value.value() : null;
    }

    private static String label(FeatureSnapshot snapshot, String key) {
        FeatureValue value = snapshot == null ? null : snapshot.feature(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }

    private static String label(ContextSnapshot context, String key) {
        FeatureValue value = context == null ? null : context.features().get(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }

    /** The three decision contexts (market, sector, stock) resolved for one anchor. */
    public record DecisionContext(MarketContext market, SectorContext sector, StockContext stock) {
    }

    /**
     * EMA3/EMA8 relationship for the current completed bar. {@code ema3}/{@code ema8} are the values
     * at this bar; {@code ...Previous} are the values at the prior bar, so a genuine crossover can be
     * detected. A single running EMA is seeded once and carried forward, never recomputed from a
     * sliding window (which would fabricate crossings).
     */
    public record EmaStructure(
            boolean present,
            BigDecimal ema3,
            BigDecimal ema8,
            BigDecimal ema3Previous,
            BigDecimal ema8Previous) {

        public static EmaStructure absent() {
            return new EmaStructure(false, null, null, null, null);
        }
    }

    /** Stateful, deterministic EMA3/EMA8 tracker for one instrument over a replay. */
    public static final class EmaTracker {

        private final int fastLength;
        private final int slowLength;
        private Double emaFast;
        private Double emaSlow;
        private Double previousFast;
        private Double previousSlow;
        private int samples;

        public EmaTracker(int fastLength, int slowLength) {
            this.fastLength = fastLength;
            this.slowLength = slowLength;
        }

        public EmaStructure update(BigDecimal close) {
            if (close == null) {
                return current();
            }
            double value = close.doubleValue();
            if (emaFast == null) {
                emaFast = value;
                emaSlow = value;
                previousFast = value;
                previousSlow = value;
                samples = 1;
                return current();
            }
            previousFast = emaFast;
            previousSlow = emaSlow;
            double fastAlpha = 2.0 / (fastLength + 1);
            double slowAlpha = 2.0 / (slowLength + 1);
            emaFast = value * fastAlpha + emaFast * (1 - fastAlpha);
            emaSlow = value * slowAlpha + emaSlow * (1 - slowAlpha);
            samples++;
            return current();
        }

        private EmaStructure current() {
            if (samples < slowLength || emaFast == null) {
                return EmaStructure.absent();
            }
            return new EmaStructure(
                    true,
                    BigDecimal.valueOf(emaFast),
                    BigDecimal.valueOf(emaSlow),
                    BigDecimal.valueOf(previousFast),
                    BigDecimal.valueOf(previousSlow));
        }
    }
}
