package com.edgerelative.application.history;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical candle write input for the M1 base.
 *
 * <p>Broker/provider semantics stop at the ingestion boundary: the canonical store never accepts a
 * broker model ({@code BrokerCandle}) directly, so a provider change cannot ripple into persistence.
 * Callers map the provider candle to this type.
 */
public record NewCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal openInterest) {
}
