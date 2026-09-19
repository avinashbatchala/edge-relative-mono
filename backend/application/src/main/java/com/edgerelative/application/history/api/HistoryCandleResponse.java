package com.edgerelative.application.history.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A canonical candle. M1 is read from the database; higher timeframes are derived from it. The
 * {@code definitionVersion} records how the bar was constructed, and {@code partial} marks a bar
 * truncated by the session boundary.
 */
public record HistoryCandleResponse(
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
