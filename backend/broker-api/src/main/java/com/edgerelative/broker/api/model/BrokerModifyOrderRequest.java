package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** Broker-neutral modify-order intent (represented, not executable in this change). */
public record BrokerModifyOrderRequest(
        String brokerOrderId,
        Long quantity,
        BigDecimal price,
        BigDecimal triggerPrice,
        BrokerOrderType orderType,
        BrokerSegment segment) {
}
