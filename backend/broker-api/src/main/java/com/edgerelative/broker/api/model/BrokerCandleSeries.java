package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministically ordered historical candles plus the resolved request window.
 */
public record BrokerCandleSeries(
        String exchangeSymbol,
        BrokerCandleInterval interval,
        java.time.Instant requestedStart,
        java.time.Instant requestedEnd,
        BigDecimal closingPrice,
        List<BrokerCandle> candles) {

    public BrokerCandleSeries {
        candles = List.copyOf(candles);
    }
}
