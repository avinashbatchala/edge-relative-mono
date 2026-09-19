package com.edgerelative.application.broker.api;

import java.math.BigDecimal;

/**
 * Application contract for creating a GTT/OCO. Deliberately not executable in this change.
 */
public record SmartOrderCreateApiRequest(
        String smartOrderType,
        String referenceId,
        String exchange,
        String segment,
        String tradingSymbol,
        long quantity,
        String product,
        String duration,
        BigDecimal triggerPrice,
        String triggerDirection,
        BigDecimal limitPrice,
        String orderType,
        String transactionType,
        Long netPositionQuantity,
        BigDecimal targetTriggerPrice,
        BigDecimal stopLossTriggerPrice) {
}
