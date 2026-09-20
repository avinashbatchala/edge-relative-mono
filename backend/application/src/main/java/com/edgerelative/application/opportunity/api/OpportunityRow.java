package com.edgerelative.application.opportunity.api;

import java.time.Instant;

/**
 * One Opportunities workspace row: the latest setup for a watchlist instrument and its current
 * risk/plan state. Setup qualification, risk outcome, plan eligibility, and execution are distinct
 * fields; a VALID setup is not an approval.
 */
public record OpportunityRow(
        long instrumentId,
        String symbol,
        String displayName,
        String exchange,
        String segment,
        String timeframe,
        Long setupObservationId,
        String setupStatus,
        String direction,
        String setupFamily,
        Instant setupObservedAt,
        String setupInstanceId,
        String riskState,
        String riskDecisionKey,
        String riskDecision,
        String rejectionReason,
        String planKey,
        String planEligibilityStatus,
        boolean planPermitsEntry,
        Instant planExpiresAt) {
}
