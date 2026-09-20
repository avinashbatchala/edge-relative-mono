package com.edgerelative.application.feature.api;

import com.edgerelative.application.feature.domain.BenchmarkIdentity;

/**
 * Explicit benchmark identity persisted/served with a snapshot (DD-05 §136).
 */
public record BenchmarkResponse(
        Long marketInstrumentId,
        String marketCode,
        Long sectorId,
        String sectorCode,
        String sectorMappingVersion,
        Long sectorInstrumentId) {

    public static BenchmarkResponse from(BenchmarkIdentity identity) {
        if (identity == null) {
            return null;
        }
        return new BenchmarkResponse(
                identity.marketInstrumentId(),
                identity.marketCode(),
                identity.sectorId(),
                identity.sectorCode(),
                identity.sectorMappingVersion(),
                identity.sectorInstrumentId());
    }
}
