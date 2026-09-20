package com.edgerelative.application.history.api;

import java.time.Instant;

/**
 * Persisted coverage summary for one canonical instrument and timeframe.
 */
public record CoverageResponse(
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
