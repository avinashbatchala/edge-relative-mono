package com.edgerelative.application.tradeplan.application;

/**
 * Authoritative freshness/authority check for a plan's required inputs. Until a producer is wired the
 * default returns false, so eligibility fails closed (PENDING) rather than assuming fresh data.
 */
@FunctionalInterface
public interface TradePlanTrustPort {

    boolean requiredInputsFresh(TradePlanRow row);
}
