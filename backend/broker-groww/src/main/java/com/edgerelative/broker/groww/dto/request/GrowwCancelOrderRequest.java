package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code POST /v1/order/cancel} body. Represented only; not executed in this change. */
public record GrowwCancelOrderRequest(
        @JsonProperty("groww_order_id") String growwOrderId, @JsonProperty("segment") String segment) {
}
