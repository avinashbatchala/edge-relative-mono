package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** One Groww position. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwPositionResponse(
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("segment") String segment,
        @JsonProperty("credit_quantity") Long creditQuantity,
        @JsonProperty("credit_price") JsonNode creditPrice,
        @JsonProperty("debit_quantity") Long debitQuantity,
        @JsonProperty("debit_price") JsonNode debitPrice,
        @JsonProperty("carry_forward_credit_quantity") Long carryForwardCreditQuantity,
        @JsonProperty("carry_forward_credit_price") JsonNode carryForwardCreditPrice,
        @JsonProperty("carry_forward_debit_quantity") Long carryForwardDebitQuantity,
        @JsonProperty("carry_forward_debit_price") JsonNode carryForwardDebitPrice,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("symbol_isin") String symbolIsin,
        @JsonProperty("quantity") Long quantity,
        @JsonProperty("product") String product,
        @JsonProperty("net_carry_forward_quantity") Long netCarryForwardQuantity,
        @JsonProperty("net_price") JsonNode netPrice,
        @JsonProperty("net_carry_forward_price") JsonNode netCarryForwardPrice,
        @JsonProperty("realised_pnl") JsonNode realisedPnl) {
}
