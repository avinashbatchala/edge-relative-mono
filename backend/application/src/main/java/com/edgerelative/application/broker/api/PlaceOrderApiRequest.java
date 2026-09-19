package com.edgerelative.application.broker.api;

import java.math.BigDecimal;

/** Application contract for placing an order. Deliberately not executable in this change. */
public record PlaceOrderApiRequest(
        String tradingSymbol,
        long quantity,
        BigDecimal price,
        BigDecimal triggerPrice,
        String validity,
        String exchange,
        String segment,
        String product,
        String orderType,
        String transactionType,
        String orderReferenceId) {
}
