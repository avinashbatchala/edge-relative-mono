package com.edgerelative.application.risk.domain;

/** Parameter promotion states (DD-03 §181); matches {@code control.risk_policy_version.lifecycle_state}. */
public enum PolicyState {
    EXPERIMENTAL,
    VALIDATED,
    PAPER,
    LIVE_LIMITED,
    PRODUCTION,
    RETIRED;

    /** Ranking used to check a policy is at least as promoted as a mode requires. */
    public int rank() {
        return ordinal();
    }

    public boolean atLeast(PolicyState required) {
        return rank() >= required.rank();
    }

    public boolean productionCalibrated() {
        return this == LIVE_LIMITED || this == PRODUCTION;
    }
}
