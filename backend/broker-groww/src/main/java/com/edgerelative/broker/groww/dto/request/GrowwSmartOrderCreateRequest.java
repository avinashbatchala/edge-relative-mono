package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /v1/order-advance/create} body for GTT and OCO. Represented only; not executed in this
 * change.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrowwSmartOrderCreateRequest(
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("smart_order_type") String smartOrderType,
        @JsonProperty("segment") String segment,
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("trigger_price") String triggerPrice,
        @JsonProperty("trigger_direction") String triggerDirection,
        @JsonProperty("order") Leg order,
        @JsonProperty("net_position_quantity") Long netPositionQuantity,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("target") Leg target,
        @JsonProperty("stop_loss") Leg stopLoss,
        @JsonProperty("product_type") String productType,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("duration") String duration) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Leg(
            @JsonProperty("trigger_price") String triggerPrice,
            @JsonProperty("order_type") String orderType,
            @JsonProperty("price") String price,
            @JsonProperty("transaction_type") String transactionType) {
    }
}
