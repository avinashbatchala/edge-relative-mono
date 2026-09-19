package com.edgerelative.broker.groww.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** {@code POST /v1/order/create} body. Represented only; no adapter executes it in this change. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrowwPlaceOrderRequest(
        @JsonProperty("trading_symbol") String tradingSymbol,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("trigger_price") BigDecimal triggerPrice,
        @JsonProperty("validity") String validity,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("segment") String segment,
        @JsonProperty("product") String product,
        @JsonProperty("order_type") String orderType,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("order_reference_id") String orderReferenceId) {
}
