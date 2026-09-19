package com.edgerelative.broker.groww.execution;

import com.edgerelative.broker.api.error.BrokerOperationNotEnabledException;
import com.edgerelative.broker.api.model.BrokerCancelOrderRequest;
import com.edgerelative.broker.api.model.BrokerModifyOrderRequest;
import com.edgerelative.broker.api.model.BrokerOrderReference;
import com.edgerelative.broker.api.model.BrokerOrderRequest;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderModifyRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderRequest;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.port.ExecutionBroker;
import com.edgerelative.broker.api.port.SmartOrderBroker;

/**
 * Safe default execution adapter: every broker-side mutation is refused before any HTTP call.
 *
 * <p>This class has no reference to the HTTP client, so it is structurally incapable of reaching
 * Groww. Enabling execution requires a deliberate, separately reviewed implementation.
 */
public class GrowwDisabledExecutionAdapter implements ExecutionBroker, SmartOrderBroker {

    public static final String BROKER = "groww";

    @Override
    public BrokerOrderReference placeOrder(BrokerOrderRequest request) {
        throw new BrokerOperationNotEnabledException(BROKER, "PLACE_ORDER");
    }

    @Override
    public BrokerOrderReference modifyOrder(BrokerModifyOrderRequest request) {
        throw new BrokerOperationNotEnabledException(BROKER, "MODIFY_ORDER");
    }

    @Override
    public BrokerOrderReference cancelOrder(BrokerCancelOrderRequest request) {
        throw new BrokerOperationNotEnabledException(BROKER, "CANCEL_ORDER");
    }

    @Override
    public BrokerSmartOrder createSmartOrder(BrokerSmartOrderRequest request) {
        throw new BrokerOperationNotEnabledException(BROKER, "SMART_ORDER_CREATE");
    }

    @Override
    public BrokerSmartOrder modifySmartOrder(String smartOrderId, BrokerSmartOrderModifyRequest request) {
        throw new BrokerOperationNotEnabledException(BROKER, "SMART_ORDER_MODIFY");
    }

    @Override
    public BrokerSmartOrder cancelSmartOrder(BrokerSegment segment, BrokerSmartOrderType type, String smartOrderId) {
        throw new BrokerOperationNotEnabledException(BROKER, "SMART_ORDER_CANCEL");
    }
}
