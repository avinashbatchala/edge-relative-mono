package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code PUT /v1/order-advance/modify/{id}} body. Represented only; not executed in this change. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrowwSmartOrderModifyRequest(
        @JsonProperty("smart_order_type") String smartOrderType,
        @JsonProperty("segment") String segment,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("trigger_price") String triggerPrice,
        @JsonProperty("trigger_direction") String triggerDirection,
        @JsonProperty("order") GrowwSmartOrderCreateRequest.Leg order,
        @JsonProperty("duration") String duration,
        @JsonProperty("product_type") String productType,
        @JsonProperty("target") GrowwSmartOrderCreateRequest.Leg target,
        @JsonProperty("stop_loss") GrowwSmartOrderCreateRequest.Leg stopLoss) {
}
