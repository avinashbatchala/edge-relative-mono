package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code GET /v1/order/list} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwOrderListResponse(@JsonProperty("order_list") List<GrowwOrderResponse> orders) {
}
