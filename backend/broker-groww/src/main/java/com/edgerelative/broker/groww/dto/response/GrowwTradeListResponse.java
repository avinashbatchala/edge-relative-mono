package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code GET /v1/order/trades/{id}} payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwTradeListResponse(@JsonProperty("trade_list") List<GrowwTradeResponse> trades) {
}
