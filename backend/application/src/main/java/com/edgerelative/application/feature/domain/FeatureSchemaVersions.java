package com.edgerelative.application.feature.domain;

/**
 * Describes the collection and semantics of columns in a snapshot (DD-05 §131). Adding or removing a
 * feature is not harmless to research datasets, so the schema version must move with the set.
 */
public final class FeatureSchemaVersions {

    private FeatureSchemaVersions() {
    }

    /**
     * First feature schema: ATR, RRS family, RVOL family, RVE, directional volume, market/sector context.
     */
    public static final String CURRENT = "er-feature-schema-v1";
}
