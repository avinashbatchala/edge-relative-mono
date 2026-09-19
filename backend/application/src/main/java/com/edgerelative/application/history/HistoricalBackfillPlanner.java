package com.edgerelative.application.history;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a requested window into provider-valid chunks.
 *
 * <p>The maximum window is supplied by the broker adapter capability, never hard-coded here.
 */
public final class HistoricalBackfillPlanner {

    private HistoricalBackfillPlanner() {
    }

    public record Chunk(Instant start, Instant end) {
    }

    public static List<Chunk> plan(Instant from, Instant to, Duration maxWindow) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new HistoryException(HistoryException.INVALID, "'from' must be before 'to'");
        }
        if (maxWindow == null || maxWindow.isZero() || maxWindow.isNegative()) {
            throw new HistoryException(HistoryException.INVALID, "Provider maximum window is unknown");
        }
        List<Chunk> chunks = new ArrayList<>();
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            Instant next = cursor.plus(maxWindow);
            Instant end = next.isAfter(to) ? to : next;
            chunks.add(new Chunk(cursor, end));
            cursor = end;
        }
        return chunks;
    }
}
