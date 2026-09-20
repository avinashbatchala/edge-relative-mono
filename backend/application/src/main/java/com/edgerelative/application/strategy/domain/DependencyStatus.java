package com.edgerelative.application.strategy.domain;

/** Availability of one required or optional strategy input. Missing is never neutral. */
public record DependencyStatus(String code, boolean required, DependencyState state) {

    public enum DependencyState {
        AVAILABLE,
        MISSING,
        STALE,
        WARMING_UP,
        INCOMPATIBLE,
        INVALID
    }

    public boolean blocking() {
        return required && state != DependencyState.AVAILABLE;
    }

    public ReasonCode reasonCode() {
        return switch (state) {
            case MISSING -> ReasonCode.MISSING_REQUIRED_DEPENDENCY;
            case STALE -> ReasonCode.STALE_REQUIRED_DEPENDENCY;
            case WARMING_UP, INCOMPATIBLE, INVALID -> ReasonCode.DATA_INVALID;
            case AVAILABLE -> null;
        };
    }
}
