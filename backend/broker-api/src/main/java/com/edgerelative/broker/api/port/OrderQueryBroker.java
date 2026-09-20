package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerOrder;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerTrade;
import com.edgerelative.broker.api.model.OrderListQuery;
import com.edgerelative.broker.api.model.TradeListQuery;

import java.util.List;

/**
 * Read-only order and trade queries.
 */
public interface OrderQueryBroker {

    BrokerOrder orderStatus(String brokerOrderId, BrokerSegment segment);

    BrokerOrder orderStatusByReference(String orderReferenceId, BrokerSegment segment);

    BrokerOrder orderDetail(String brokerOrderId, BrokerSegment segment);

    List<BrokerOrder> orders(OrderListQuery query);

    List<BrokerTrade> trades(String brokerOrderId, TradeListQuery query);
}
