package com.edgerelative.broker.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Option chain snapshot for an underlying and expiry. */
public record BrokerOptionChain(
        String underlying,
        LocalDate expiryDate,
        BigDecimal underlyingLastPrice,
        List<BrokerOptionChainStrike> strikes) {

    public BrokerOptionChain {
        strikes = List.copyOf(strikes);
    }
}
