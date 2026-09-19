package com.edgerelative.application.watchlist.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One watched instrument using canonical Edge Relative identity.
 *
 * <p>{@code brokerSymbol} is a resolved broker mapping detail for market-data calls, never identity.
 */
public record WatchlistEntry(
        long instrumentId,
        UUID instrumentKey,
        String exchange,
        String segment,
        String instrumentType,
        String symbol,
        String name,
        String brokerSymbol,
        BigDecimal tickSize,
        Long lotSize,
        int slot) {
}
