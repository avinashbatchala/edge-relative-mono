package com.edgerelative.broker.groww.resilience;

/**
 * Broker interaction health, distinct from Groww's own health.
 */
public enum GrowwBrokerState {
    ACTIVE,
    RATE_LIMITED,
    COOLING_DOWN,
    DEGRADED,
    AUTH_FAILURE,
    UNAVAILABLE
}
