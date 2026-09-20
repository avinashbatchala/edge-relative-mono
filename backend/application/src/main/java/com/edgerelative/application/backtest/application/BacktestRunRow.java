package com.edgerelative.application.backtest.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read model for a backtest run (list and detail). */
public record BacktestRunRow(
        String runKey,
        long experimentRunId,
        long backtestRunId,
        String status,
        String strategyId,
        String strategyVersion,
        String datasetCode,
        String datasetChecksum,
        LocalDate startDate,
        LocalDate endDate,
        int universeSize,
        BigDecimal startingCapital,
        String currency,
        long progressEvents,
        Long progressTotal,
        Instant progressThrough,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> metrics,
        Map<String, Object> failure,
        Map<String, Object> parameters) {

    public BacktestRunRow {
        // Undefined metrics are deliberately null values, so Map.copyOf (which rejects nulls) cannot
        // be used here. Preserve the values in an unmodifiable, null-tolerant view.
        metrics = immutable(metrics);
        failure = immutable(failure);
        parameters = immutable(parameters);
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        return value == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(value));
    }

    public List<String> symbols() {
        Object value = parameters.get("symbols");
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
