package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /v1/live-data/greeks/...} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwGreeksResponse(@JsonProperty("greeks") Greeks greeks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Greeks(
            @JsonProperty("delta") Double delta,
            @JsonProperty("gamma") Double gamma,
            @JsonProperty("theta") Double theta,
            @JsonProperty("vega") Double vega,
            @JsonProperty("rho") Double rho,
            @JsonProperty("iv") Double impliedVolatility) {
    }
}
