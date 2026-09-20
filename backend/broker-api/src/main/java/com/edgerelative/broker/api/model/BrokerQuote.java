package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full market quote snapshot for one instrument.
 */
public record BrokerQuote(
        BigDecimal lastPrice,
        BigDecimal averagePrice,
        BigDecimal dayChange,
        BigDecimal dayChangePercent,
        BigDecimal upperCircuitLimit,
        BigDecimal lowerCircuitLimit,
        BrokerOhlc ohlc,
        List<BrokerDepthLevel> bids,
        List<BrokerDepthLevel> asks,
        BigDecimal bidPrice,
        long bidQuantity,
        BigDecimal offerPrice,
        long offerQuantity,
        long volume,
        long lastTradeQuantity,
        Instant lastTradeTime,
        BigDecimal openInterest,
        BigDecimal previousOpenInterest,
        BigDecimal openInterestDayChange,
        BigDecimal openInterestDayChangePercentage,
        BigDecimal week52High,
        BigDecimal week52Low,
        BigDecimal impliedVolatility,
        BigDecimal marketCap,
        BigDecimal totalBuyQuantity,
        BigDecimal totalSellQuantity) {
}
