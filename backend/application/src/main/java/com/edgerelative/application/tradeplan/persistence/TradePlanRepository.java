package com.edgerelative.application.tradeplan.persistence;

import com.edgerelative.application.tradeplan.application.TradePlanRow;
import com.edgerelative.application.tradeplan.domain.TradePlan;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Immutable persistence for trade plans and their append-only event stream. Creation is idempotent on
 * {@code risk_decision_id} (one plan per approved decision), so repeated delivery returns the existing
 * plan and never duplicates intent.
 */
@Repository
public class TradePlanRepository {

    private static final String SELECT_COLUMNS =
            "SELECT tp.trade_plan_key, tp.risk_decision_id, rd.decision_key, rd.decision, rd.decision_at, "
                    + "rd.approved_quantity, rd.approved_risk, rd.approved_notional, tp.setup_observation_id, "
                    + "tp.setup_instance_id, tp.tenant_id, tp.broker_account_id, tp.instrument_id, "
                    + "i.canonical_symbol, i.display_name, tp.direction, tp.entry_pattern, tp.entry_method, "
                    + "tp.target_method, tp.planned_quantity, tp.entry_low, tp.entry_high, "
                    + "tp.structural_invalidation, tp.protective_stop, tp.target_reference, tp.expected_reward_risk, "
                    + "tp.planned_risk, tp.planned_notional, tp.expected_cost, tp.expected_slippage, "
                    + "tp.invalidation_reason, tp.correlation_id, tp.created_at, tp.valid_from, tp.expires_at, "
                    + "tp.entry_cutoff_at, tp.entry_trigger_price, tp.no_chase_price, tp.no_chase_basis, "
                    + "tp.stop_buffer_method, tp.target_rationale, tp.explanation, tp.feature_schema_version, "
                    + "tp.plan_policy_reference, tp.market_regime, tp.sector_code, tp.tick_size, "
                    + "tp.quantity_increment, s.code AS strategy_id, sv.version AS strategy_version "
                    + "FROM operational.trade_plan tp "
                    + "JOIN operational.risk_decision rd ON rd.risk_decision_id = tp.risk_decision_id "
                    + "JOIN control.strategy_version sv ON sv.strategy_version_id = tp.strategy_version_id "
                    + "JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
                    + "JOIN reference.instrument i ON i.instrument_id = tp.instrument_id ";

    private final DSLContext dsl;
    private final JsonMapper json;

    public TradePlanRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    /** Idempotent insert; returns the trade_plan_id (existing or newly created). */
    public long ensurePlan(TradePlan plan) {
        Record inserted = dsl.fetchOne(
                "INSERT INTO operational.trade_plan (trade_plan_key, risk_decision_id, setup_observation_id, "
                        + "tenant_id, broker_account_id, strategy_version_id, instrument_id, market_observation_id, "
                        + "direction, entry_pattern, entry_method, target_method, planned_quantity, entry_low, "
                        + "entry_high, structural_invalidation, protective_stop, target_reference, "
                        + "expected_reward_risk, planned_risk, planned_notional, expected_cost, expected_slippage, "
                        + "invalidation_reason, correlation_id, valid_from, expires_at, entry_cutoff_at, "
                        + "entry_trigger_price, no_chase_price, no_chase_basis, stop_buffer_method, target_rationale, "
                        + "explanation, feature_schema_version, plan_policy_reference, market_regime, sector_code, "
                        + "tick_size, quantity_increment) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::timestamptz, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (risk_decision_id) DO NOTHING RETURNING trade_plan_id",
                UUID.fromString(plan.planKey()), plan.riskDecisionId(), plan.setupObservationId(),
                plan.tenantId(), plan.brokerAccountId(), plan.strategyVersionId(), plan.instrumentId(),
                plan.marketObservationId(), plan.direction().name(), plan.entryPattern(), plan.entryMethod(),
                plan.targetMethod(), plan.plannedQuantity(), plan.entryLow(), plan.entryHigh(),
                plan.structuralInvalidation(), plan.protectiveStop(), plan.targetReference(),
                plan.expectedRewardRisk(), plan.plannedRisk(), plan.plannedNotional(), plan.expectedCost(),
                plan.expectedSlippage(), plan.invalidationReason(), plan.correlationId(), utc(plan.validFrom()),
                utc(plan.expiresAt()), utc(plan.entryCutoffAt()), plan.entryTriggerPrice(), plan.noChasePrice(),
                plan.noChaseBasis(), plan.stopBufferMethod(), plan.targetRationale(), plan.explanation(),
                plan.featureSchemaVersion(), plan.policyReference(), plan.marketRegime(), plan.sectorCode(),
                plan.tickSize(), plan.quantityIncrement());
        if (inserted != null) {
            return inserted.get("trade_plan_id", Long.class);
        }
        return dsl.fetchOne(
                        "SELECT trade_plan_id FROM operational.trade_plan WHERE risk_decision_id = ?",
                        plan.riskDecisionId())
                .get("trade_plan_id", Long.class);
    }

    /** Append-only lifecycle event; idempotent on the deterministic event key. */
    public void recordEvent(long tradePlanId, String planKey, String eventType, Instant occurredAt, String reason) {
        UUID key = UUID.nameUUIDFromBytes(
                ("trade-plan-event:" + eventType + ":" + planKey).getBytes(StandardCharsets.UTF_8));
        dsl.execute(
                "INSERT INTO operational.trade_plan_event (event_key, trade_plan_id, event_type, occurred_at, "
                        + "reason, payload) VALUES (?, ?, ?, ?::timestamptz, ?, ?::jsonb) "
                        + "ON CONFLICT (event_key) DO NOTHING",
                key, tradePlanId, eventType, utc(occurredAt), reason, json.writeValueAsString(java.util.Map.of()));
    }

