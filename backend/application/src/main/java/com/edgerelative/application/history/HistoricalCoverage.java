package com.edgerelative.application.history;

import java.time.Instant;

/**
 * Broker-neutral coverage summary for one canonical instrument and timeframe.
 */
public record HistoricalCoverage(
        long instrumentId,
        String timeframe,
        Instant earliest,
        Instant latest,
        long candleCount,
        int completedChunks,
        int pendingChunks,
        int failedChunks,
        Instant lastSyncedAt,
        String status) {
}
