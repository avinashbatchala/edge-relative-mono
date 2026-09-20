package com.edgerelative.application.reference;

import com.edgerelative.application.reference.SectorReferenceCatalog.SectorBenchmark;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotently seeds the verified NSE sector/benchmark reference data and the configured
 * instrument-to-sector membership on startup.
 *
 * <p>Benchmark codes are the canonical index symbols verified in Groww's instrument master; the
 * benchmark's instrument link is resolved later (via {@code reference.benchmark.instrument_id} or the
 * canonical_symbol fallback) once the index is ingested. Sectors without a verified NSE index are not
 * seeded.
 */
@Component
public class SectorReferenceInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SectorReferenceInitializer.class);
    /** Membership is treated as valid from the epoch until a revision is recorded. */
    private static final LocalDate VALID_FROM = LocalDate.of(1970, 1, 1);

    private final DSLContext dsl;
    private final SectorMappingProperties properties;

    public SectorReferenceInitializer(DSLContext dsl, SectorMappingProperties properties) {
        this.dsl = dsl;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensure();
    }

    @Transactional
    public void ensure() {
        for (SectorBenchmark benchmark : SectorReferenceCatalog.NSE_SECTOR_BENCHMARKS) {
            long sectorId = ensureSector(benchmark);
            long benchmarkId = ensureBenchmark(benchmark);
            ensureSectorBenchmark(sectorId, benchmarkId);
        }
        properties.getSectorMap().forEach(this::mapInstrument);
        LOG.debug("Sector reference seeding complete ({} sectors configured)", properties.getSectorMap().size());
    }

    private long ensureSector(SectorBenchmark benchmark) {
        Record record = dsl.fetchOne(
                "INSERT INTO reference.sector (code, name) VALUES (?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING sector_id",
                benchmark.sectorCode(),
                benchmark.sectorName());
        return record.get("sector_id", Long.class);
    }

    private long ensureBenchmark(SectorBenchmark benchmark) {
        UUID key = UUID.nameUUIDFromBytes(
                ("benchmark:" + benchmark.benchmarkCode()).getBytes(StandardCharsets.UTF_8));
        Record record = dsl.fetchOne(
                "INSERT INTO reference.benchmark (benchmark_key, code, name, benchmark_type) "
                        + "VALUES (?, ?, ?, 'INDEX') "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING benchmark_id",
                key,
                benchmark.benchmarkCode(),
                benchmark.benchmarkName());
        return record.get("benchmark_id", Long.class);
    }

    private void ensureSectorBenchmark(long sectorId, long benchmarkId) {
        Record existing = dsl.fetchOne(
                "SELECT 1 AS present FROM reference.sector_benchmark_history "
                        + "WHERE sector_id = ? AND valid_to IS NULL",
                sectorId);
        if (existing != null) {
            return;
        }
        dsl.execute(
                "INSERT INTO reference.sector_benchmark_history (sector_id, benchmark_id, valid_from) "
                        + "VALUES (?, ?, ?)",
                sectorId,
                benchmarkId,
                VALID_FROM);
    }

    private void mapInstrument(String symbol, String sectorCode) {
        Record instrument = dsl.fetchOne(
                "SELECT instrument_id FROM reference.instrument "
                        + "WHERE canonical_symbol = ? AND instrument_type = 'EQUITY' LIMIT 1",
                symbol.trim().toUpperCase());
        if (instrument == null) {
            return;
        }
        Record sector = dsl.fetchOne("SELECT sector_id FROM reference.sector WHERE code = ?", sectorCode);
        if (sector == null) {
            LOG.warn("Configured sector {} for {} is not a seeded sector", sectorCode, symbol);
            return;
        }
        long instrumentId = instrument.get("instrument_id", Long.class);
        Record existing = dsl.fetchOne(
                "SELECT 1 AS present FROM reference.instrument_sector_history "
                        + "WHERE instrument_id = ? AND valid_to IS NULL",
                instrumentId);
        if (existing != null) {
            return;
        }
        dsl.execute(
                "INSERT INTO reference.instrument_sector_history (instrument_id, sector_id, valid_from, source) "
                        + "VALUES (?, ?, ?, ?)",
                instrumentId,
                sector.get("sector_id", Long.class),
                VALID_FROM,
                "seed-v1");
    }
}
