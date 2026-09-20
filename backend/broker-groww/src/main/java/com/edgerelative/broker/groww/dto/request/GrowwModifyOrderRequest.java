package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * {@code POST /v1/order/modify} body. Represented only; not executed in this change.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrowwModifyOrderRequest(
        @JsonProperty("groww_order_id") String growwOrderId,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("trigger_price") BigDecimal triggerPrice,
        @JsonProperty("order_type") String orderType,
        @JsonProperty("segment") String segment) {
}
