package com.edgerelative.broker.api.model;

/** Exchange on which a broker instrument trades. */
public enum BrokerExchange {
    NSE,
    BSE,
    /** Multi Commodity Exchange. Present in the instrument master; not an authorised trading venue yet. */
    MCX
}
