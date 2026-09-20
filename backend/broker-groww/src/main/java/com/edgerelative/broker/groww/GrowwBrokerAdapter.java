package com.edgerelative.broker.groww;

import com.edgerelative.broker.api.model.BrokerCapabilities;
import com.edgerelative.broker.api.model.BrokerCapability;
import com.edgerelative.broker.api.port.BrokerAdapter;

import java.util.Set;

/**
 * Groww adapter identity and capability advertisement.
 *
 * <p>Execution capabilities are deliberately absent so callers can check capability rather than
 * assuming a mutation will work.
 */
public class GrowwBrokerAdapter implements BrokerAdapter {

    private static final Set<BrokerCapability> CAPABILITIES = Set.of(
            BrokerCapability.INSTRUMENT_MASTER,
            BrokerCapability.LIVE_QUOTE,
            BrokerCapability.LIVE_LTP,
            BrokerCapability.LIVE_OHLC,
            BrokerCapability.MARKET_DEPTH,
            BrokerCapability.OPTION_CHAIN,
            BrokerCapability.OPTION_GREEKS,
            BrokerCapability.HISTORICAL_CANDLES,
            BrokerCapability.HISTORICAL_EXPIRIES,
            BrokerCapability.HISTORICAL_CONTRACTS,
            BrokerCapability.ORDER_QUERY,
            BrokerCapability.TRADE_QUERY,
            BrokerCapability.POSITIONS,
            BrokerCapability.HOLDINGS,
            BrokerCapability.MARGIN,
            BrokerCapability.USER_PROFILE,
            BrokerCapability.SMART_ORDER_QUERY);

    @Override
    public String brokerName() {
        return "groww";
    }

    @Override
    public BrokerCapabilities capabilities() {
        return new BrokerCapabilities(brokerName(), CAPABILITIES);
    }
}
