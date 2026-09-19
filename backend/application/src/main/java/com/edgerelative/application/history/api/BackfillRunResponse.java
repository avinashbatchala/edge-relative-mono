package com.edgerelative.application.history.api;

import java.time.Instant;

/** Progress/state of one backfill run. */
public record BackfillRunResponse(
        String runKey,
        long instrumentId,
        String timeframe,
        Instant requestedFrom,
        Instant requestedTo,
        String status,
        int totalChunks,
        int completedChunks,
        int failedChunks,
        long candlesWritten,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
