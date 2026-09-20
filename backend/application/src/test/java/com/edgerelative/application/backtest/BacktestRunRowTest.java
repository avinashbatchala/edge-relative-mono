package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.application.BacktestRunRow;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BacktestRunRowTest {

    @Test
    void toleratesNullMetricValuesWithoutThrowing() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("netReturnPct", null);
        metrics.put("completedTrades", 0);

        BacktestRunRow row = new BacktestRunRow(
                "run", 1, 2, "SUCCEEDED", "ER_RS_CONTINUATION_V1", "v1", "CANONICAL_M5", "abc",
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 18), 3,
                java.math.BigDecimal.ONE, "INR", 0, 0L, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
                Instant.EPOCH, metrics, null, Map.of("symbols", java.util.List.of("SBIN")));

        assertThat(row.metrics()).containsKey("netReturnPct");
        assertThat(row.metrics().get("netReturnPct")).isNull();
        assertThat(row.failure()).isEmpty();
        assertThat(row.symbols()).containsExactly("SBIN");
    }
}
