package com.edgerelative.application.history;

import java.math.BigDecimal;
import java.time.Instant;

/** A raw canonical candle read from the store. The M1 base is the only persisted series. */
public record HistoricalCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal openInterest) {
}
