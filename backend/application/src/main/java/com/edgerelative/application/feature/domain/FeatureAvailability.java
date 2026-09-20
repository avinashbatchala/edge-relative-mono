package com.edgerelative.application.feature.domain;

/**
 * Why a feature value is or is not present (DD-05 §126/§133).
 *
 * <p>Missing is never numeric zero. {@link #VALID} is the only state that guarantees a value;
 * {@link #WARMING_UP}/{@link #INSUFFICIENT_HISTORY}/{@link #MISSING_INPUT}/{@link #STALE}/
 * {@link #INCOMPLETE}/{@link #INVALID} carry no value and must not be coerced to one.
 */
public enum FeatureAvailability {
    VALID,
    WARMING_UP,
    INSUFFICIENT_HISTORY,
    MISSING_INPUT,
    STALE,
    INCOMPLETE,
    INVALID,
    NOT_APPLICABLE;

    public boolean hasValue() {
        return this == VALID;
    }
}
