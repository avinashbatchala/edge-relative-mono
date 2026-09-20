package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerMargin;
import com.edgerelative.broker.api.model.BrokerMarginOrder;
import com.edgerelative.broker.api.model.BrokerMarginRequirement;
import com.edgerelative.broker.api.model.BrokerSegment;

import java.util.List;

/**
 * Margin information. {@link #requiredMargin} is a pure calculation and therefore read-only despite POST.
 */
public interface MarginBroker {

    BrokerMargin userMargin();

    BrokerMarginRequirement requiredMargin(BrokerSegment segment, List<BrokerMarginOrder> orders);
}
