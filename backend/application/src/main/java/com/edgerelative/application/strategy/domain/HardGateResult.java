package com.edgerelative.application.strategy.domain;

/**
 * Structured hard-gate outcome so the system can explain which rule produced a decision.
 *
 * @param actualValue the observed value/state
 * @param reference the threshold or reference the gate compared against
 * @param sourceObservation identity of the observation the gate read, when applicable
 */
public record HardGateResult(
        GateCode gateCode,
        String gateVersion,
        boolean required,
        GateStatus status,
        String actualValue,
        String reference,
        ReasonCode reasonCode,
        String sourceObservation) {

    public static HardGateResult passed(
            GateCode code, String actual, String reference, String sourceObservation) {
        return new HardGateResult(code, "1", true, GateStatus.PASSED, actual, reference, null, sourceObservation);
    }

    public static HardGateResult failed(
            GateCode code, String actual, String reference, ReasonCode reason, String sourceObservation) {
        return new HardGateResult(code, "1", true, GateStatus.FAILED, actual, reference, reason, sourceObservation);
    }

    public static HardGateResult unavailable(GateCode code, ReasonCode reason, String sourceObservation) {
        return new HardGateResult(code, "1", true, GateStatus.UNAVAILABLE, null, null, reason, sourceObservation);
    }
}
