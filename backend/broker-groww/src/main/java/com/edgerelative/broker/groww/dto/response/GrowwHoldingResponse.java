package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * One Groww demat holding.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwHoldingResponse(
        @JsonProperty("isin") String isin,
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("average_price") JsonNode averagePrice,
        @JsonProperty("pledge_quantity") JsonNode pledgeQuantity,
        @JsonProperty("demat_locked_quantity") JsonNode dematLockedQuantity,
        @JsonProperty("groww_locked_quantity") JsonNode growwLockedQuantity,
        @JsonProperty("repledge_quantity") JsonNode repledgeQuantity,
        @JsonProperty("t1_quantity") JsonNode t1Quantity,
        @JsonProperty("demat_free_quantity") JsonNode dematFreeQuantity,
        @JsonProperty("corporate_action_additional_quantity") Long corporateActionAdditionalQuantity,
        @JsonProperty("active_demat_transfer_quantity") Long activeDematTransferQuantity) {
}
