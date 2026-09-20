package com.edgerelative.application.feature.api;

import com.edgerelative.application.feature.domain.ContextSnapshot;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Market or sector context for one anchor.
 */
public record ContextSnapshotResponse(
        String contextType,
        Long instrumentId,
        String referenceCode,
        Long sectorId,
        Instant anchorTimestamp,
        String timeframe,
        String quality,
        Map<String, FeatureValueResponse> features) {

    public static ContextSnapshotResponse from(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, FeatureValueResponse> features = new LinkedHashMap<>();
        snapshot.features().forEach((key, value) -> features.put(key, FeatureValueResponse.from(value)));
        return new ContextSnapshotResponse(
                snapshot.contextType(),
                snapshot.instrumentId(),
                snapshot.referenceCode(),
                snapshot.sectorId(),
                snapshot.anchorTimestamp(),
                snapshot.timeframe(),
                snapshot.quality().name(),
                features);
    }
}
