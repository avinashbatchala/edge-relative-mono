package com.edgerelative.application.strategy.domain;

/** Deterministic setup lifecycle states (DD-02 §74). */
public enum SetupState {
    NONE,
    WATCH,
    FORMING,
    NEAR_TRIGGER,
    VALID,
    INVALIDATED,
    EXPIRED,
    MISSED,
    BLOCKED;

    /** Terminal states never silently reopen; a new opportunity is a new setup instance. */
    public boolean isTerminal() {
        return this == INVALIDATED || this == EXPIRED || this == MISSED || this == BLOCKED;
    }
}
