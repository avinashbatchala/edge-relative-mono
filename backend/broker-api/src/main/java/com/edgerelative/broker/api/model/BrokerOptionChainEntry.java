package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** One option contract within an option chain. */
public record BrokerOptionChainEntry(
        String tradingSymbol,
        BigDecimal lastPrice,
        long openInterest,
        long volume,
        BrokerOptionGreeks greeks) {
}
