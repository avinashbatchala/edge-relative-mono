package com.edgerelative.application.strategy.domain;

/**
 * Stable, versioned reason codes. Human-readable explanations are derived from these codes and the
 * structured gate facts, never the other way round.
 */
public enum ReasonCode {
    // DD-02 invalid reasons
    MARKET_OPPOSING,
    MARKET_DISLOCATED,
    DAILY_NOT_ALIGNED,
    RRS_D1_FAILED,
    RRS_M5_FAILED,
    RVOL_FAILED,
    LIQUIDITY_FAILED,
    NO_TECHNICAL_VOID,
    OPENING_BLACKOUT,
    NO_CONFIRMED_TRIGGER,
    ENTRY_EXTENDED,
    EVENT_BLOCKED,
    NO_STRUCTURAL_INVALIDATION,
    TRIGGER_EXPIRED,
    DATA_INVALID,

    // Lifecycle / structural
    SETUP_STRUCTURE_NOT_FOUND,
    SETUP_STRUCTURE_FAILED,
    TRIGGER_NOT_NEAR,
    SESSION_ENTRY_CUTOFF,
    MARKET_TRANSITION_RESTRICTED,
    MISSING_REQUIRED_DEPENDENCY,
    STALE_REQUIRED_DEPENDENCY,
    ILLEGAL_STATE_TRANSITION,
    NEUTRAL_MARKET_BLOCKED;

    /** Deterministic primary-reason ordering; the first present code is reported as primary. */
    public int priority() {
        return ordinal();
    }
}
