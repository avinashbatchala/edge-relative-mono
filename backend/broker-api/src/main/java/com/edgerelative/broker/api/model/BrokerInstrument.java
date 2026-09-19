package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Broker-published instrument master row.
 *
 * <p>The broker's symbol/token is never Edge Relative identity. The adapter preserves the broker
 * identifiers so the reference-data layer can build a temporal {@code broker_instrument_mapping}.
 */
public record BrokerInstrument(
        BrokerExchange exchange,
        String exchangeToken,
        String tradingSymbol,
        String brokerSymbol,
        String name,
        BrokerInstrumentType instrumentType,
        BrokerSegment segment,
        String series,
        String isin,
        String underlyingSymbol,
        String underlyingExchangeToken,
        long lotSize,
        LocalDate expiryDate,
        BigDecimal strikePrice,
        BigDecimal tickSize,
        Long freezeQuantity,
        boolean reserved,
        boolean buyAllowed,
        boolean sellAllowed) {
}
