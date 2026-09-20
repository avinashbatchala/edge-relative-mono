package com.edgerelative.application.backtest.domain;

import java.util.List;
import java.util.Map;

/** Deterministic output of one run: simulated trades, equity samples, rejections, and progress. */
public record BacktestResult(
        List<BacktestTrade> trades,
        List<EquityPoint> equityPoints,
        List<BacktestRejection> rejections,
        long processedEvents,
        long totalEvents,
        Map<String, Long> stageCounts) {

    public BacktestResult {
        trades = trades == null ? List.of() : List.copyOf(trades);
        equityPoints = equityPoints == null ? List.of() : List.copyOf(equityPoints);
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
        stageCounts = stageCounts == null ? Map.of() : Map.copyOf(stageCounts);
    }
}
