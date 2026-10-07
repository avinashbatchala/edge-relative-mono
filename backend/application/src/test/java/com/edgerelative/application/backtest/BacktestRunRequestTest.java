package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.application.BacktestRunRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BacktestRunRequestTest {

    private static BacktestRunRequest base() {
        return new BacktestRunRequest(
                List.of("SBIN"), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30), "M5", "D1",
                new BigDecimal("1000000"), "INR", "NIFTY", null, "ER_RS_CONTINUATION_V1_RESEARCH", null,
                "RESEARCH_PERMISSIVE", null, null, null, "DERIVED_RESEARCH", false, null, 60, 7L,
                "MARK_TO_MARKET", null, null);
    }

    @Test
    void withStrategyParametersCopiesTheBaseAndSetsTheSweepConfig() {
        BacktestRunRequest copied = base().withStrategyParameters(Map.of("rrsM5PersistenceLongMin", 0.5));
        assertThat(copied.symbols()).containsExactly("SBIN");
        assertThat(copied.timeframe()).isEqualTo("M5");
        assertThat(copied.warmupSessions()).isEqualTo(60);
        assertThat(copied.strictProducers()).isFalse();
        assertThat(copied.strategyParameters()).containsEntry("rrsM5PersistenceLongMin", 0.5);
        assertThat(base().strategyParameters()).isNull();
    }
}
