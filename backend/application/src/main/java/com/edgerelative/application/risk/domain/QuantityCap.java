package com.edgerelative.application.risk.domain;

/**
 * One independent upper bound on quantity. {@code NOT_APPLICABLE} (optional model explicitly
 * disabled) is distinct from {@code UNAVAILABLE} (mandatory input missing, which blocks approval).
 * A disabled optional cap must never be manufactured as a zero quantity.
 */
public record QuantityCap(
        String name, ConstraintStatus status, long quantity, RiskReasonCode reasonCode, boolean binding) {

    public static QuantityCap of(String name, long quantity) {
        return new QuantityCap(name, ConstraintStatus.PASS, quantity, null, false);
    }

    public static QuantityCap notApplicable(String name) {
        return new QuantityCap(name, ConstraintStatus.NOT_APPLICABLE, 0, null, false);
    }

    public static QuantityCap unavailable(String name, RiskReasonCode reasonCode) {
        return new QuantityCap(name, ConstraintStatus.UNAVAILABLE, 0, reasonCode, false);
    }

    public boolean applicable() {
        return status == ConstraintStatus.PASS || status == ConstraintStatus.FAIL;
    }

    public QuantityCap asBinding() {
        return new QuantityCap(name, ConstraintStatus.FAIL, quantity, reasonCode, true);
    }

    public QuantityCap withReason(RiskReasonCode code) {
        return new QuantityCap(name, status, quantity, code, binding);
    }
}
