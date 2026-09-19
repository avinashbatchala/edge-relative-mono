package com.edgerelative.application.history;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A candle produced by the deterministic aggregator.
 *
 * <p>{@code partial} marks a bar truncated by the session boundary (e.g. the final H1/H4 bar of an
 * NSE day). {@code definitionVersion} records the construction rules so a change in session or
 * aggregation semantics is visible in lineage.
 */
public record AggregatedCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal openInterest,
        boolean partial,
        String definitionVersion) {
}
