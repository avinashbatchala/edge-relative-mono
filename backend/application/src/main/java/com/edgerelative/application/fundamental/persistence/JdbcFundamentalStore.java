package com.edgerelative.application.fundamental.persistence;

import com.edgerelative.application.fundamental.domain.InstrumentRef;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

/**
 * jOOQ persistence for fundamentals. Inserts are idempotent on a natural key and append-only; the
 * database triggers reject updates and deletes so a published fact can never be rewritten.
 */
@Repository
public class JdbcFundamentalStore implements FundamentalStore {

    private static final String SOURCE_LICENSE = "Free-first source; verify redistribution rights before sharing.";

    private final DSLContext dsl;

    public JdbcFundamentalStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<InstrumentRef> resolveInstrument(long instrumentId) {
        Record record = dsl.fetchOne(
                "SELECT e.code AS exchange, i.canonical_symbol AS symbol "
                        + "FROM reference.instrument i "
                        + "JOIN reference.exchange e ON e.exchange_id = i.exchange_id "
                        + "WHERE i.instrument_id = ?",
                instrumentId);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(new InstrumentRef(record.get("exchange", String.class), record.get("symbol", String.class)));
    }

    @Override
    public void save(long instrumentId, FundamentalSnapshot snapshot) {
        long dataSourceId = ensureDataSource(snapshot.provider());
        long filingId = ensureFiling(instrumentId, dataSourceId, snapshot);
        long periodId = ensurePeriod(filingId, snapshot.period());
        insertStatements(periodId, snapshot.statements());
        insertMetrics(periodId, snapshot.metrics());
    }

    private long ensureDataSource(String provider) {
        Record record = dsl.fetchOne(
                "INSERT INTO fundamental.data_source (code, provider, license) VALUES (?, ?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET provider = EXCLUDED.provider "
                        + "RETURNING data_source_id",
                provider,
                provider,
                SOURCE_LICENSE);
        return record.get("data_source_id", Long.class);
    }

    private long ensureFiling(long instrumentId, long dataSourceId, FundamentalSnapshot snapshot) {
        Filing filing = snapshot.filing();
        Record inserted = dsl.fetchOne(
                "INSERT INTO fundamental.filing "
                        + "(instrument_id, data_source_id, filed_at, observed_at, document_reference, revision) "
                        + "VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?, ?) "
                        + "ON CONFLICT (instrument_id, data_source_id, filed_at, revision) DO NOTHING "
                        + "RETURNING filing_id",
                instrumentId,
                dataSourceId,
                offset(filing.filedAt()),
                offset(snapshot.asOf()),
                filing.documentReference(),
                filing.revision());
        if (inserted != null) {
            return inserted.get("filing_id", Long.class);
        }
        Record existing = dsl.fetchOne(
                "SELECT filing_id FROM fundamental.filing "
                        + "WHERE instrument_id = ? AND data_source_id = ? AND filed_at = ?::timestamptz AND revision = ?",
                instrumentId,
                dataSourceId,
                offset(filing.filedAt()),
                filing.revision());
        return existing.get("filing_id", Long.class);
    }

    private long ensurePeriod(long filingId, FinancialPeriod period) {
        Record inserted = dsl.fetchOne(
                "INSERT INTO fundamental.reporting_period "
                        + "(filing_id, fiscal_year, fiscal_quarter, period_type, reporting_basis, period_end) "
                        + "VALUES (?, ?, ?, ?, ?, ?::date) "
                        + "ON CONFLICT (filing_id, fiscal_year, fiscal_quarter, period_type, reporting_basis) "
                        + "DO NOTHING RETURNING reporting_period_id",
                filingId,
                period.fiscalYear(),
                period.fiscalQuarter(),
                period.type().name(),
                period.basis().name(),
                period.periodEnd());
        if (inserted != null) {
            return inserted.get("reporting_period_id", Long.class);
        }
        Record existing = dsl.fetchOne(
                "SELECT reporting_period_id FROM fundamental.reporting_period "
                        + "WHERE filing_id = ? AND fiscal_year = ? AND fiscal_quarter = ? "
                        + "AND period_type = ? AND reporting_basis = ?",
                filingId,
                period.fiscalYear(),
                period.fiscalQuarter(),
                period.type().name(),
                period.basis().name());
        return existing.get("reporting_period_id", Long.class);
    }

