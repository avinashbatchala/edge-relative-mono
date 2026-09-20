package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * {@code GET /v1/live-data/quote} payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwQuoteResponse(
        @JsonProperty("average_price") JsonNode averagePrice,
        @JsonProperty("bid_quantity") Long bidQuantity,
        @JsonProperty("bid_price") JsonNode bidPrice,
        @JsonProperty("day_change") JsonNode dayChange,
        @JsonProperty("day_change_perc") JsonNode dayChangePercent,
        @JsonProperty("upper_circuit_limit") JsonNode upperCircuitLimit,
        @JsonProperty("lower_circuit_limit") JsonNode lowerCircuitLimit,
        @JsonProperty("ohlc") JsonNode ohlc,
        @JsonProperty("depth") Depth depth,
        @JsonProperty("high_trade_range") JsonNode highTradeRange,
        @JsonProperty("implied_volatility") JsonNode impliedVolatility,
        @JsonProperty("last_trade_quantity") Long lastTradeQuantity,
        @JsonProperty("last_trade_time") Long lastTradeTime,
        @JsonProperty("low_trade_range") JsonNode lowTradeRange,
        @JsonProperty("last_price") JsonNode lastPrice,
        @JsonProperty("market_cap") JsonNode marketCap,
        @JsonProperty("offer_price") JsonNode offerPrice,
        @JsonProperty("offer_quantity") Long offerQuantity,
        @JsonProperty("oi_day_change") JsonNode openInterestDayChange,
        @JsonProperty("oi_day_change_percentage") JsonNode openInterestDayChangePercentage,
        @JsonProperty("open_interest") JsonNode openInterest,
        @JsonProperty("previous_open_interest") JsonNode previousOpenInterest,
        @JsonProperty("total_buy_quantity") JsonNode totalBuyQuantity,
        @JsonProperty("total_sell_quantity") JsonNode totalSellQuantity,
        @JsonProperty("volume") Long volume,
        @JsonProperty("week_52_high") JsonNode week52High,
        @JsonProperty("week_52_low") JsonNode week52Low) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Depth(@JsonProperty("buy") List<Level> buy, @JsonProperty("sell") List<Level> sell) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Level(@JsonProperty("price") JsonNode price, @JsonProperty("quantity") Long quantity) {
    }
}
