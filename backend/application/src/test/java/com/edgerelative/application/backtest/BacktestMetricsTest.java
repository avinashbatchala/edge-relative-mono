package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.domain.BacktestMetrics;
import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.domain.EquityPoint;
import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BacktestMetricsTest {

    private static final Instant T = Instant.parse("2026-09-18T04:30:00Z");

    private static BacktestTrade trade(String key, BigDecimal net, BigDecimal r) {
        return new BacktestTrade(
                key, 1L, "TCS", Direction.LONG, "M5_3_8_CONFIRMATION", T, new BigDecimal("100"), T.plusSeconds(300),
                new BigDecimal("101"), 100, new BigDecimal("2"), net.add(new BigDecimal("5")), new BigDecimal("5"), net, r,
                300L, "TARGET", 0, Map.of("brokerage", new BigDecimal("5")), "plan", "decision");
    }

    private static EquityPoint point(long seconds, String equity, String highWater, String drawdown) {
        return new EquityPoint(
                T.plusSeconds(seconds), new BigDecimal(equity), new BigDecimal(equity), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(highWater), new BigDecimal(drawdown), null, 0);
    }

    @Test
    void computesCoreMetricsFromLedgerAndEquity() {
        BacktestResult result = new BacktestResult(
                List.of(
                        trade("t1", new BigDecimal("125"), new BigDecimal("1.25")),
                        trade("t2", new BigDecimal("-50"), new BigDecimal("-0.50")),
                        trade("t3", new BigDecimal("25"), new BigDecimal("0.25"))),
                List.of(
                        point(0, "1000", "1000", "0"),
                        point(300, "1125", "1125", "0"),
                        point(600, "1075", "1125", "50"),
                        point(900, "1100", "1125", "25")),
                List.of(),
                4,
                4,
                Map.of());

        Map<String, Object> metrics = BacktestMetrics.compute(result, new BigDecimal("1000"), 18900);

        assertThat(new BigDecimal(String.valueOf(metrics.get("netPnl")))).isEqualByComparingTo("100");
        assertThat(metrics.get("completedTrades")).isEqualTo(3);
        assertThat(metrics.get("openPositions")).isEqualTo(0);
        assertThat(metrics.get("wins")).isEqualTo(2);
        assertThat(metrics.get("losses")).isEqualTo(1);
        assertThat(new BigDecimal(String.valueOf(metrics.get("winRatePct")))).isEqualByComparingTo("66.67");
        assertThat(new BigDecimal(String.valueOf(metrics.get("profitFactor")))).isEqualByComparingTo("3.000");
        assertThat(new BigDecimal(String.valueOf(metrics.get("maxDrawdown")))).isEqualByComparingTo("50");
        assertThat(new BigDecimal(String.valueOf(metrics.get("averageRealizedR")))).isEqualByComparingTo("0.333");
        assertThat(metrics.get("maxConsecutiveLosses")).isEqualTo(1);
    }

    @Test
    void undefinedMetricsAreNullWithExplanationsNotZero() {
        BacktestResult empty = new BacktestResult(List.of(), List.of(), List.of(), 0, 0, Map.of());
        Map<String, Object> metrics = BacktestMetrics.compute(empty, new BigDecimal("1000"), 18900);

        assertThat(metrics.get("winRatePct")).isNull();
        assertThat(metrics.get("averageWinner")).isNull();
        assertThat(metrics.get("averageLoser")).isNull();
        assertThat(metrics.get("expectancy")).isNull();
        assertThat(metrics.get("profitFactor")).isNull();
        assertThat(metrics.get("turnover")).isNull();
        assertThat(metrics.get("cagrPct")).isNull();
        assertThat(String.valueOf(metrics.get("notes"))).contains("No completed trades");
    }

    @Test
    void openPositionsAreSeparateFromCompletedTrades() {
        BacktestTrade open = new BacktestTrade(
                "open", 1L, "TCS", Direction.LONG, null, T, new BigDecimal("100"), null, null, 100,
                new BigDecimal("2"), new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("50"), null, null,
                "OPEN_MARKED_TO_MARKET", 0, Map.of(), "plan", "decision");
        BacktestResult result = new BacktestResult(List.of(open), List.of(point(0, "1050", "1050", "0")), List.of(), 1, 1, Map.of());
        Map<String, Object> metrics = BacktestMetrics.compute(result, new BigDecimal("1000"), 18900);
        assertThat(metrics.get("completedTrades")).isEqualTo(0);
        assertThat(metrics.get("openPositions")).isEqualTo(1);
        assertThat(metrics.get("winRatePct")).isNull();
    }
}
