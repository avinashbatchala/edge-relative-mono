package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code GET /v1/order-advance/list} payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwSmartOrderListResponse(@JsonProperty("orders") List<GrowwSmartOrderResponse> orders) {
}
