package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerCapabilities;

/** Common identity/capability surface every broker adapter exposes. */
public interface BrokerAdapter {

    String brokerName();

    BrokerCapabilities capabilities();
}
