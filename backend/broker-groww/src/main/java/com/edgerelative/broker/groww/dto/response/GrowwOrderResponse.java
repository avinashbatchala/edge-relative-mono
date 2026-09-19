package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** Groww order status/details/list item. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwOrderResponse(
        @JsonProperty("groww_order_id") String growwOrderId,
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("remark") String remark,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("price") JsonNode price,
        @JsonProperty("trigger_price") JsonNode triggerPrice,
        @JsonProperty("filled_quantity") Long filledQuantity,
        @JsonProperty("remaining_quantity") Long remainingQuantity,
        @JsonProperty("average_fill_price") JsonNode averageFillPrice,
        @JsonProperty("deliverable_quantity") Long deliverableQuantity,
        @JsonProperty("amo_status") String amoStatus,
        @JsonProperty("validity") String validity,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("order_type") String orderType,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("segment") String segment,
        @JsonProperty("product") String product,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("exchange_time") String exchangeTime,
        @JsonProperty("trade_date") String tradeDate,
        @JsonProperty("order_reference_id") String orderReferenceId) {
}
