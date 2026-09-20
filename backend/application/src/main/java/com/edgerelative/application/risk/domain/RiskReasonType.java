package com.edgerelative.application.risk.domain;

/** Reason classification; matches {@code control.risk_decision_reason.reason_type}. */
public enum RiskReasonType {
    INFO,
    REDUCTION,
    REJECTION,
    EXIT,
    HALT
}
