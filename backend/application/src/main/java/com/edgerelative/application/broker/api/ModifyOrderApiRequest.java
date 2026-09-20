package com.edgerelative.application.broker.api;

import java.math.BigDecimal;

/**
 * Application contract for modifying an order. Deliberately not executable in this change.
 */
public record ModifyOrderApiRequest(
        String brokerOrderId,
        Long quantity,
        BigDecimal price,
        BigDecimal triggerPrice,
        String orderType,
        String segment) {
}
