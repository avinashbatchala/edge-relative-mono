package com.edgerelative.application.tradeplan.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, server-side eligibility. Boundaries are explicit: a plan is eligible while
 * {@code now < expiresAt} and {@code now < entryCutoffAt}; exactly at a boundary it is expired.
 * Eligibility additionally requires fresh required inputs and an open session. Viewing a plan never
 * renews or reapproves it.
 */
public final class TradePlanEligibilityEvaluator {

    public static final String TRIGGER_LIFETIME_EXPIRED = "TRIGGER_LIFETIME_EXPIRED";
    public static final String ENTRY_CUTOFF_REACHED = "ENTRY_CUTOFF_REACHED";
    public static final String REQUIRED_INPUTS_STALE = "REQUIRED_INPUTS_STALE";
    public static final String SESSION_NOT_OPEN = "SESSION_NOT_OPEN";
    public static final String SESSION_UNKNOWN = "SESSION_UNKNOWN";
    public static final String VALIDITY_POLICY_UNCONFIGURED = "VALIDITY_POLICY_UNCONFIGURED";

    private TradePlanEligibilityEvaluator() {
    }

    public static PlanEligibility evaluate(
            TradePlan plan, Instant now, boolean requiredInputsFresh, String sessionContext) {
        return evaluate(plan.validFrom(), plan.expiresAt(), plan.entryCutoffAt(), now, requiredInputsFresh, sessionContext);
    }

    public static PlanEligibility evaluate(
            Instant validFrom,
            Instant expiresAt,
            Instant entryCutoffAt,
            Instant now,
            boolean requiredInputsFresh,
            String sessionContext) {
        List<String> reasons = new ArrayList<>();
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            reasons.add(TRIGGER_LIFETIME_EXPIRED);
            return new PlanEligibility(TradePlanStatus.EXPIRED, now, validFrom, expiresAt, entryCutoffAt, reasons);
        }
        if (entryCutoffAt != null && !now.isBefore(entryCutoffAt)) {
            reasons.add(ENTRY_CUTOFF_REACHED);
            return new PlanEligibility(TradePlanStatus.EXPIRED, now, validFrom, expiresAt, entryCutoffAt, reasons);
        }
        if (expiresAt == null && entryCutoffAt == null) {
            reasons.add(VALIDITY_POLICY_UNCONFIGURED);
            return new PlanEligibility(TradePlanStatus.UNKNOWN, now, validFrom, null, null, reasons);
        }
        if (!requiredInputsFresh) {
            reasons.add(REQUIRED_INPUTS_STALE);
            return new PlanEligibility(TradePlanStatus.PENDING, now, validFrom, expiresAt, entryCutoffAt, reasons);
        }
        if (sessionContext == null) {
            reasons.add(SESSION_UNKNOWN);
            return new PlanEligibility(TradePlanStatus.PENDING, now, validFrom, expiresAt, entryCutoffAt, reasons);
        }
        if (!"OPEN".equals(sessionContext)) {
            reasons.add(SESSION_NOT_OPEN);
            return new PlanEligibility(TradePlanStatus.PENDING, now, validFrom, expiresAt, entryCutoffAt, reasons);
        }
        return new PlanEligibility(TradePlanStatus.ELIGIBLE, now, validFrom, expiresAt, entryCutoffAt, reasons);
    }
}
