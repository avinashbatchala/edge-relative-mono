package com.edgerelative.application.history.api;

import java.time.Instant;

/**
 * Requested historical range for one canonical instrument. Only the M1 base may be requested.
 */
public record StartBackfillRequest(
        long instrumentId, String timeframe, Instant from, Instant to) {
}
