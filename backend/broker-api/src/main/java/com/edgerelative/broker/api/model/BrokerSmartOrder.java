package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Normalised smart order (GTT/OCO).
 *
 * <p>GTT and OCO carry different leg structures. The broker-neutral model keeps a {@code details}
 * map for leg-specific values so the neutral contract never has to emulate one broker's shape, while
 * the common fields are typed.
 */
public record BrokerSmartOrder(
        String smartOrderId,
        BrokerSmartOrderType type,
        BrokerSmartOrderStatus status,
        String tradingSymbol,
        BrokerExchange exchange,
        BrokerSegment segment,
        long quantity,
        BrokerProduct product,
        BrokerValidity duration,
        BigDecimal triggerPrice,
        String triggerDirection,
        BigDecimal lastPrice,
        boolean cancellationAllowed,
        boolean modificationAllowed,
        Instant createdAt,
        Instant expireAt,
        Instant triggeredAt,
        Instant updatedAt,
        Map<String, Object> details) {

    public BrokerSmartOrder {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
