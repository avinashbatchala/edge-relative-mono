package com.edgerelative.application.feature.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Point-in-time context for an instrument (market or sector), kept separate from stock features so
 * the same snapshot can answer "is the market strong?", "is this sector strong vs market?" and
 * "is this stock strong vs sector?" without conflation (DD-05 §142/§173).
 */
public record ContextSnapshot(
        String contextType,
        Long instrumentId,
        String referenceCode,
        Long sectorId,
        Instant anchorTimestamp,
        String timeframe,
        FeatureQuality quality,
        Map<String, FeatureValue> features) {

    public ContextSnapshot {
        features = features == null ? Map.of() : Map.copyOf(features);
    }
}
