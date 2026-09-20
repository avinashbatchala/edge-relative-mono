package com.edgerelative.application.tradeplan.domain;

/**
 * Server-evaluated eligibility of an immutable trade plan at a point in time. Execution progress is
 * separate (this is not a fill state). A UI countdown is never authoritative.
 */
public enum TradePlanStatus {
    /** All known validity and freshness conditions hold. */
    ELIGIBLE,
    /** Trigger lifetime or entry cutoff has passed. */
    EXPIRED,
    /** A defined invalidating event has occurred. */
    INVALIDATED,
    /** Plan was explicitly cancelled before execution. */
    CANCELLED,
    /** A newer approved decision/plan supersedes this one. */
    SUPERSEDED,
    /** Cannot yet confirm eligibility (missing/uncertain authoritative inputs). */
    PENDING,
    /** No validity policy is configured, so eligibility cannot be asserted. */
    UNKNOWN
}
