package com.edgerelative.application.feature.domain;

import java.time.Instant;

/**
 * The resolved, explicit benchmark relationships for one anchor (DD-05 §136/§137/§149/§150).
 *
 * <p>A relative feature always records which instruments it compared. {@code null} ids mean the
 * relationship could not be resolved point-in-time, which degrades the dependent feature rather than
 * substituting an arbitrary proxy.
 */
public record BenchmarkIdentity(
        Long marketInstrumentId,
        String marketCode,
        Long sectorId,
        String sectorCode,
        String sectorMappingVersion,
        Long sectorInstrumentId,
        Instant resolvedAt) {

    public BenchmarkIdentity {
        resolvedAt = resolvedAt == null ? null : resolvedAt;
    }

    public boolean hasMarket() {
        return marketInstrumentId != null;
    }

    public boolean hasSector() {
        return sectorInstrumentId != null;
    }
}
