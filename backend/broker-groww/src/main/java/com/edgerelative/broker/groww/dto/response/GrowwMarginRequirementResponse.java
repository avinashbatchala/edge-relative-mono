package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** {@code POST /v1/margins/detail/orders} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwMarginRequirementResponse(
        @JsonProperty("exposure_required") JsonNode exposureRequired,
        @JsonProperty("span_required") JsonNode spanRequired,
        @JsonProperty("option_buy_premium") JsonNode optionBuyPremium,
        @JsonProperty("brokerage_and_charges") JsonNode brokerageAndCharges,
        @JsonProperty("total_requirement") JsonNode totalRequirement,
        @JsonProperty("cash_cnc_margin_required") JsonNode cashCncMarginRequired,
        @JsonProperty("cash_mis_margin_required") JsonNode cashMisMarginRequired,
        @JsonProperty("physical_delivery_margin_requirement") JsonNode physicalDeliveryMarginRequirement) {
}