    public Optional<TradePlanRow> findByKey(String planKey) {
        Record record = dsl.fetchOne(SELECT_COLUMNS + "WHERE tp.trade_plan_key = ?", UUID.fromString(planKey));
        return Optional.ofNullable(record).map(this::map);
    }

    public Optional<TradePlanRow> findByDecision(String decisionKey) {
        Record record = dsl.fetchOne(SELECT_COLUMNS + "WHERE rd.decision_key = ?", UUID.fromString(decisionKey));
        return Optional.ofNullable(record).map(this::map);
    }

    public List<TradePlanRow> findBySetup(long setupObservationId) {
        return dsl.fetch(SELECT_COLUMNS + "WHERE tp.setup_observation_id = ? ORDER BY tp.created_at DESC",
                        setupObservationId)
                .map(this::map);
    }

    public Optional<TradePlanRow> findLatestForInstrument(long instrumentId) {
        Record record = dsl.fetchOne(
                SELECT_COLUMNS + "WHERE tp.instrument_id = ? ORDER BY tp.created_at DESC LIMIT 1", instrumentId);
        return Optional.ofNullable(record).map(this::map);
    }

    /** Setup + instrument + sector lineage needed to build a plan from an approved decision. */
    public Optional<Record> findSetupLineage(long setupObservationId) {
        return Optional.ofNullable(dsl.fetchOne(
                "SELECT so.setup_observation_id, so.setup_instance_id, so.entry_pattern, so.market_observation_id, "
                        + "so.direction, so.observed_at, so.explanation, so.sector_id, so.strategy_version_id, "
                        + "i.canonical_symbol, i.tick_size, i.lot_size, s.code AS sector_code, "
                        + "st.code AS strategy_id, sv.version AS strategy_version_number "
                        + "FROM operational.setup_observation so "
                        + "JOIN reference.instrument i ON i.instrument_id = so.instrument_id "
                        + "LEFT JOIN reference.sector s ON s.sector_id = so.sector_id "
                        + "JOIN control.strategy_version sv ON sv.strategy_version_id = so.strategy_version_id "
                        + "JOIN control.strategy st ON st.strategy_id = sv.strategy_id "
                        + "WHERE so.setup_observation_id = ?",
                setupObservationId));
    }

    public List<String> reasonCodes(long riskDecisionId) {
        return dsl.fetch(
                        "SELECT reason_code FROM operational.risk_decision_reason WHERE risk_decision_id = ? "
                                + "ORDER BY ordinal",
                        riskDecisionId)
                .map(record -> record.get("reason_code", String.class));
    }

    public boolean existsByDecision(String decisionKey) {
        return findByDecision(decisionKey).isPresent();
    }

    private TradePlanRow map(Record r) {
        long riskDecisionId = r.get("risk_decision_id", Long.class);
        Long increment = r.get("quantity_increment", Long.class);
        return new TradePlanRow(
                r.get("trade_plan_key", UUID.class).toString(),
                riskDecisionId,
                r.get("decision_key", UUID.class).toString(),
                r.get("decision", String.class),
                instant(r.get("decision_at", OffsetDateTime.class)),
                orZero(r.get("approved_quantity", Long.class)),
                r.get("approved_risk", BigDecimal.class),
                r.get("approved_notional", BigDecimal.class),
                r.get("setup_observation_id", Long.class),
                r.get("setup_instance_id", UUID.class) == null ? null : r.get("setup_instance_id", UUID.class).toString(),
                r.get("tenant_id", Long.class),
                r.get("broker_account_id", Long.class),
                r.get("instrument_id", Long.class),
                r.get("canonical_symbol", String.class),
                r.get("display_name", String.class),
                r.get("direction", String.class),
                r.get("entry_pattern", String.class),
                r.get("entry_method", String.class),
                r.get("target_method", String.class),
                r.get("planned_quantity", Long.class),
                r.get("entry_low", BigDecimal.class),
                r.get("entry_high", BigDecimal.class),
                r.get("structural_invalidation", BigDecimal.class),
                r.get("protective_stop", BigDecimal.class),
                r.get("target_reference", BigDecimal.class),
                r.get("expected_reward_risk", BigDecimal.class),
                r.get("planned_risk", BigDecimal.class),
                r.get("planned_notional", BigDecimal.class),
                r.get("expected_cost", BigDecimal.class),
                r.get("expected_slippage", BigDecimal.class),
                r.get("invalidation_reason", String.class),
                r.get("correlation_id", UUID.class),
                instant(r.get("created_at", OffsetDateTime.class)),
                instant(r.get("valid_from", OffsetDateTime.class)),
                instant(r.get("expires_at", OffsetDateTime.class)),
                instant(r.get("entry_cutoff_at", OffsetDateTime.class)),
                r.get("entry_trigger_price", BigDecimal.class),
                r.get("no_chase_price", BigDecimal.class),
                r.get("no_chase_basis", String.class),
                r.get("stop_buffer_method", String.class),
                r.get("target_rationale", String.class),
                r.get("explanation", String.class),
                r.get("feature_schema_version", String.class),
                r.get("plan_policy_reference", String.class),
                r.get("market_regime", String.class),
                r.get("sector_code", String.class),
                r.get("tick_size", BigDecimal.class),
                increment,
                r.get("strategy_id", String.class),
                "v" + r.get("strategy_version", Integer.class),
                reasonCodes(riskDecisionId));
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toString();
    }
}
