package com.edgerelative.application.feature.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The point-in-time analytical state of one instrument/timeframe under a schema version (DD-05
 * §130). It is measurement only: it never contains a strategy decision, position, or future label.
 *
 * <p>Component quality is preserved on every {@link FeatureValue}; {@code quality} is the aggregate
 * over the snapshot's required components. A stock may have a snapshot even when no setup exists.
 */
public record FeatureSnapshot(
        long instrumentId,
        Instant anchorTimestamp,
        String timeframe,
        String featureSchemaVersion,
        FeatureQuality quality,
        FeatureAvailability availability,
        BenchmarkIdentity benchmark,
        Map<String, FeatureValue> features,
        ContextSnapshot market,
        ContextSnapshot sector) {

    public FeatureSnapshot {
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    public FeatureValue feature(String featureKey) {
        return features.get(featureKey);
    }

    public List<FeatureValue> values() {
        return List.copyOf(features.values());
    }

    /**
     * Snapshot quality excluding context; useful when only stock features are consumed.
     */
    public FeatureQuality stockQuality() {
        return FeatureQuality.worst(features.values().stream()
                .map(FeatureValue::quality)
                .collect(Collectors.toList()));
    }
}
