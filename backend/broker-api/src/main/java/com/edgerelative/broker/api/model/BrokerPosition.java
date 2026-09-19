package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** Normalised net position. */
public record BrokerPosition(
        String tradingSymbol,
        String isin,
        BrokerExchange exchange,
        BrokerSegment segment,
        BrokerProduct product,
        long quantity,
        BigDecimal netPrice,
        long creditQuantity,
        BigDecimal creditPrice,
        long debitQuantity,
        BigDecimal debitPrice,
        long carryForwardCreditQuantity,
        BigDecimal carryForwardCreditPrice,
        long carryForwardDebitQuantity,
        BigDecimal carryForwardDebitPrice,
        long netCarryForwardQuantity,
        BigDecimal netCarryForwardPrice,
        BigDecimal realisedPnl) {
}
