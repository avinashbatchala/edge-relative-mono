package com.edgerelative.application.risk.domain;

/**
 * Outcome of one evaluated constraint. UNAVAILABLE is distinct from NOT_APPLICABLE: a mandatory
 * limit that cannot be evaluated blocks approval, while an explicitly disabled optional model
 * produces NOT_APPLICABLE and sets no quantity cap.
 */
public enum ConstraintStatus {
    PASS,
    FAIL,
    NOT_APPLICABLE,
    UNAVAILABLE
}
