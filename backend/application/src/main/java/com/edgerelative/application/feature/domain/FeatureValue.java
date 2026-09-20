package com.edgerelative.application.feature.domain;

import java.time.Instant;
import java.util.Map;

/**
 * One point-in-time feature observation with quality and lineage (DD-05 §127).
 *
 * <p>{@code value} is present only when {@code availability == VALID}; a categorical feature uses
 * {@code label} instead. A missing value is never represented as {@code 0.0}.
 */
public record FeatureValue(
        String featureKey,
        FeatureVersion version,
        Instant anchorTimestamp,
        String timeframe,
        FeatureAvailability availability,
        FeatureQuality quality,
        Double value,
        String label,
        Map<String, String> lineage) {

    public FeatureValue {
        lineage = lineage == null ? Map.of() : Map.copyOf(lineage);
        if (availability == FeatureAvailability.VALID && value == null && label == null) {
            throw new IllegalArgumentException("VALID feature must carry a value or label: " + featureKey);
        }
        if (availability != FeatureAvailability.VALID && (value != null || label != null)) {
            throw new IllegalArgumentException("non-VALID feature must not carry a value: " + featureKey);
        }
    }

    public static FeatureValue numeric(
            FeatureVersion version, Instant anchor, String timeframe, double value, FeatureQuality quality) {
        return new FeatureValue(
                version.featureKey(),
                version,
                anchor,
                timeframe,
                FeatureAvailability.VALID,
                quality,
                value,
                null,
                Map.of());
    }

    public static FeatureValue categorical(
            FeatureVersion version,
            Instant anchor,
            String timeframe,
            String label,
            FeatureQuality quality) {
        return new FeatureValue(
                version.featureKey(),
                version,
                anchor,
                timeframe,
                FeatureAvailability.VALID,
                quality,
                null,
                label,
                Map.of());
    }

    public static FeatureValue unavailable(
            FeatureVersion version,
            Instant anchor,
            String timeframe,
            FeatureAvailability availability,
            FeatureQuality quality,
            String reason) {
        return new FeatureValue(
                version.featureKey(),
                version,
                anchor,
                timeframe,
                availability,
                quality,
                null,
                null,
                reason == null ? Map.of() : Map.of("reason", reason));
    }

    public FeatureValue withLineage(Map<String, String> lineage) {
        return new FeatureValue(
                featureKey, version, anchorTimestamp, timeframe, availability, quality, value, label, lineage);
    }
}
