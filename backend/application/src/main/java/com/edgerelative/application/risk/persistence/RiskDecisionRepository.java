package com.edgerelative.application.risk.persistence;

import com.edgerelative.application.risk.domain.QuantityCap;
import com.edgerelative.application.risk.domain.RiskContext;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.risk.domain.RiskReasonCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only persistence for risk context snapshots, decisions, reasons, and candidate-linked
 * reservations, plus the mutable {@code risk_account_state} capacity ledger.
 *
 * <p>Idempotency is enforced by database uniqueness: {@code risk_context_key}, {@code decision_key},
 * and one reservation per decision. A retry of the same evaluation writes nothing new.
 */
@Repository
public class RiskDecisionRepository {

    private final DSLContext dsl;
    private final JsonMapper json;

    public RiskDecisionRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    public long ensureContextSnapshot(RiskContext ctx, long tenantId, long brokerAccountId, long policyVersionId) {
        UUID key = UUID.fromString(ctx.contextKey());
        dsl.execute(
                "INSERT INTO operational.risk_context_snapshot (risk_context_key, tenant_id, broker_account_id, "
                        + "trading_date, captured_at, risk_policy_version_id, risk_reference_equity, current_equity, "
                        + "available_cash, buying_power, margin_used, portfolio_open_risk, portfolio_stress_risk, "
                        + "gross_exposure, net_exposure, session_pnl, session_drawdown, risk_state, broker_health, "
                        + "data_health, reconciliation_state, limits_snapshot, capacities_snapshot) "
                        + "VALUES (?, ?, ?, ?, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb) ON CONFLICT (risk_context_key) DO NOTHING",
                key, tenantId, brokerAccountId, ctx.tradingDate(), utc(ctx.capturedAt()), policyVersionId,
                ctx.riskReferenceEquity(), ctx.currentNetLiquidationValue(), ctx.availableCash(), ctx.buyingPower(),
                ctx.marginUsed(), ctx.portfolioOpenRisk(), ctx.portfolioStressRisk(), ctx.grossExposure(),
                ctx.netExposure(), sessionPnl(ctx), ctx.sessionDrawdown(), ctx.riskState().name(),
                ctx.brokerHealth(), ctx.dataHealth(), ctx.reconciliationState(),
                json.writeValueAsString(Map.of()), json.writeValueAsString(Map.of()));
        return dsl.fetchOne(
                        "SELECT risk_context_snapshot_id FROM operational.risk_context_snapshot WHERE risk_context_key = ?",
                        key)
                .get("risk_context_snapshot_id", Long.class);
    }

    public long ensureDecision(
            RiskDecisionProposal proposal,
            long setupObservationId,
            long contextSnapshotId,
            long tenantId,
            long brokerAccountId,
            long strategyVersionId,
            long policyVersionId) {
        UUID key = UUID.fromString(proposal.decisionKey());
        long requestedQuantity = proposal.requestedQuantity() != null && proposal.requestedQuantity() > 0
                ? proposal.requestedQuantity()
                : Math.max(1, proposal.riskSizedQuantity());
        BigDecimal requestedRisk = proposal.effectiveLossPerUnit() == null
                ? BigDecimal.ZERO
                : scale(proposal.effectiveLossPerUnit().multiply(BigDecimal.valueOf(requestedQuantity)));
        BigDecimal approvedRisk = proposal.executionAdjustedRisk() == null ? BigDecimal.ZERO : scale(proposal.executionAdjustedRisk());
        dsl.execute(
                "INSERT INTO operational.risk_decision (decision_key, setup_observation_id, "
                        + "risk_context_snapshot_id, tenant_id, broker_account_id, strategy_version_id, "
                        + "risk_policy_version_id, instrument_id, decision_at, decision, requested_quantity, "
                        + "approved_quantity, requested_risk, approved_risk, approved_notional, "
                        + "effective_loss_per_unit, stress_loss_per_unit, binding_constraints) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) "
                        + "ON CONFLICT (decision_key) DO NOTHING",
                key, setupObservationId, contextSnapshotId, tenantId, brokerAccountId, strategyVersionId,
                policyVersionId, proposal.instrumentId(), utc(proposal.decidedAt()), proposal.decision().name(),
                requestedQuantity, proposal.approvedQuantity(), requestedRisk, approvedRisk,
                scale(proposal.approvedNotional()), positive(proposal.effectiveLossPerUnit()),
                positive(proposal.stressLossPerUnit()), json.writeValueAsString(binding(proposal)));
        return dsl.fetchOne(
                        "SELECT risk_decision_id FROM operational.risk_decision WHERE decision_key = ?", key)
                .get("risk_decision_id", Long.class);
    }

