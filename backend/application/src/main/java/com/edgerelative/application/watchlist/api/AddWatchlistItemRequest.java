package com.edgerelative.application.watchlist.api;

import java.math.BigDecimal;

/**
 * Broker-neutral request to watch a canonical instrument.
 */
public record AddWatchlistItemRequest(
        String exchange,
        String segment,
        String instrumentType,
        String symbol,
        String name,
        String brokerSymbol,
        BigDecimal tickSize,
        Long lotSize) {
}
