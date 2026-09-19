package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One execution against an order. */
public record BrokerTrade(
        String brokerTradeId,
        String exchangeTradeId,
        String exchangeOrderId,
        String brokerOrderId,
        String tradingSymbol,
        String isin,
        BrokerExchange exchange,
        BrokerSegment segment,
        BrokerProduct product,
        BrokerTransactionType transactionType,
        BigDecimal price,
        long quantity,
        String tradeStatus,
        String settlementNumber,
        String remark,
        Instant createdAt,
        Instant tradeDateTime) {
}
