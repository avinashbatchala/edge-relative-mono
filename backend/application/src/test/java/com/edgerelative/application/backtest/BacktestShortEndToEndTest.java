package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.PriceChange;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.volume.BaselineEstimatorType;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.history.HistoricalCoverage;
import com.edgerelative.application.history.query.HistoricalDataReader;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.risk.domain.PolicyState;
import com.edgerelative.application.risk.domain.RiskEvaluator;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.risk.domain.TradingMode;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.StrategyEngine;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.domain.family.SetupFamilyRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Symmetric SHORT counterpart to the long end-to-end fixture: a persistent relatively-weak stock
 * in a bearish market, a 3/8 short confirmation, risk approval, a next-bar fill and a stop-out on a
 * gap against the position. Exercises the same production path as the long scenario.
 */
class BacktestShortEndToEndTest {

    private static final long SUBJECT = 100L;
    private static final long MARKET = 200L;

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private static StrategyParameters parameters() {
        return new StrategyParameters(
                "BT_SHORT_TEST", 1, Set.of(SetupFamily.M5_3_8_CONFIRMATION),
                0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0,
                0.0,
                1000.0,
                1000.0,
                0.0,
                0,
                100,
                100,
                0,
                0,
                1_000_000_000.0,
                StrategyParameters.NeutralMarketPolicy.BLOCK,
                0.0,
                null, null, null, null, null);
    }

    private static RiskPolicy riskPolicy() {
        return new RiskPolicy(
                "BT_SHORT_RISK", 1, PolicyState.VALIDATED, EnumSet.allOf(TradingMode.class), Map.of(),
                new RiskPolicy.TradeLimits(dec("0.50"), null, null, null, dec("10.0")),
                new RiskPolicy.PortfolioLimits(
                        dec("0.5"), dec("0.5"), dec("10.0"), dec("10.0"), dec("0.5"), dec("0.5"), 10),
                new RiskPolicy.SymbolLimits(dec("10.0"), dec("10.0"), dec("10.0"), dec("1.0")),
                new RiskPolicy.SectorLimits(dec("10.0"), dec("10.0"), dec("10.0"), dec("10.0"), false),
                new RiskPolicy.MarginLimits(dec("0.0")),
                new RiskPolicy.LiquidityLimits(dec("1.0"), dec("1000"), false, false, false),
                new RiskPolicy.ExecutionAssumptions(dec("1"), dec("1"), dec("0"), null),
                new RiskPolicy.StressAssumptions(dec("0.03"), dec("0"), dec("0"), false),
                new RiskPolicy.DrawdownLimits(
                        dec("1"), dec("1"), dec("1"), dec("1"), dec("1"), dec("1"), 100),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                false, Map.of(), false, false, false, BigDecimal.ONE);
    }

    private static FeaturePolicy featurePolicy() {
        return new FeaturePolicy(
                30,
                new FeaturePolicy.Benchmark("NIFTY50"),
                new FeaturePolicy.Atr(Map.of("M5", 1, "D1", 1), 1, AtrSmoothing.SIMPLE),
                new FeaturePolicy.Rrs(PriceChange.CLOSE_TO_CLOSE, 1, 2, 2, 1, 2, 1),
                new FeaturePolicy.Rvol(BaselineEstimatorType.MEAN, 1, 1, 1, 1, 0.0, 2),
                new FeaturePolicy.Rve(1, 2),
                new FeaturePolicy.Structure(1, 2),
                new FeaturePolicy.DirectionalVolume(2));
    }

