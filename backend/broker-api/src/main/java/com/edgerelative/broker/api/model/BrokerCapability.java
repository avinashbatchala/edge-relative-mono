package com.edgerelative.broker.api.model;

/** Capabilities a broker adapter may or may not support. */
public enum BrokerCapability {
    INSTRUMENT_MASTER,
    LIVE_QUOTE,
    LIVE_LTP,
    LIVE_OHLC,
    MARKET_DEPTH,
    OPTION_CHAIN,
    OPTION_GREEKS,
    HISTORICAL_CANDLES,
    HISTORICAL_EXPIRIES,
    HISTORICAL_CONTRACTS,
    ORDER_QUERY,
    TRADE_QUERY,
    POSITIONS,
    HOLDINGS,
    MARGIN,
    USER_PROFILE,
    SMART_ORDER_QUERY,
    ORDER_EXECUTION,
    SMART_ORDER_EXECUTION
}
