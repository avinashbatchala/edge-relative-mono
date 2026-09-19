package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One historical candle.
 *
 * @param openInterest open interest for FNO instruments, {@code null} otherwise
 */
public record BrokerCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal openInterest) {
}
