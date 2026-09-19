package com.edgerelative.application.history.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A canonical candle. M1 is read from the database; higher timeframes are derived from it.
 *
 * <p>{@code partial} marks a bar truncated by the session boundary; {@code complete} marks a
 * finalized historical bar; {@code qualityState} is {@code INCOMPLETE} when a required minute is
 * missing from the underlying base. {@code definitionVersion} records how the bar was constructed.
 */
public record HistoryCandleResponse(
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
