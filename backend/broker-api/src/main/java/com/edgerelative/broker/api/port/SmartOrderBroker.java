package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.error.BrokerOperationNotEnabledException;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderModifyRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;

/**
 * Broker-side GTT/OCO mutations.
 *
 * <p>Every implementation in this change MUST throw {@link BrokerOperationNotEnabledException} and
 * MUST NOT emit a downstream request.
 */
public interface SmartOrderBroker {

    BrokerSmartOrder createSmartOrder(BrokerSmartOrderRequest request);

    BrokerSmartOrder modifySmartOrder(String smartOrderId, BrokerSmartOrderModifyRequest request);

    BrokerSmartOrder cancelSmartOrder(BrokerSegment segment, BrokerSmartOrderType type, String smartOrderId);
}
