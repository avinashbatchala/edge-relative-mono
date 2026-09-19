package com.edgerelative.broker.groww.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** {@code GET /v1/margins/detail/user} payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GrowwMarginResponse(
        @JsonProperty("clear_cash") JsonNode clearCash,
        @JsonProperty("net_margin_used") JsonNode netMarginUsed,
        @JsonProperty("brokerage_and_charges") JsonNode brokerageAndCharges,
        @JsonProperty("collateral_used") JsonNode collateralUsed,
        @JsonProperty("collateral_available") JsonNode collateralAvailable,
        @JsonProperty("adhoc_margin") JsonNode adhocMargin,
        @JsonProperty("fno_margin_details") Fno fnoMarginDetails,
        @JsonProperty("equity_margin_details") Equity equityMarginDetails) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fno(
            @JsonProperty("net_fno_margin_used") JsonNode netFnoMarginUsed,
            @JsonProperty("span_margin_used") JsonNode spanMarginUsed,
            @JsonProperty("exposure_margin_used") JsonNode exposureMarginUsed,
            @JsonProperty("future_balance_available") JsonNode futureBalanceAvailable,
            @JsonProperty("option_buy_balance_available") JsonNode optionBuyBalanceAvailable,
            @JsonProperty("option_sell_balance_available") JsonNode optionSellBalanceAvailable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Equity(
            @JsonProperty("net_equity_margin_used") JsonNode netEquityMarginUsed,
            @JsonProperty("cnc_margin_used") JsonNode cncMarginUsed,
            @JsonProperty("mis_margin_used") JsonNode misMarginUsed,
            @JsonProperty("cnc_balance_available") JsonNode cncBalanceAvailable,
            @JsonProperty("mis_balance_available") JsonNode misBalanceAvailable) {
    }
}
