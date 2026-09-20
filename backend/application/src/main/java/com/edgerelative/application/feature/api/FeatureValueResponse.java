package com.edgerelative.application.feature.api;

import com.edgerelative.application.feature.domain.FeatureValue;

import java.util.Map;

/**
 * Broker-neutral feature value DTO (DD-05 §48; never exposes internal Java types).
 */
public record FeatureValueResponse(
        String featureKey,
        String featureVersion,
        String parameterHash,
        String timeframe,
        String availability,
        String quality,
        Double value,
        String label,
        Map<String, String> lineage) {

    public static FeatureValueResponse from(FeatureValue value) {
        return new FeatureValueResponse(
                value.featureKey(),
                value.version().semanticVersion(),
                value.version().parameterHash(),
                value.timeframe(),
                value.availability().name(),
                value.quality().name(),
                value.value(),
                value.label(),
                value.lineage());
    }
}
