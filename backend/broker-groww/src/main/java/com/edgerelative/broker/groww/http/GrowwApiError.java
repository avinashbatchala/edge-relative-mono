package com.edgerelative.broker.groww.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Groww documented error object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwApiError(@JsonProperty("code") String code, @JsonProperty("message") String message) {
}
