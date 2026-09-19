package com.edgerelative.application.history;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A candle produced by the deterministic aggregator (or passed through for M1).
 *
 * <p>{@code partial} marks a bar truncated by the session boundary (e.g. the final H1/H4 bar of an
 * NSE day). {@code complete} marks a finalized historical bar; {@code qualityState} is
 * {@code INCOMPLETE} when a required minute is missing from the underlying base (DD-05 §93/§97/§103).
 * {@code definitionVersion} records the construction rules so a change in session or aggregation
 * semantics is visible in lineage.
 */
public record AggregatedCandle(
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal openInterest,
        Integer tradeCount,
        BigDecimal vwap,
        boolean partial,
        boolean complete,
        String qualityState,
        String definitionVersion) {
}
