package com.edgerelative.application.strategy.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Effective-dated per-instrument strategy parameter bindings (DD-01 §51, DD-04 §86). Resolution is
 * point-in-time: a decision at date {@code d} sees the binding whose {@code [effective_from,
 * effective_to)} contains {@code d}. Rows are append-only; the database exclusion constraint forbids
 * overlapping periods for one instrument.
 */
@Repository
public class StrategyBindingRepository {

    private final DSLContext dsl;

    public StrategyBindingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record Binding(
            String parametersJson,
            long strategyVersionId,
            String lifecycleState,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public Optional<Binding> findEffective(long instrumentId, LocalDate asOf) {
        Record record = dsl.fetchOne(
                "SELECT parameters::text AS parameters, strategy_version_id, lifecycle_state, "
                        + "effective_from, effective_to FROM control.strategy_instrument_binding "
                        + "WHERE instrument_id = ? AND effective_from <= ? "
                        + "AND (effective_to IS NULL OR effective_to > ?) AND lifecycle_state <> 'RETIRED' "
                        + "ORDER BY effective_from DESC LIMIT 1",
                instrumentId, asOf, asOf);
        return record == null ? Optional.empty() : Optional.of(map(record));
    }

    public List<Binding> listForInstrument(long instrumentId) {
        return dsl.fetch(
                        "SELECT parameters::text AS parameters, strategy_version_id, lifecycle_state, "
                                + "effective_from, effective_to FROM control.strategy_instrument_binding "
                                + "WHERE instrument_id = ? ORDER BY effective_from DESC",
                        instrumentId)
                .map(StrategyBindingRepository::map);
    }

    public boolean hasBindings() {
        return dsl.fetchOne("SELECT 1 FROM control.strategy_instrument_binding LIMIT 1") != null;
    }

    public long insert(
            long instrumentId,
            long strategyVersionId,
            String parametersJson,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String lifecycleState,
            String source) {
        return dsl.fetchOne(
                        "INSERT INTO control.strategy_instrument_binding (instrument_id, strategy_version_id, "
                                + "parameters, effective_from, effective_to, lifecycle_state, source, code_version) "
                                + "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?) RETURNING binding_id",
                        instrumentId, strategyVersionId, parametersJson, effectiveFrom, effectiveTo, lifecycleState,
                        source, "er-strategy-binding-v1")
                .get("binding_id", Long.class);
    }

    private static Binding map(Record record) {
        return new Binding(
                record.get("parameters", String.class),
                record.get("strategy_version_id", Long.class),
                record.get("lifecycle_state", String.class),
                record.get("effective_from", LocalDate.class),
                record.get("effective_to", LocalDate.class));
    }
}
