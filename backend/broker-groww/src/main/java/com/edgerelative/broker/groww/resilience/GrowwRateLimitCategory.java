package com.edgerelative.broker.groww.resilience;

/**
 * Groww applies rate limits at type level, not per endpoint. Endpoints that share a type share a quota.
 */
public enum GrowwRateLimitCategory {
    AUTHENTICATION,
    ORDERS,
    LIVE_DATA,
    NON_TRADING
}
