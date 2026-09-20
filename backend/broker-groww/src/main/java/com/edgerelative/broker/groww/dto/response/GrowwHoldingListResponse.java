package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code GET /v1/holdings/user} payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwHoldingListResponse(@JsonProperty("holdings") List<GrowwHoldingResponse> holdings) {
}
