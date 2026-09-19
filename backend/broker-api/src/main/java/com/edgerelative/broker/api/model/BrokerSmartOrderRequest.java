package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Broker-neutral smart-order creation intent (GTT/OCO).
 *
 * <p>Represented at the application boundary but not executable in this change.
 */
public record BrokerSmartOrderRequest(
        BrokerSmartOrderType smartOrderType,
        String referenceId,
        BrokerExchange exchange,
        BrokerSegment segment,
        String tradingSymbol,
        long quantity,
        BrokerProduct product,
        BrokerValidity duration,
        BigDecimal triggerPrice,
        String triggerDirection,
        BigDecimal limitPrice,
        BrokerOrderType orderType,
        BrokerTransactionType transactionType,
        Long netPositionQuantity,
        BigDecimal targetTriggerPrice,
        BigDecimal stopLossTriggerPrice,
        LocalDate expiry) {

    public Map<String, Object> toDetails() {
        return Map.of();
    }
}
