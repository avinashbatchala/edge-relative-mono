package com.edgerelative.application.feature.engine;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;

import java.util.Map;

/**
 * A raw calculation result before it is bound to a feature version and anchor. A value is only
 * present when {@code availability == VALID}; {@code quality} can still be degraded on a present
 * value (for example a stale benchmark bar that was still computable).
 */
public record Metric(Double value, String label, FeatureAvailability availability, FeatureQuality quality,
                     String reason) {

    public static Metric numeric(double value) {
        return new Metric(value, null, FeatureAvailability.VALID, FeatureQuality.GOOD, null);
    }

    public static Metric numeric(double value, FeatureQuality quality) {
        return new Metric(value, null, FeatureAvailability.VALID, quality, null);
    }

    public static Metric label(String label, FeatureQuality quality) {
        return new Metric(null, label, FeatureAvailability.VALID, quality, null);
    }

    public static Metric unavailable(FeatureAvailability availability, FeatureQuality quality, String reason) {
        return new Metric(null, null, availability, quality, reason);
    }

    public static Metric warmingUp() {
        return unavailable(FeatureAvailability.WARMING_UP, FeatureQuality.INCOMPLETE, "warmup");
    }

    public boolean available() {
        return availability == FeatureAvailability.VALID;
    }
}
