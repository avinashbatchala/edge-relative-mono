package com.edgerelative.broker.groww.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire response for {@code POST /v1/token/api/access}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwTokenResponse(
        @JsonProperty("token") String token,
        @JsonProperty("tokenRefId") String tokenRefId,
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("expiry") String expiry,
        @JsonProperty("isActive") Boolean isActive) {
}
