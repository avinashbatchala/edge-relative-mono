package com.edgerelative.application.broker.api;

import java.math.BigDecimal;

/** Application contract for modifying a GTT/OCO. Deliberately not executable in this change. */
public record SmartOrderModifyApiRequest(
        String smartOrderType,
        String segment,
        Long quantity,
        BigDecimal triggerPrice,
        String triggerDirection,
        BigDecimal limitPrice,
        String orderType,
        String product,
        String duration,
        BigDecimal targetTriggerPrice,
        BigDecimal stopLossTriggerPrice) {
}
