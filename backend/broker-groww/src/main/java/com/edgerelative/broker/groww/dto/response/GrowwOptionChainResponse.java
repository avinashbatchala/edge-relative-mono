package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * {@code GET /v1/option-chain/...} payload. Strikes are keyed by strike price.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwOptionChainResponse(
        @JsonProperty("underlying_ltp") JsonNode underlyingLtp,
        @JsonProperty("strikes") Map<String, Strike> strikes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Strike(@JsonProperty("CE") Entry call, @JsonProperty("PE") Entry put) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            @JsonProperty("greeks") GrowwGreeksResponse.Greeks greeks,
            @JsonProperty("trading_symbol") String tradingSymbol,
            @JsonProperty("ltp") JsonNode lastPrice,
            @JsonProperty("open_interest") Long openInterest,
            @JsonProperty("volume") Long volume) {
    }
}
