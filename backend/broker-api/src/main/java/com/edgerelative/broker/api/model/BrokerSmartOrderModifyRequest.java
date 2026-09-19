package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** Broker-neutral smart-order modification intent (represented, not executable in this change). */
public record BrokerSmartOrderModifyRequest(
        BrokerSmartOrderType smartOrderType,
        BrokerSegment segment,
        Long quantity,
        BigDecimal triggerPrice,
        String triggerDirection,
        BigDecimal limitPrice,
        BrokerOrderType orderType,
        BrokerProduct product,
        BrokerValidity duration,
        BigDecimal targetTriggerPrice,
        BigDecimal stopLossTriggerPrice) {
}
