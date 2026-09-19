package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Approval-mode token request body. */
public record GrowwTokenApprovalRequest(
        @JsonProperty("key_type") String keyType,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("timestamp") String timestamp) {
}
