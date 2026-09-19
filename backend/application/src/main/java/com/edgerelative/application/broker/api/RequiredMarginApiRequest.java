package com.edgerelative.application.broker.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application request for a required-margin calculation. This is read-only: the adapter maps it to
 * Groww's calculation-only endpoint.
 */
public record RequiredMarginApiRequest(String segment, List<OrderLeg> orders) {

    public record OrderLeg(
            String tradingSymbol,
            long quantity,
            BigDecimal price,
            String exchange,
            String segment,
            String product,
            String orderType,
            String transactionType) {
    }
}
