package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/**
 * Broker-neutral place-order intent.
 *
 * <p>This contract exists so the application boundary and validation are complete, but no adapter may
 * execute it until trading authority is explicitly enabled (see {@code ExecutionBroker}).
 */
public record BrokerOrderRequest(
        String tradingSymbol,
        long quantity,
        BigDecimal price,
        BigDecimal triggerPrice,
        BrokerValidity validity,
        BrokerExchange exchange,
        BrokerSegment segment,
        BrokerProduct product,
        BrokerOrderType orderType,
        BrokerTransactionType transactionType,
        String orderReferenceId) {
}
