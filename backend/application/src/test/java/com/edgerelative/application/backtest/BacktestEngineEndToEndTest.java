package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.engine.BacktestContextProvider;
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
 * Small, manually verifiable end-to-end fixture: canonical candles -> production feature engine ->
 * production strategy engine -> production risk engine -> production plan factory -> simulated
 * execution -> costs -> equity/drawdown. Structure producers are simulated (they are not wired in
 * production); everything downstream is the production code path.
 */
class BacktestEngineEndToEndTest {

    private static final long SUBJECT = 100L;
    private static final long MARKET = 200L;
    private static final LocalDate DAY1 = LocalDate.of(2026, 9, 14);

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private static StrategyParameters parameters() {
        return new StrategyParameters(
                "BT_TEST", 1, Set.of(SetupFamily.M5_3_8_CONFIRMATION),
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
                "BT_RISK", 1, PolicyState.VALIDATED, EnumSet.allOf(TradingMode.class), Map.of(),
                new RiskPolicy.TradeLimits(dec("0.50"), null, null, null, dec("10.0")),
                new RiskPolicy.PortfolioLimits(
                        dec("0.5"), dec("0.5"), dec("10.0"), dec("10.0"), dec("0.5"), dec("0.5"), 10),
                new RiskPolicy.SymbolLimits(dec("10.0"), dec("10.0"), dec("10.0"), dec("1.0")),
                new RiskPolicy.SectorLimits(dec("10.0"), dec("10.0"), dec("10.0"), dec("10.0"), false),
                new RiskPolicy.MarginLimits(dec("0.0")),
                new RiskPolicy.LiquidityLimits(dec("1.0"), dec("1000"), false, false, false),
                new RiskPolicy.ExecutionAssumptions(dec("1"), dec("1"), dec("0")),
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
        return spec(BacktestSpec.ContextSource.STRICT_PRODUCTION);
    }

    private static BacktestSpec spec(BacktestSpec.ContextSource contextSource) {
        return new BacktestSpec(
                "e2e-run", List.of(SUBJECT), List.of("SUBJECT"),
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11), "M5", "D1",
                dec("1000000"), "INR", false, parameters(), riskPolicy(), featurePolicy(),
                new BacktestSpec.ExecutionPolicy("test-exec", 0, dec("2"), dec("5"), BigDecimal.ONE, 1,
                        BacktestSpec.SessionCutoff.NEW_ENTRY_CUTOFF,
                        BacktestSpec.ExecutionPolicy.AmbiguityPolicy.STOP_FIRST_CONSERVATIVE, true),
                new BacktestSpec.CostSchedule("TEST_COSTS", dec("3"), dec("3"), dec("10"), dec("1"),
                        dec("18"), dec("0.1"), dec("0.5"), dec("0"), dec("0"), true),
                BacktestSpec.EndOfRunPolicy.MARK_TO_MARKET, 10, 1L, BacktestEngine.ENGINE_REVISION,
                MARKET, null, "CANONICAL_M5", "fixture", contextSource);
    }

    @Test
    void derivedResearchContextProducesTradesFromCanonicalData() {
        BacktestResult result = new BacktestEngine(
                new FixtureReader(), new FeatureEngine(),
                new StrategyEngine(SetupFamilyRegistry.production()), new RiskEvaluator(),
                NseTradingCalendar.weekendsOnly())
                .run(spec(BacktestSpec.ContextSource.DERIVED_RESEARCH), BacktestContextProvider.strict(), null);
        assertThat(result.trades()).isNotEmpty();
    }

    private static BacktestContextProvider provider() {
        return new BacktestContextProvider() {
            @Override
            public MarketInput market(long instrumentId, Instant at) {
                return new MarketInput(true, "BULLISH", "RANGE", "BULL_IMPULSE");
            }

            @Override
            public StockInput stock(long instrumentId, Instant at) {
                return new StockInput(true, "LONG_ALIGNED", "POSITIVE", "VALID", 50_000_000.0, 1.0, true, false,
                        new StructureInput(dec("100.40"), dec("100.00"), dec("99.90"), dec("100.00")));
            }
        };
    }

