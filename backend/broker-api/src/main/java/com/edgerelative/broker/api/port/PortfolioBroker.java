package com.edgerelative.broker.api.port;

import com.edgerelative.broker.api.model.BrokerHolding;
import com.edgerelative.broker.api.model.BrokerPosition;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerUserProfile;

import java.util.List;

/**
 * Positions, holdings and account profile.
 */
public interface PortfolioBroker {

    List<BrokerHolding> holdings();

    List<BrokerPosition> positions(BrokerSegment segment);

    List<BrokerPosition> positionsForSymbol(String tradingSymbol, BrokerSegment segment);

    BrokerUserProfile userProfile();
}