    private static BacktestSpec spec() {
        return new BacktestSpec(
                "e2e-short-run", List.of(SUBJECT), List.of("SUBJECT"),
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11), "M5", "D1",
                dec("1000000"), "INR", false, parameters(), riskPolicy(), featurePolicy(),
                new BacktestSpec.ExecutionPolicy("test-exec", 0, dec("2"), dec("5"), BigDecimal.ONE, 1,
                        BacktestSpec.SessionCutoff.NEW_ENTRY_CUTOFF,
                        BacktestSpec.ExecutionPolicy.AmbiguityPolicy.STOP_FIRST_CONSERVATIVE, true,
                        null, null, null, null),
                new BacktestSpec.CostSchedule("TEST_COSTS", dec("3"), dec("3"), dec("10"), dec("1"),
                        dec("18"), dec("0.1"), dec("0.5"), dec("0"), dec("0"), true),
                BacktestSpec.EndOfRunPolicy.MARK_TO_MARKET, 10, 1L, BacktestEngine.ENGINE_REVISION,
                MARKET, null, "CANONICAL_M5", "short-fixture", BacktestSpec.ContextSource.DERIVED_RESEARCH);
    }

    @Test
    void bearishRelativeWeaknessProducesACompletedShortTrade() {
        BacktestResult result = new BacktestEngine(
                new FixtureReader(), new FeatureEngine(),
                new StrategyEngine(SetupFamilyRegistry.production()), new RiskEvaluator(),
                NseTradingCalendar.weekendsOnly())
                .run(spec(), null);

        assertThat(result.trades()).isNotEmpty();
        BacktestTrade completed = result.trades().stream()
                .filter(trade -> !trade.isOpen())
                .findFirst()
                .orElseThrow();
        assertThat(completed.direction().name()).isEqualTo("SHORT");
        assertThat(completed.exitReason()).isEqualTo("STOP");
        // A short stop fills at or above the entry.
        assertThat(completed.exitPrice()).isGreaterThanOrEqualTo(completed.entryPrice());
        assertThat(completed.netPnl())
                .isEqualByComparingTo(completed.grossPnl().subtract(completed.explicitCosts()));
        assertThat(completed.realizedR()).isNotNull();
    }

    /** Deterministic reader: falling subject with a gap up against the short. */
    private static final class FixtureReader implements HistoricalDataReader {

        @Override
        public List<AggregatedCandle> candles(
                long instrumentId, String timeframeCode, Instant from, Instant to, int limit) {
            if ("D1".equals(timeframeCode)) {
                return daily(instrumentId);
            }
            return instrumentId == MARKET ? marketM5() : subjectM5();
        }

        @Override
        public List<AggregatedCandle> replayCandles(
                long instrumentId, String timeframeCode, Instant from, Instant to, int maxBars) {
            return candles(instrumentId, timeframeCode, from, to, maxBars);
        }

        @Override
        public HistoricalCoverage coverage(long instrumentId, String timeframeCode) {
            return null;
        }

        private static final int BARS_PER_SESSION = 75;
        private static final LocalDate[] SESSIONS = {
            LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9),
            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)
        };

        private List<AggregatedCandle> subjectM5() {
            List<AggregatedCandle> candles = new ArrayList<>();
            BigDecimal price = dec("100.00");
            int absolute = 0;
            for (int session = 0; session < SESSIONS.length; session++) {
                for (int bar = 0; bar < BARS_PER_SESSION; bar++) {
                    Instant at = SESSIONS[session].atTime(3, 45).toInstant(ZoneOffset.UTC)
                            .plus(Duration.ofMinutes(5L * bar));
                    if (session == SESSIONS.length - 1 && bar == 0) {
                        // Final session gaps up through the short's protective stop.
                        candles.add(new AggregatedCandle(
                                at, at.plus(Duration.ofMinutes(5)), dec("101.20"), dec("101.80"), dec("100.80"),
                                dec("101.50"), 1000, null, 10, dec("101.50"), false, true, "GOOD", "v1"));
                        price = dec("101.50");
                        absolute++;
                        continue;
                    }
                    // Zigzag around 100: 20 bars down, 20 bars up. Each down leg produces a genuine
                    // EMA3-below-EMA8 confirmation so the 3/8 short family can trigger.
                    BigDecimal open = price;
                    int positionInCycle = absolute % 40;
                    BigDecimal delta = positionInCycle < 20 ? dec("-0.30") : dec("0.30");
                    BigDecimal close = open.add(delta);
                    BigDecimal high = close.max(open).add(dec("0.01"));
                    BigDecimal low = close.min(open).subtract(dec("0.01"));
                    price = close;
                    absolute++;
                    candles.add(new AggregatedCandle(
                            at, at.plus(Duration.ofMinutes(5)), open, high, low, close, 1000, null, 10, close,
                            false, true, "GOOD", "v1"));
                }
            }
            return candles;
        }

        private List<AggregatedCandle> marketM5() {
            List<AggregatedCandle> candles = new ArrayList<>();
            BigDecimal price = dec("20000");
            // Initial downward zigzag confirms lower highs and lower lows (BEAR_STRUCTURE), then a
            // steady decline keeps the market regime trending and bias BEARISH.
            BigDecimal[] zigzag = { dec("-1"), dec("-1"), dec("0.5"), dec("0.5") };
            int index = 0;
            int absolute = 0;
            for (LocalDate session : SESSIONS) {
                for (int bar = 0; bar < BARS_PER_SESSION; bar++) {
                    Instant at = session.atTime(3, 45).toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(5L * bar));
                    BigDecimal open = price;
                    BigDecimal delta = absolute < 16 ? zigzag[index++ % zigzag.length] : dec("-1");
                    BigDecimal close = open.add(delta);
                    BigDecimal high = close.add(dec("0.25"));
                    BigDecimal low = close.subtract(dec("0.25"));
                    price = close;
                    absolute++;
                    candles.add(new AggregatedCandle(
                            at, at.plus(Duration.ofMinutes(5)), open, high, low, close, 5000, null, 10, close,
                            false, true, "GOOD", "v1"));
                }
            }
            return candles;
        }

        private List<AggregatedCandle> daily(long instrumentId) {
            List<AggregatedCandle> candles = new ArrayList<>();
            BigDecimal price = instrumentId == MARKET ? dec("20000") : dec("100");
            for (int i = 0; i < 6; i++) {
                price = instrumentId == MARKET ? price : price.subtract(dec("10"));
                Instant at = LocalDate.of(2026, 9, 14).minusDays(6 - i).atTime(10, 0).toInstant(ZoneOffset.UTC);
                BigDecimal open = instrumentId == MARKET ? price : price.add(dec("10"));
                candles.add(new AggregatedCandle(
                        at, at.plus(Duration.ofHours(6)), open, open.add(dec("1")), price.subtract(dec("1")), price,
                        100000, null, 100, price, false, true, "GOOD", "v1"));
            }
            return candles;
        }
    }
}
