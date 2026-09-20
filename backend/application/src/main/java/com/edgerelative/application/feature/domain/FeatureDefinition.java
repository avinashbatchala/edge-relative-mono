package com.edgerelative.application.feature.domain;

import java.util.List;

/**
 * Registry metadata for a feature (DD-05 §120). Dependencies are machine-readable so replay and
 * backfill can order computation deterministically without a generic workflow engine.
 */
public record FeatureDefinition(
        String featureKey,
        String name,
        String valueType,
        String description,
        List<String> dependencies,
        String pointInTimeSemantics) {

    public FeatureDefinition {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public static FeatureDefinition numeric(
            String key, String name, String description, List<String> dependencies) {
        return new FeatureDefinition(key, name, "DOUBLE", description, dependencies, "trailing-window");
    }

    public static FeatureDefinition categorical(
            String key, String name, String description, List<String> dependencies) {
        return new FeatureDefinition(key, name, "TEXT", description, dependencies, "trailing-window");
    }
}
