package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** {@code GET /v1/historical/expiries} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwExpiriesResponse(@JsonProperty("expiries") List<String> expiries) {
}
