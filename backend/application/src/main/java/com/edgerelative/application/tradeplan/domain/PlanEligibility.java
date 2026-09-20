package com.edgerelative.application.tradeplan.domain;

import java.time.Instant;
import java.util.List;

/** Structured eligibility with reasons; computed server-side, never from a client timer. */
public record PlanEligibility(
        TradePlanStatus status,
        Instant evaluatedAt,
        Instant validFrom,
        Instant expiresAt,
        Instant entryCutoffAt,
        List<String> reasons) {

    public PlanEligibility {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean permitsEntry() {
        return status == TradePlanStatus.ELIGIBLE;
    }
}
