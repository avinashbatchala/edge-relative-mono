package com.edgerelative.application.corporateaction.persistence;

import com.edgerelative.application.corporateaction.domain.CorporateActionFactor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Persistence for corporate-action reference data and its versioned adjustment factors.
 */
@Repository
public class CorporateActionFactorRepository {

    /**
     * Action types whose factor semantics the analytical adjusted series supports today.
     */
    public static final List<String> SUPPORTED_TYPES = List.of("SPLIT", "BONUS");

    private final DSLContext dsl;

    public CorporateActionFactorRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long insertAction(
            long instrumentId,
            String actionType,
            LocalDate exDate,
            LocalDate recordDate,
            LocalDate effectiveDate,
            BigDecimal ratioNumerator,
            BigDecimal ratioDenominator,
            BigDecimal cashAmount,
            String currencyCode,
            String sourceReference) {
        Record record = dsl.fetchOne(
                "INSERT INTO reference.corporate_action "
                        + "(instrument_id, action_type, ex_date, record_date, effective_date, ratio_numerator, "
                        + "ratio_denominator, cash_amount, currency_code, source_reference) "
                        + "VALUES (?, ?, ?::date, ?::date, ?::date, ?, ?, ?, ?, ?) RETURNING corporate_action_id",
                instrumentId,
                actionType,
                exDate,
                recordDate,
                effectiveDate,
                ratioNumerator,
                ratioDenominator,
                cashAmount,
                currencyCode,
                sourceReference);
        return record.get("corporate_action_id", Long.class);
    }

    public long insertFactor(
            long corporateActionId,
            long instrumentId,
            BigDecimal priceFactor,
            BigDecimal quantityFactor,
            Instant availableAt,
            String definition) {
        Record record = dsl.fetchOne(
                "INSERT INTO reference.corporate_action_factor "
                        + "(corporate_action_id, instrument_id, price_factor, quantity_factor, definition, available_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::timestamptz) RETURNING corporate_action_factor_id",
                corporateActionId,
                instrumentId,
                priceFactor,
                quantityFactor,
                definition,
                utc(availableAt));
        return record.get("corporate_action_factor_id", Long.class);
    }

    /**
     * Factors that were known at {@code asOf} for the instrument, ascending by ex-date.
     */
    public List<CorporateActionFactor> listFactors(long instrumentId, Instant asOf) {
        return dsl.fetch(
                        "SELECT f.corporate_action_id, f.instrument_id, a.action_type, a.ex_date, f.price_factor, "
                                + "f.quantity_factor, f.available_at, f.definition "
                                + "FROM reference.corporate_action_factor f "
                                + "JOIN reference.corporate_action a USING (corporate_action_id) "
                                + "WHERE f.instrument_id = ? AND f.available_at <= ?::timestamptz "
                                + "ORDER BY a.ex_date, f.factor_version",
                        instrumentId,
                        utc(asOf))
                .map(CorporateActionFactorRepository::toFactor);
    }

    /**
     * Actions that affect a back-adjusted window starting at {@code fromDate} but cannot be applied:
     * an unsupported action type, or a supported type with no factor at all. A factor that simply was
     * not known yet at the requested as-of time is not an error — it is excluded from the read so it
     * cannot leak (DD-05 §260); only a genuine gap or unsupported type fails closed (DD-05 §43).
     * Returned as {@code TYPE@exDate} for a clear error message.
     */
    public List<String> unsupportedActions(long instrumentId, LocalDate fromDate) {
        return dsl.fetch(
                        "SELECT a.action_type, a.ex_date FROM reference.corporate_action a "
                                + "WHERE a.instrument_id = ? AND a.ex_date > ?::date "
                                + "AND (a.action_type NOT IN ('SPLIT', 'BONUS') "
                                + "  OR NOT EXISTS (SELECT 1 FROM reference.corporate_action_factor f "
                                + "     WHERE f.corporate_action_id = a.corporate_action_id)) "
                                + "ORDER BY a.ex_date",
                        instrumentId,
                        fromDate)
                .map(record -> record.get("action_type", String.class) + "@" + record.get("ex_date", LocalDate.class));
    }

    private static CorporateActionFactor toFactor(Record record) {
        return new CorporateActionFactor(
                record.get("corporate_action_id", Long.class),
                record.get("instrument_id", Long.class),
                record.get("action_type", String.class),
                record.get("ex_date", LocalDate.class),
                record.get("price_factor", BigDecimal.class),
                record.get("quantity_factor", BigDecimal.class),
                record.get("available_at", OffsetDateTime.class).toInstant(),
                record.get("definition", String.class));
    }

    private static String utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toString();
    }
}
