package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/**
 * Last traded price for one instrument.
 *
 * @param exchangeSymbol broker-qualified symbol, e.g. {@code NSE_RELIANCE}
 */
public record BrokerLtp(String exchangeSymbol, BigDecimal lastPrice) {
}
