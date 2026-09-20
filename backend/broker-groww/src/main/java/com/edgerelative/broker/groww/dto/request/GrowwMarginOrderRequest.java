package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * One element of the {@code POST /v1/margins/detail/orders} basket (a pure calculation).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrowwMarginOrderRequest(
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("segment") String segment,
        @JsonProperty("product") String product,
        @JsonProperty("order_type") String orderType,
        @JsonProperty("transaction_type") String transactionType) {
}
