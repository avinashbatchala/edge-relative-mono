package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/**
 * Margin requirement computed by the broker for a basket. All amounts in rupees.
 */
public record BrokerMarginRequirement(
        BigDecimal exposureRequired,
        BigDecimal spanRequired,
        BigDecimal optionBuyPremium,
        BigDecimal brokerageAndCharges,
        BigDecimal totalRequirement,
        BigDecimal cashCncMarginRequired,
        BigDecimal cashMisMarginRequired,
        BigDecimal physicalDeliveryMarginRequirement) {
}
