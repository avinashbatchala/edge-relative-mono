package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code GET /v1/positions/...} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwPositionListResponse(@JsonProperty("positions") List<GrowwPositionResponse> positions) {
}
