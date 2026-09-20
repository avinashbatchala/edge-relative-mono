package com.edgerelative.application.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies the verified NSE sector/benchmark reference data is seeded and mapped. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SectorReferenceInitializerTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("sector_ref")
            .withUsername("sector_ref")
            .withPassword("sector_ref");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    private SectorReferenceInitializer initializer;

    @Autowired
    private CanonicalInstrumentService canonical;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedsVerifiedSectorBenchmarks() {
        Integer sectors = jdbc.queryForObject("SELECT count(*) FROM reference.sector", Integer.class);
        assertThat(sectors).isGreaterThanOrEqualTo(SectorReferenceCatalog.NSE_SECTOR_BENCHMARKS.size());

        String benchmark = jdbc.queryForObject(
                "SELECT b.code FROM reference.sector s "
                        + "JOIN reference.sector_benchmark_history h ON h.sector_id = s.sector_id "
                        + "JOIN reference.benchmark b ON b.benchmark_id = h.benchmark_id "
                        + "WHERE s.code = 'IT'",
                String.class);
        assertThat(benchmark).isEqualTo("NIFTYIT");

        Integer benchmarks = jdbc.queryForObject(
                "SELECT count(*) FROM reference.benchmark WHERE benchmark_type = 'INDEX'", Integer.class);
        assertThat(benchmarks).isGreaterThanOrEqualTo(SectorReferenceCatalog.NSE_SECTOR_BENCHMARKS.size());
    }

    @Test
    void mapsConfiguredInstrumentToSectorOnceItExists() {
        long instrumentId = canonical.ensureInstrument(
                "NSE", "CASH", "EQUITY", "TCS", "Tata Consultancy Services", null, null);
        initializer.ensure();

        Integer mapped = jdbc.queryForObject(
                "SELECT count(*) FROM reference.instrument_sector_history h "
                        + "JOIN reference.sector s ON s.sector_id = h.sector_id "
                        + "WHERE h.instrument_id = ? AND s.code = 'IT'",
                Integer.class,
                instrumentId);
        assertThat(mapped).isEqualTo(1);

        // Re-running must not create a duplicate membership row.
        initializer.ensure();
        Integer stillOne = jdbc.queryForObject(
                "SELECT count(*) FROM reference.instrument_sector_history WHERE instrument_id = ?",
                Integer.class,
                instrumentId);
        assertThat(stillOne).isEqualTo(1);
    }

    @Test
    void doesNotSeedSectorsWithoutAVerifiedNseIndex() {
        Integer energy = jdbc.queryForObject(
                "SELECT count(*) FROM reference.sector WHERE code IN ('ENERGY', 'TELECOM')", Integer.class);
        assertThat(energy).isZero();
    }
}
