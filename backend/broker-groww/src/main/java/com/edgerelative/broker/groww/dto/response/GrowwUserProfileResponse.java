package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code GET /v1/user/detail} payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwUserProfileResponse(
        @JsonProperty("vendor_user_id") String vendorUserId,
        @JsonProperty("ucc") String ucc,
        @JsonProperty("nse_enabled") Boolean nseEnabled,
        @JsonProperty("bse_enabled") Boolean bseEnabled,
        @JsonProperty("ddpi_enabled") Boolean ddpiEnabled,
        @JsonProperty("active_segments") List<String> activeSegments) {
}