    @Test
    void producesACompletedTradeWithCostsAndReconciledEquity() {
        BacktestEngine engine = new BacktestEngine(
                new FixtureReader(), new FeatureEngine(),
                new StrategyEngine(SetupFamilyRegistry.production()), new RiskEvaluator(), NseTradingCalendar.weekendsOnly());

        BacktestResult result = engine.run(spec(), provider(), null);

        System.out.println("DEBUG trades=" + result.trades().size() + " rejections=" + result.rejections().size()
                + " equity=" + result.equityPoints().size());
        result.rejections().stream().limit(5).forEach(r -> System.out.println("DEBUG REJ " + r));
        assertThat(result.trades()).isNotEmpty();
        BacktestTrade completed = result.trades().stream().filter(t -> !t.isOpen()).findFirst().orElseThrow();
        assertThat(completed.direction().name()).isEqualTo("LONG");
        assertThat(completed.exitReason()).isEqualTo("STOP");
        // Gap-through-stop: the fill is the (worse) open, not the stop level.
        assertThat(completed.exitPrice()).isLessThan(completed.entryPrice());
        // Warm-up history before the requested window was used for features, never for trades.
        assertThat(completed.entryAt()).isAfterOrEqualTo(Instant.parse("2026-09-09T00:00:00Z"));
        assertThat(completed.explicitCosts()).isGreaterThan(BigDecimal.ZERO);
        assertThat(completed.costBreakdown()).isNotEmpty();
        assertThat(completed.netPnl())
                .isEqualByComparingTo(completed.grossPnl().subtract(completed.explicitCosts()));
        assertThat(completed.realizedR()).isNotNull();

        // Equity reconciles: starting + realized net + open-marked P&L.
        BigDecimal realized = result.trades().stream().map(BacktestTrade::netPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalEquity = result.equityPoints().get(result.equityPoints().size() - 1).equity();
        BigDecimal openMarked = result.trades().stream().filter(BacktestTrade::isOpen)
                .map(BacktestTrade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(finalEquity.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(dec("1000000").add(realized).add(openMarked).setScale(2, RoundingMode.HALF_UP));
        assertThat(result.equityPoints().get(result.equityPoints().size() - 1).drawdown())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.processedEvents()).isGreaterThan(0);
    }

    @Test
    void researchPresetsRunWithoutFailingAndFailClosedInStrictMode() {
        BacktestSpec presetSpec = new BacktestSpec(
                "preset-run", List.of(SUBJECT), List.of("SUBJECT"),
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11), "M5", "D1",
                dec("1000000"), "INR", true,
                com.edgerelative.application.backtest.application.BacktestPresets
                        .strategy(com.edgerelative.application.backtest.application.BacktestPresets.STRATEGY_RS_RESEARCH)
                        .orElseThrow(),
                com.edgerelative.application.backtest.application.BacktestPresets
                        .risk(com.edgerelative.application.backtest.application.BacktestPresets.RISK_RESEARCH_PERMISSIVE)
                        .orElseThrow(),
                featurePolicy(),
                new BacktestSpec.ExecutionPolicy("test-exec", 0, dec("2"), dec("5"), BigDecimal.ONE, 1,
                        BacktestSpec.SessionCutoff.NEW_ENTRY_CUTOFF,
                        BacktestSpec.ExecutionPolicy.AmbiguityPolicy.STOP_FIRST_CONSERVATIVE, true),
                new BacktestSpec.CostSchedule("TEST_COSTS", dec("3"), dec("3"), dec("10"), dec("1"),
                        dec("18"), dec("0.1"), dec("0.5"), dec("0"), dec("0"), true),
                BacktestSpec.EndOfRunPolicy.MARK_TO_MARKET, 10, 1L, BacktestEngine.ENGINE_REVISION,
                MARKET, null, "CANONICAL_M5", "fixture", BacktestSpec.ContextSource.STRICT_PRODUCTION);

        BacktestResult result = new BacktestEngine(
                new FixtureReader(), new FeatureEngine(),
                new StrategyEngine(SetupFamilyRegistry.production()), new RiskEvaluator(),
                NseTradingCalendar.weekendsOnly())
                .run(presetSpec, BacktestContextProvider.strict(), null);

        // Strict production context has no producers, so the run completes with no fabricated trades.
        assertThat(result.trades()).isEmpty();
        assertThat(result.equityPoints()).isNotEmpty();
    }

    /** Deterministic reader: rising tight subject for history, then a gap below the stop. */
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
            for (int session = 0; session < SESSIONS.length; session++) {
                for (int bar = 0; bar < BARS_PER_SESSION; bar++) {
                    Instant at = SESSIONS[session].atTime(3, 45).toInstant(ZoneOffset.UTC)
                            .plus(Duration.ofMinutes(5L * bar));
                    if (session == SESSIONS.length - 1 && bar == 0) {
                        // Final session gaps below the protective stop.
                        candles.add(new AggregatedCandle(
                                at, at.plus(Duration.ofMinutes(5)), dec("99.00"), dec("99.00"), dec("98.50"),
                                dec("98.80"), 1000, null, 10, dec("98.80"), false, true, "GOOD", "v1"));
                        price = dec("98.80");
                        continue;
                    }
                    BigDecimal open = price;
                    price = price.add(dec("0.30"));
                    candles.add(new AggregatedCandle(
                            at, at.plus(Duration.ofMinutes(5)), open, price.add(dec("0.01")), price.subtract(dec("0.01")),
                            price, 1000, null, 10, price, false, true, "GOOD", "v1"));
                }
            }
            return candles;
        }

        private List<AggregatedCandle> marketM5() {
            List<AggregatedCandle> candles = new ArrayList<>();
            BigDecimal price = dec("20000");
            for (LocalDate session : SESSIONS) {
                for (int bar = 0; bar < BARS_PER_SESSION; bar++) {
                    Instant at = session.atTime(3, 45).toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(5L * bar));
                    BigDecimal open = price;
                    price = price.add(dec("1.0"));
                    candles.add(new AggregatedCandle(
                            at, at.plus(Duration.ofMinutes(5)), open, price.add(dec("1")), open.subtract(dec("1")),
                            price, 5000, null, 10, price, false, true, "GOOD", "v1"));
                }
            }
            return candles;
        }

        private List<AggregatedCandle> daily(long instrumentId) {
            List<AggregatedCandle> candles = new ArrayList<>();
            BigDecimal price = instrumentId == MARKET ? dec("20000") : dec("100");
            for (int i = 0; i < 6; i++) {
                price = price.add(instrumentId == MARKET ? BigDecimal.ZERO : dec("10"));
                Instant at = DAY1.minusDays(6 - i).atTime(10, 0).toInstant(ZoneOffset.UTC);
                BigDecimal open = instrumentId == MARKET ? price : price.subtract(dec("10"));
                candles.add(new AggregatedCandle(
                        at, at.plus(Duration.ofHours(6)), open, price.add(dec("1")), open.subtract(dec("1")), price,
                        100000, null, 100, price, false, true, "GOOD", "v1"));
            }
            return candles;
        }

    }
}
