package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** Demat holding. */
public record BrokerHolding(
        String isin,
        String tradingSymbol,
        long quantity,
        BigDecimal averagePrice,
        BigDecimal pledgeQuantity,
        BigDecimal dematLockedQuantity,
        BigDecimal brokerLockedQuantity,
        BigDecimal repledgeQuantity,
        BigDecimal t1Quantity,
        BigDecimal dematFreeQuantity,
        long corporateActionAdditionalQuantity,
        long activeDematTransferQuantity) {
}
