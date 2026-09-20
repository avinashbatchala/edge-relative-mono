package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TOTP-mode token request body.
 */
public record GrowwTokenTotpRequest(
        @JsonProperty("key_type") String keyType, @JsonProperty("totp") String totp) {
}
