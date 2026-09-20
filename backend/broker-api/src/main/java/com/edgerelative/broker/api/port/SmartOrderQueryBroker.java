package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.SmartOrderListQuery;

import java.util.List;

/**
 * Read-only GTT/OCO queries.
 */
public interface SmartOrderQueryBroker {

    BrokerSmartOrder smartOrder(BrokerSegment segment, BrokerSmartOrderType type, String smartOrderId);

    List<BrokerSmartOrder> smartOrders(SmartOrderListQuery query);
}
