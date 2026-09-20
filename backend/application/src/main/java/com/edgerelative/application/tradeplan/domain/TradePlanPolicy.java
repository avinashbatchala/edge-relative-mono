package com.edgerelative.application.tradeplan.domain;

import java.math.BigDecimal;

/**
 * Versioned validity/entry policy for a trade plan. Every value is a research parameter: null means
 * "not configured" and the plan is created without inventing a value. No universal TTL is assumed.
 */
public record TradePlanPolicy(
        String code,
        int version,
        String reference,
        Integer validityMinutes,
        Integer entryCutoffMinutesBeforeClose,
        BigDecimal noChaseTicks,
        String entryMethod,
        String targetMethod,
        String stopBufferMethod) {

    public TradePlanPolicy {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("trade plan policy code is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("trade plan policy version must be >= 1");
        }
        if (validityMinutes != null && validityMinutes < 1) {
            throw new IllegalArgumentException("validityMinutes must be >= 1 when configured");
        }
        if (entryCutoffMinutesBeforeClose != null && entryCutoffMinutesBeforeClose < 0) {
            throw new IllegalArgumentException("entryCutoffMinutesBeforeClose must be >= 0");
        }
        if (noChaseTicks != null && noChaseTicks.signum() < 0) {
            throw new IllegalArgumentException("noChaseTicks must be >= 0");
        }
    }

    public String referenceOr(String fallback) {
        return reference == null || reference.isBlank() ? fallback : reference;
    }
}
