package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/**
 * One leg of a margin calculation request.
 */
public record BrokerMarginOrder(
        String tradingSymbol,
        long quantity,
        BigDecimal price,
        BrokerExchange exchange,
        BrokerSegment segment,
        BrokerProduct product,
        BrokerOrderType orderType,
        BrokerTransactionType transactionType) {
}
