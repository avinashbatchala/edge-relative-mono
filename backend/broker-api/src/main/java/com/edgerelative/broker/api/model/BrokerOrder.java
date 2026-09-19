package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Normalised order view. */
public record BrokerOrder(
        String brokerOrderId,
        String orderReferenceId,
        String tradingSymbol,
        BrokerExchange exchange,
        BrokerSegment segment,
        BrokerTransactionType transactionType,
        BrokerOrderType orderType,
        BrokerProduct product,
        BrokerValidity validity,
        BrokerOrderStatus status,
        long quantity,
        BigDecimal price,
        BigDecimal triggerPrice,
        long filledQuantity,
        long remainingQuantity,
        BigDecimal averageFillPrice,
        long deliverableQuantity,
        String remark,
        Instant createdAt,
        Instant exchangeTime,
        Instant tradeDate) {
}