    public void insertReasons(long decisionId, RiskDecisionProposal proposal) {
        Long existing = dsl.fetchOne(
                        "SELECT count(*) AS c FROM operational.risk_decision_reason WHERE risk_decision_id = ?",
                        decisionId)
                .get("c", Long.class);
        if (existing != null && existing != 0) {
            return;
        }
        int ordinal = 1;
        for (RiskReasonCode code : proposal.reasonCodes()) {
            dsl.execute(
                    "INSERT INTO operational.risk_decision_reason (risk_decision_id, ordinal, reason_code, "
                            + "reason_type, before_value, after_value, details) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb) "
                            + "ON CONFLICT (risk_decision_id, ordinal) DO NOTHING",
                    decisionId, ordinal++, code.name(), code.type().name(),
                    proposal.availableTradeRiskBudget(), scale(proposal.executionAdjustedRisk()),
                    json.writeValueAsString(Map.of()));
        }
    }

    /** Candidate-linked reservation; one per decision. */
    public Optional<Long> ensureReservation(RiskDecisionProposal proposal, long decisionId, LocalDate tradingDate) {
        if (!proposal.decision().authorizesNewRisk() || proposal.approvedQuantity() < 1) {
            return Optional.empty();
        }
        BigDecimal reservedRisk = scale(proposal.executionAdjustedRisk());
        if (reservedRisk.signum() <= 0) {
            return Optional.empty();
        }
        UUID key = UUID.nameUUIDFromBytes(("risk-reservation:" + proposal.decisionKey()).getBytes(StandardCharsets.UTF_8));
        Record inserted = dsl.fetchOne(
                "INSERT INTO operational.risk_reservation (reservation_key, broker_account_id, trading_date, "
                        + "risk_decision_id, trade_plan_id, status, reserved_quantity, reserved_risk, reserved_notional) "
                        + "VALUES (?, ?, ?, ?, NULL, 'ACTIVE', ?, ?, ?) "
                        + "ON CONFLICT (risk_decision_id) DO NOTHING RETURNING risk_reservation_id",
                key, proposal.brokerAccountId(), tradingDate, decisionId, proposal.approvedQuantity(),
                reservedRisk, scale(proposal.approvedNotional()));
        // Only a freshly inserted reservation consumes capacity; a retry returns empty.
        return inserted == null ? Optional.empty() : Optional.of(inserted.get("risk_reservation_id", Long.class));
    }

    public Record findSetupObservation(long setupObservationId) {
        return dsl.fetchOne(
                "SELECT setup_observation_id, tenant_id, broker_account_id, strategy_version_id, instrument_id, "
                        + "setup_status, direction, structural_invalidation, sector_id "
                        + "FROM operational.setup_observation WHERE setup_observation_id = ?",
                setupObservationId);
    }

    public Record lockAccountState(long brokerAccountId, LocalDate tradingDate) {
        return dsl.fetchOne(
                "SELECT * FROM operational.risk_account_state WHERE broker_account_id = ? AND trading_date = ? "
                        + "FOR UPDATE",
                brokerAccountId, tradingDate);
    }

