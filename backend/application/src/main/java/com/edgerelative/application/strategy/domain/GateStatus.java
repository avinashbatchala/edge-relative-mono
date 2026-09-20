package com.edgerelative.application.strategy.domain;

/** Outcome of one gate evaluation. Never reduced to a bare boolean. */
public enum GateStatus {
    PASSED,
    FAILED,
    UNAVAILABLE,
    NOT_APPLICABLE;

    public boolean isPassed() {
        return this == PASSED;
    }
}
