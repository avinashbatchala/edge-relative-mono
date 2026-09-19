package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * Smart order (GTT/OCO) response. Leg-specific structures are retained as raw nodes and surfaced
 * through the neutral model's details map, so the neutral contract never emulates a broker shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwSmartOrderResponse(
        @JsonProperty("smart_order_id") String smartOrderId,
        @JsonProperty("smart_order_type") String smartOrderType,
        @JsonProperty("status") String status,
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("segment") String segment,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("product_type") String productType,
        @JsonProperty("duration") String duration,
        @JsonProperty("trigger_price") JsonNode triggerPrice,
        @JsonProperty("trigger_direction") String triggerDirection,
        @JsonProperty("ltp") JsonNode lastPrice,
        @JsonProperty("is_cancellation_allowed") Boolean cancellationAllowed,
        @JsonProperty("is_modification_allowed") Boolean modificationAllowed,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("expire_at") String expireAt,
        @JsonProperty("triggered_at") String triggeredAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("order") JsonNode order,
        @JsonProperty("target") JsonNode target,
        @JsonProperty("stop_loss") JsonNode stopLoss,
        @JsonProperty("child_legs") JsonNode childLegs) {
}