    private void insertStatements(long periodId, List<StatementLine> statements) {
        int ordinal = 1;
        for (StatementLine line : statements) {
            dsl.execute(
                    "INSERT INTO fundamental.statement_line "
                            + "(reporting_period_id, line_code, label, value, unit, scale, ordinal) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (reporting_period_id, line_code) DO NOTHING",
                    periodId,
                    line.lineCode(),
                    line.label(),
                    line.value(),
                    line.unit(),
                    line.scale(),
                    ordinal++);
        }
    }

    private void insertMetrics(long periodId, List<FundamentalMetric> metrics) {
        for (FundamentalMetric metric : metrics) {
            dsl.execute(
                    "INSERT INTO fundamental.metric_value "
                            + "(reporting_period_id, metric_code, value, unit, decimals) "
                            + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (reporting_period_id, metric_code) DO NOTHING",
                    periodId,
                    metric.metricCode(),
                    metric.value(),
                    metric.unit(),
                    metric.decimals());
        }
    }

    @Override
    public Optional<FundamentalSnapshot> findLatest(long instrumentId, Instant asOf) {
        Record header = dsl.fetchOne(
                "SELECT rp.reporting_period_id, rp.fiscal_year, rp.fiscal_quarter, rp.period_type, "
                        + "rp.reporting_basis, rp.period_end, "
                        + "f.filed_at, f.document_reference, f.revision, ds.provider, "
                        + "e.code AS exchange, i.canonical_symbol AS symbol "
                        + "FROM fundamental.reporting_period rp "
                        + "JOIN fundamental.filing f ON f.filing_id = rp.filing_id "
                        + "JOIN fundamental.data_source ds ON ds.data_source_id = f.data_source_id "
                        + "JOIN reference.instrument i ON i.instrument_id = f.instrument_id "
                        + "JOIN reference.exchange e ON e.exchange_id = i.exchange_id "
                        + "WHERE f.instrument_id = ? AND f.filed_at <= ?::timestamptz "
                        + "ORDER BY rp.period_end DESC, f.filed_at DESC LIMIT 1",
                instrumentId,
                offset(asOf));
        if (header == null) {
            return Optional.empty();
        }
        long periodId = header.get("reporting_period_id", Long.class);

        FinancialPeriod period = new FinancialPeriod(
                header.get("fiscal_year", String.class),
                header.get("fiscal_quarter", Integer.class),
                PeriodType.valueOf(header.get("period_type", String.class)),
                ReportingBasis.valueOf(header.get("reporting_basis", String.class)),
                header.get("period_end", LocalDate.class));
        Filing filing = new Filing(
                toInstant(header.get("filed_at", OffsetDateTime.class)),
                header.get("provider", String.class),
                header.get("document_reference", String.class),
                header.get("revision", String.class));
        return Optional.of(new FundamentalSnapshot(
                header.get("exchange", String.class),
                header.get("symbol", String.class),
                header.get("provider", String.class),
                header.get("revision", String.class),
                asOf,
                filing,
                period,
                loadStatements(periodId),
                loadMetrics(periodId)));
    }

    private List<StatementLine> loadStatements(long periodId) {
        Result<Record> rows = dsl.fetch(
                "SELECT line_code, label, value, unit, scale FROM fundamental.statement_line "
                        + "WHERE reporting_period_id = ? ORDER BY ordinal",
                periodId);
        List<StatementLine> lines = new ArrayList<>();
        for (Record row : rows) {
            lines.add(new StatementLine(
                    row.get("line_code", String.class),
                    row.get("label", String.class),
                    row.get("value", BigDecimal.class),
                    row.get("unit", String.class),
                    row.get("scale", String.class)));
        }
        return lines;
    }

    private List<FundamentalMetric> loadMetrics(long periodId) {
        Result<Record> rows = dsl.fetch(
                "SELECT metric_code, value, unit, decimals FROM fundamental.metric_value "
                        + "WHERE reporting_period_id = ? ORDER BY metric_code",
                periodId);
        List<FundamentalMetric> metrics = new ArrayList<>();
        for (Record row : rows) {
            metrics.add(new FundamentalMetric(
                    row.get("metric_code", String.class),
                    row.get("value", BigDecimal.class),
                    row.get("unit", String.class),
                    row.get("decimals", Integer.class)));
        }
        return metrics;
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value.toInstant();
    }
}
