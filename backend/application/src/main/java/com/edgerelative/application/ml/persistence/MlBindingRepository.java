package com.edgerelative.application.ml.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Effective-dated per-instrument ML model bindings. Resolution is point-in-time: a decision at date
 * {@code d} sees the binding whose {@code [effective_from, effective_to)} contains {@code d}. Rows are
 * append-only; the database exclusion constraint forbids overlapping periods for one instrument.
 */
@Repository
public class MlBindingRepository {

    private final DSLContext dsl;

    public MlBindingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record Binding(
            long modelVersionId,
            String authorityLevel,
            String lifecycleState,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public Optional<Binding> findEffective(long instrumentId, LocalDate asOf) {
        Record record = dsl.fetchOne(
                "SELECT model_version_id, authority_level, lifecycle_state, effective_from, effective_to "
                        + "FROM control.ml_model_binding WHERE instrument_id = ? AND effective_from <= ? "
                        + "AND (effective_to IS NULL OR effective_to > ?) AND lifecycle_state <> 'RETIRED' "
                        + "ORDER BY effective_from DESC LIMIT 1",
                instrumentId, asOf, asOf);
        return record == null ? Optional.empty() : Optional.of(map(record));
    }

    public List<Binding> listForInstrument(long instrumentId) {
        return dsl.fetch(
                        "SELECT model_version_id, authority_level, lifecycle_state, effective_from, effective_to "
                                + "FROM control.ml_model_binding WHERE instrument_id = ? "
                                + "ORDER BY effective_from DESC",
                        instrumentId)
                .map(MlBindingRepository::map);
    }

    public long insert(
            long instrumentId,
            long modelVersionId,
            String authorityLevel,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String lifecycleState,
            String source) {
        return dsl.fetchOne(
                        "INSERT INTO control.ml_model_binding (instrument_id, model_version_id, authority_level, "
                                + "effective_from, effective_to, lifecycle_state, source, code_version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING binding_id",
                        instrumentId, modelVersionId, authorityLevel, effectiveFrom, effectiveTo,
                        lifecycleState, source, "er-ml-binding-v1")
                .get("binding_id", Long.class);
    }

    private static Binding map(Record record) {
        return new Binding(
                record.get("model_version_id", Long.class),
                record.get("authority_level", String.class),
                record.get("lifecycle_state", String.class),
                record.get("effective_from", LocalDate.class),
                record.get("effective_to", LocalDate.class));
    }
}
