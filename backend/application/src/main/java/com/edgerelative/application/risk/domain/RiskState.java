package com.edgerelative.application.risk.domain;

/** Canonical account risk states (DD-03 §44). */
public enum RiskState {
    NORMAL,
    REDUCED_1,
    REDUCED_2,
    NO_NEW_RISK,
    FLATTEN_ONLY,
    HALTED;

    /** New exposure is prohibited in the final three states. */
    public boolean permitsNewRisk() {
        return this == NORMAL || this == REDUCED_1 || this == REDUCED_2;
    }

    public boolean halted() {
        return this == HALTED;
    }
}
