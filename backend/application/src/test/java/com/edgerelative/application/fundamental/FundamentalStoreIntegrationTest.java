package com.edgerelative.application.fundamental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.application.fundamental.persistence.FundamentalStore;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.fundamentals.api.model.Filing;
import com.edgerelative.fundamentals.api.model.FinancialPeriod;
import com.edgerelative.fundamentals.api.model.FundamentalMetric;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;
import com.edgerelative.fundamentals.api.model.PeriodType;
import com.edgerelative.fundamentals.api.model.ReportingBasis;
import com.edgerelative.fundamentals.api.model.StatementLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Point-in-time fundamentals store: a value is only visible from its filing timestamp, writes are
 * idempotent, and published facts cannot be rewritten (DD-06, ADR-005).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FundamentalStoreIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("fundamental_store")
            .withUsername("fundamental_store")
            .withPassword("fundamental_store");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    private FundamentalStore store;

    @Autowired
    private CanonicalInstrumentService instruments;

    @Autowired
    private DSLContext dsl;

    private static FundamentalSnapshot snapshot(String symbol, Instant filedAt) {
        return new FundamentalSnapshot(
                "NSE",
                symbol,
                "test-provider",
                "rev-1",
                filedAt,
                new Filing(filedAt, "Test", "doc-1", "rev-1"),
                new FinancialPeriod(
                        "FY2025", 0, PeriodType.ANNUAL, ReportingBasis.CONSOLIDATED, LocalDate.of(2025, 3, 31)),
                List.of(new StatementLine(
                        "total_revenue", "Total revenue", new BigDecimal("9000000000000"), "INR", "unit")),
                List.of(new FundamentalMetric("trailing_pe", new BigDecimal("25.0"), "ratio", null)));
    }

    private long instrumentId(String symbol) {
        return instruments.ensureInstrument(
                "NSE", "EQ", "EQUITY", symbol, symbol, new BigDecimal("0.05"), 1L);
    }

    @Test
    void valueIsVisibleOnlyFromItsFilingTimestamp() {
        long instrumentId = instrumentId("RELIANCE");
        Instant filedAt = Instant.parse("2026-04-01T00:00:00Z");
        store.save(instrumentId, snapshot("RELIANCE", filedAt));

        Optional<FundamentalSnapshot> before = store.findLatest(instrumentId, filedAt.minusSeconds(1));
        assertThat(before).isEmpty();

        Optional<FundamentalSnapshot> after = store.findLatest(instrumentId, filedAt.plusSeconds(3600));
        assertThat(after).isPresent();
        assertThat(after.get().period().fiscalYear()).isEqualTo("FY2025");
        assertThat(after.get().filing().filedAt()).isEqualTo(filedAt);
        assertThat(after.get().statements())
                .extracting(StatementLine::lineCode)
                .containsExactly("total_revenue");
        assertThat(after.get().metrics())
                .extracting(FundamentalMetric::metricCode)
                .containsExactly("trailing_pe");
    }

    @Test
    void repeatedSaveIsIdempotent() {
        long instrumentId = instrumentId("TCS");
        Instant filedAt = Instant.parse("2026-05-01T00:00:00Z");
        store.save(instrumentId, snapshot("TCS", filedAt));
        store.save(instrumentId, snapshot("TCS", filedAt));

        assertThat(scopedCount("fundamental.reporting_period", instrumentId)).isEqualTo(1);
        assertThat(scopedCount("fundamental.metric_value", instrumentId)).isEqualTo(1);
        assertThat(scopedCount("fundamental.statement_line", instrumentId)).isEqualTo(1);
    }

    @Test
    void publishedFactsCannotBeRewritten() {
        long instrumentId = instrumentId("INFY");
        store.save(instrumentId, snapshot("INFY", Instant.parse("2026-06-01T00:00:00Z")));

        assertThatThrownBy(() -> dsl.execute("UPDATE fundamental.metric_value SET value = 0"))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> dsl.execute("DELETE FROM fundamental.filing"))
                .hasMessageContaining("immutable");
    }

    private int scopedCount(String table, long instrumentId) {
        return dsl.fetch(
                        "SELECT 1 FROM " + table + " t "
                                + "JOIN fundamental.reporting_period rp ON rp.reporting_period_id = t.reporting_period_id "
                                + "JOIN fundamental.filing f ON f.filing_id = rp.filing_id "
                                + "WHERE f.instrument_id = ?",
                        instrumentId)
                .size();
    }
}
