package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** One Groww trade. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwTradeResponse(
        @JsonProperty("price") JsonNode price,
        @JsonProperty("isin") String isin,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("groww_order_id") String growwOrderId,
        @JsonProperty("groww_trade_id") String growwTradeId,
        @JsonProperty("exchange_trade_id") String exchangeTradeId,
        @JsonProperty("exchange_order_id") String exchangeOrderId,
        @JsonProperty("trade_status") String tradeStatus,
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("remark") String remark,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("segment") String segment,
        @JsonProperty("product") String product,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("trade_date_time") String tradeDateTime,
        @JsonProperty("settlement_number") String settlementNumber) {
}
