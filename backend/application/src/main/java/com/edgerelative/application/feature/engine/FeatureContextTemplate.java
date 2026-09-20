package com.edgerelative.application.feature.engine;

import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.reference.NseTradingCalendar;

/**
 * Binds a subject series to its resolved benchmarks for the live store.
 */
public record FeatureContextTemplate(
        long subjectInstrumentId,
        String timeframe,
        Long marketInstrumentId,
        String marketCode,
        Long sectorInstrumentId,
        String sectorCode,
        Long sectorId,
        String sectorMappingVersion,
        FeaturePolicy policy,
        FeatureVersions versions,
        NseTradingCalendar calendar) {

    public BenchmarkIdentity benchmark() {
        return new BenchmarkIdentity(
                marketInstrumentId,
                marketCode,
                sectorId,
                sectorCode,
                sectorMappingVersion,
                sectorInstrumentId,
                null);
    }
}