    public void createAccountState(RiskContext ctx, long brokerAccountId) {
        dsl.execute(
                "INSERT INTO operational.risk_account_state (broker_account_id, trading_date, "
                        + "risk_reference_equity, current_net_liquidation_value, reserved_risk, reserved_notional, "
                        + "open_risk, stress_open_risk, gross_exposure, net_exposure, realized_session_pnl, "
                        + "unrealized_pnl, session_drawdown, risk_state, state_version) "
                        + "VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, ?, 0) "
                        + "ON CONFLICT (broker_account_id, trading_date) DO NOTHING",
                brokerAccountId, ctx.tradingDate(), ctx.riskReferenceEquity(), ctx.currentNetLiquidationValue(),
                ctx.portfolioOpenRisk(), ctx.portfolioStressRisk(), ctx.grossExposure(), ctx.netExposure(),
                ctx.sessionRealizedLoss(), ctx.currentNetLiquidationValue(), ctx.sessionDrawdown(),
                ctx.riskState().name());
    }

    public void reserveCapacity(long brokerAccountId, LocalDate tradingDate, BigDecimal risk, BigDecimal notional) {
        dsl.execute(
                "UPDATE operational.risk_account_state SET reserved_risk = reserved_risk + ?, "
                        + "reserved_notional = reserved_notional + ?, state_version = state_version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE broker_account_id = ? AND trading_date = ?",
                scale(risk), scale(notional), brokerAccountId, tradingDate);
    }

    /** Idempotent release: only an active reservation is released, and capacity is returned once. */
    public boolean releaseReservation(long brokerAccountId, long decisionId, String reason) {
        Record reservation = dsl.fetchOne(
                "SELECT risk_reservation_id, reserved_risk, reserved_notional, status "
                        + "FROM operational.risk_reservation WHERE risk_decision_id = ? FOR UPDATE",
                decisionId);
        if (reservation == null) {
            return false;
        }
        // Mark released first; only the transition releases capacity, so retries are idempotent.
        int updated = dsl.execute(
                "UPDATE operational.risk_reservation SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP, "
                        + "release_reason = ? WHERE risk_reservation_id = ? AND status <> 'RELEASED'",
                reason, reservation.get("risk_reservation_id", Long.class));
        if (updated <= 0) {
            return false;
        }
        dsl.execute(
                "UPDATE operational.risk_account_state SET reserved_risk = greatest(0, reserved_risk - ?), "
                        + "reserved_notional = greatest(0, reserved_notional - ?), state_version = state_version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE broker_account_id = ? AND trading_date = "
                        + "(SELECT trading_date FROM operational.risk_reservation WHERE risk_reservation_id = ?)",
                reservation.get("reserved_risk", BigDecimal.class), reservation.get("reserved_notional", BigDecimal.class),
                brokerAccountId, reservation.get("risk_reservation_id", Long.class));
        return true;
    }

    public List<Record> findReasons(long decisionId) {
        return dsl.fetch(
                "SELECT ordinal, reason_code, reason_type FROM operational.risk_decision_reason "
                        + "WHERE risk_decision_id = ? ORDER BY ordinal",
                decisionId);
    }

    public Record findDecision(String decisionKey) {
        return dsl.fetchOne(
                "SELECT risk_decision_id, decision, requested_quantity, approved_quantity, approved_risk, approved_notional "
                        + "FROM operational.risk_decision WHERE decision_key = ?",
                UUID.fromString(decisionKey));
    }

    private static List<Map<String, Object>> binding(RiskDecisionProposal proposal) {
        return proposal.quantityCaps().stream()
                .filter(QuantityCap::binding)
                .map(cap -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", cap.name());
                    entry.put("quantity", cap.quantity());
                    entry.put("reason", cap.reasonCode() == null ? null : cap.reasonCode().name());
                    return entry;
                })
                .toList();
    }

    private static BigDecimal sessionPnl(RiskContext ctx) {
        if (ctx.currentNetLiquidationValue() == null || ctx.riskReferenceEquity() == null) {
            return BigDecimal.ZERO;
        }
        return scale(ctx.currentNetLiquidationValue().subtract(ctx.riskReferenceEquity()));
    }

    private static BigDecimal positive(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(8, java.math.RoundingMode.HALF_UP);
    }

    private static String utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toString();
    }

    static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
