package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.error.BrokerOperationNotEnabledException;
import com.edgerelative.broker.api.model.BrokerCancelOrderRequest;
import com.edgerelative.broker.api.model.BrokerModifyOrderRequest;
import com.edgerelative.broker.api.model.BrokerOrderReference;
import com.edgerelative.broker.api.model.BrokerOrderRequest;

/**
 * Broker-side order mutations.
 *
 * <p>Every implementation in this change MUST throw {@link BrokerOperationNotEnabledException} and
 * MUST NOT emit a downstream request. A future live implementation must persist intent before
 * submission and reconcile ambiguous outcomes.
 */
public interface ExecutionBroker {

    BrokerOrderReference placeOrder(BrokerOrderRequest request);

    BrokerOrderReference modifyOrder(BrokerModifyOrderRequest request);

    BrokerOrderReference cancelOrder(BrokerCancelOrderRequest request);
}
