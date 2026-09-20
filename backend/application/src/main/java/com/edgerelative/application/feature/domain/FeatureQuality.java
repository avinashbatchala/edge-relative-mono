package com.edgerelative.application.feature.domain;

/**
 * Canonical data-quality state for a feature value (DD-05 quality model).
 *
 * <p>The values mirror the {@code control}/catalog quality vocabulary already enforced by the
 * database so persisted features use one vocabulary. Severity is monotonic so derived features can
 * inherit the worst input quality deterministically (DD-05 §132/§138).
 */
public enum FeatureQuality {
    GOOD(0),
    CORRECTED(1),
    DEGRADED(2),
    SUSPECT(3),
    STALE(4),
    INCOMPLETE(5),
    UNAVAILABLE(6);

    private final int severity;

    FeatureQuality(int severity) {
        this.severity = severity;
    }

    public boolean trustworthy() {
        return this == GOOD || this == CORRECTED;
    }

    /**
     * The worse of two states by monotonic severity.
     */
    public static FeatureQuality worst(FeatureQuality left, FeatureQuality right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.severity >= right.severity ? left : right;
    }

    public static FeatureQuality worst(Iterable<FeatureQuality> qualities) {
        FeatureQuality result = GOOD;
        for (FeatureQuality quality : qualities) {
            result = worst(result, quality);
        }
        return result;
    }
}
