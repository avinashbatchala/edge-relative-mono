package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/**
 * Call and put entries for a single strike.
 */
public record BrokerOptionChainStrike(
        BigDecimal strikePrice,
        BrokerOptionChainEntry call,
        BrokerOptionChainEntry put) {
}
