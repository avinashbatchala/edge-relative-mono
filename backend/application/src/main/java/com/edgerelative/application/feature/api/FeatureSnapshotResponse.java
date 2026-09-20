package com.edgerelative.application.feature.api;

import com.edgerelative.application.feature.domain.FeatureSnapshot;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Point-in-time feature snapshot: measurement only, never a strategy decision (DD-05 §130).
 */
public record FeatureSnapshotResponse(
        long instrumentId,
        Instant anchorTimestamp,
        String timeframe,
        String featureSchemaVersion,
        String quality,
        String availability,
        BenchmarkResponse benchmark,
        Map<String, FeatureValueResponse> features,
        ContextSnapshotResponse market,
        ContextSnapshotResponse sector) {

    public static FeatureSnapshotResponse from(FeatureSnapshot snapshot) {
        Map<String, FeatureValueResponse> features = new LinkedHashMap<>();
        snapshot.features().forEach((key, value) -> features.put(key, FeatureValueResponse.from(value)));
        return new FeatureSnapshotResponse(
                snapshot.instrumentId(),
                snapshot.anchorTimestamp(),
                snapshot.timeframe(),
                snapshot.featureSchemaVersion(),
                snapshot.quality().name(),
                snapshot.availability().name(),
                BenchmarkResponse.from(snapshot.benchmark()),
                features,
                ContextSnapshotResponse.from(snapshot.market()),
                ContextSnapshotResponse.from(snapshot.sector()));
    }
}
