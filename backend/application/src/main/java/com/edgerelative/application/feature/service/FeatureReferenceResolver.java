package com.edgerelative.application.feature.service;

import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.reference.NseTradingCalendar;

import java.time.Instant;
import java.time.LocalDate;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

/**
 * Resolves the explicit benchmark relationships for an anchor (DD-05 §136/§141/§149).
 *
 * <p>Market benchmark identity and sector membership are read point-in-time from the reference
 * tables. Sector mapping is temporal: the sector valid at the anchor's session date is used, never
 * today's mapping. Unresolved relationships stay null so dependent features degrade instead of
 * substituting a proxy.
 */
@Service
public class FeatureReferenceResolver {

    private final DSLContext dsl;
    private final NseTradingCalendar calendar;

    public FeatureReferenceResolver(DSLContext dsl, NseTradingCalendar calendar) {
        this.dsl = dsl;
        this.calendar = calendar;
    }

    public BenchmarkIdentity resolve(long instrumentId, Instant anchor, String marketCode) {
        LocalDate sessionDate = calendar.sessionDate(anchor);
        Long marketInstrumentId = resolveMarket(marketCode);
        Record sector = dsl.fetchOne(
                "SELECT s.sector_id, s.code, m.source FROM reference.instrument_sector_history m "
                        + "JOIN reference.sector s ON s.sector_id = m.sector_id "
                        + "WHERE m.instrument_id = ? AND m.valid_from <= ? "
                        + "AND (m.valid_to IS NULL OR ? < m.valid_to) LIMIT 1",
                instrumentId,
                sessionDate,
                sessionDate);
        Long sectorId = sector == null ? null : sector.get("sector_id", Long.class);
        String sectorCode = sector == null ? null : sector.get("code", String.class);
        String mappingVersion = sector == null ? null : sector.get("source", String.class);
        Long sectorInstrumentId = sectorId == null ? null : resolveSectorInstrument(sectorId, sessionDate);
        return new BenchmarkIdentity(
                marketInstrumentId, marketCode, sectorId, sectorCode, mappingVersion, sectorInstrumentId, anchor);
    }

    private Long resolveMarket(String marketCode) {
        if (marketCode == null || marketCode.isBlank()) {
            return null;
        }
        Record benchmark = dsl.fetchOne(
                "SELECT COALESCE(b.instrument_id, i.instrument_id) AS instrument_id "
                        + "FROM reference.benchmark b "
                        + "LEFT JOIN reference.instrument i ON i.canonical_symbol = b.code "
                        + "WHERE b.code = ? AND b.active LIMIT 1",
                marketCode);
        if (benchmark != null && benchmark.get("instrument_id", Long.class) != null) {
            return benchmark.get("instrument_id", Long.class);
        }
        Record instrument = dsl.fetchOne(
                "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol = ? "
                        + "AND instrument_type = 'INDEX' LIMIT 1",
                marketCode);
        return instrument == null ? null : instrument.get("instrument_id", Long.class);
    }

    private Long resolveSectorInstrument(long sectorId, LocalDate sessionDate) {
        Record record = dsl.fetchOne(
                "SELECT COALESCE(b.instrument_id, i.instrument_id) AS instrument_id "
                        + "FROM reference.sector_benchmark_history h "
                        + "JOIN reference.benchmark b ON b.benchmark_id = h.benchmark_id "
                        + "LEFT JOIN reference.instrument i ON i.canonical_symbol = b.code "
                        + "WHERE h.sector_id = ? AND h.valid_from <= ? "
                        + "AND (h.valid_to IS NULL OR ? < h.valid_to) LIMIT 1",
                sectorId,
                sessionDate,
                sessionDate);
        return record == null ? null : record.get("instrument_id", Long.class);
    }
}
