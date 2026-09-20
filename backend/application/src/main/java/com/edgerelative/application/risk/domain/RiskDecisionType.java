package com.edgerelative.application.risk.domain;

/** DD-03 decision outcomes. */
public enum RiskDecisionType {
    APPROVE,
    REDUCE,
    REJECT,
    EXIT_REQUIRED,
    HALT_REQUIRED;

    public boolean authorizesNewRisk() {
        return this == APPROVE || this == REDUCE;
    }
}
