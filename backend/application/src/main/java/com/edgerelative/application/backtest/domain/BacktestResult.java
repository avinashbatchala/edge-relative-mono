package com.edgerelative.application.backtest.domain;

import java.util.List;

/** Deterministic output of one run: simulated trades, equity samples, rejections, and progress. */
public record BacktestResult(
        List<BacktestTrade> trades,
        List<EquityPoint> equityPoints,
        List<BacktestRejection> rejections,
        long processedEvents,
        long totalEvents) {

    public BacktestResult {
        trades = trades == null ? List.of() : List.copyOf(trades);
        equityPoints = equityPoints == null ? List.of() : List.copyOf(equityPoints);
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
    }
}
