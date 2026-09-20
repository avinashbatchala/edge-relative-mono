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
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        failure = failure == null ? Map.of() : Map.copyOf(failure);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public List<String> symbols() {
        Object value = parameters.get("symbols");
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
