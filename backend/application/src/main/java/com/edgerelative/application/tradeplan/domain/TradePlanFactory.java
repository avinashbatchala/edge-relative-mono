package com.edgerelative.application.tradeplan.domain;

import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Pure, deterministic construction of an immutable trade plan from an approved risk proposal. It does
 * not size: quantity/risk/notional come from the risk decision. It does not invent a target price or
 * a universal TTL; unconfigured policy is represented as null with an explicit methodology.
 */
public final class TradePlanFactory {

    private static final String UNRESOLVED_TARGET = "STRUCTURAL_UNRESOLVED";
    private static final String UNRESOLVED_STOP_BUFFER = "POLICY_UNCONFIGURED";

    private TradePlanFactory() {
    }

    public static String planKeyFor(String decisionKey) {
        return UUID.nameUUIDFromBytes(("trade-plan:" + decisionKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static TradePlan create(
            RiskDecisionProposal proposal,
            long riskDecisionId,
            PlanLineage lineage,
            TradePlanPolicy policy,
            Instant createdAt,
            Instant sessionClose) {
        if (proposal == null || !proposal.decision().authorizesNewRisk()) {
            throw new TradePlanException(
                    TradePlanException.INELIGIBLE_DECISION,
                    "only APPROVE or REDUCE decisions may create a trade plan");
        }
        if (proposal.approvedQuantity() < 1 || proposal.entryPrice() == null || proposal.protectiveStop() == null
                || proposal.structuralInvalidation() == null) {
            throw new TradePlanException(
                    TradePlanException.PLAN_INPUTS_UNAVAILABLE,
                    "approved quantity, entry, protective stop, and invalidation are required");
        }
        validateGeometry(proposal, lineage);
        if (proposal.executionAdjustedRisk().signum() <= 0 || proposal.approvedNotional().signum() <= 0) {
            throw new TradePlanException(
                    TradePlanException.CEILING_EXCEEDED,
                    "approved risk and notional must be positive");
        }

        String planKey = planKeyFor(proposal.decisionKey());
        Instant validFrom = createdAt;
        Instant expiresAt = policy.validityMinutes() == null
                ? null
                : validFrom.plusSeconds(60L * policy.validityMinutes());
        Instant entryCutoffAt = policy.entryCutoffMinutesBeforeClose() == null || sessionClose == null
                ? null
                : sessionClose.minusSeconds(60L * policy.entryCutoffMinutesBeforeClose());

        BigDecimal trigger = lineage.triggerLevel();
        BigDecimal noChasePrice = noChasePrice(policy, lineage, trigger);
        String noChaseBasis = noChasePrice == null
                ? "No no-chase policy configured"
                : "Trigger +/- configured no-chase ticks (DD02 section 80)";

        String targetMethod = policy.targetMethod() == null ? UNRESOLVED_TARGET : policy.targetMethod();
        BigDecimal targetReference = null;
        String targetRationale =
                "No structural target producer is wired; reward/risk is not asserted for this plan.";

        String invalidationReason = lineage.invalidationType();
        String invalidationBasis = lineage.invalidationBasis();

        return new TradePlan(
                planKey,
                proposal.decisionKey(),
                riskDecisionId,
                lineage.setupObservationId(),
                lineage.setupInstanceId(),
                lineage.setupFamily(),
                lineage.tenantId(),
                lineage.brokerAccountId(),
                lineage.instrumentId(),
                lineage.symbol(),
                lineage.direction(),
                lineage.strategyId(),
                lineage.strategyVersion(),
                lineage.strategyVersionId(),
                lineage.marketObservationId(),
                lineage.featureSchemaVersion(),
                lineage.marketObservationLineage(),
                createdAt,
                proposal.decidedAt(),
                lineage.observedAt(),
                validFrom,
                expiresAt,
                entryCutoffAt,
                policy.entryMethod() == null ? "REFERENCE_PRICE" : policy.entryMethod(),
                lineage.setupFamily(),
                proposal.entryPrice(),
                proposal.entryPrice(),
                proposal.entryPrice(),
                trigger,
                noChasePrice,
                noChaseBasis,
                proposal.structuralInvalidation(),
                invalidationReason,
                invalidationBasis,
                proposal.protectiveStop(),
                policy.stopBufferMethod() == null ? UNRESOLVED_STOP_BUFFER : policy.stopBufferMethod(),
                targetMethod,
                targetReference,
                targetRationale,
                null,
                proposal.approvedQuantity(),
                proposal.approvedQuantity(),
                lineage.quantityIncrement(),
                lineage.tickSize(),
                proposal.executionAdjustedRisk(),
                proposal.executionAdjustedRisk(),
                proposal.approvedNotional(),
                proposal.approvedNotional(),
                proposal.executionAdjustedRisk(),
                proposal.executionAdjustedRisk(),
                null,
                policy.referenceOr("trade-plan-policy"),
                lineage.marketRegime(),
                lineage.sectorCode(),
                lineage.correlationId(),
                proposal.reasonCodes(),
                proposal.explanation());
    }

    private static void validateGeometry(RiskDecisionProposal proposal, PlanLineage lineage) {
        boolean longSide = lineage.direction().isLong();
        BigDecimal entry = proposal.entryPrice();
        BigDecimal stop = proposal.protectiveStop();
        if (longSide && stop.compareTo(entry) >= 0) {
            throw new TradePlanException(TradePlanException.INVALID_GEOMETRY, "long protective stop must be below entry");
        }
        if (!longSide && stop.compareTo(entry) <= 0) {
            throw new TradePlanException(TradePlanException.INVALID_GEOMETRY, "short protective stop must be above entry");
        }
        BigDecimal tick = lineage.tickSize();
        if (tick != null && tick.signum() > 0) {
            if (entry.remainder(tick).signum() != 0 || stop.remainder(tick).signum() != 0) {
                throw new TradePlanException(TradePlanException.INVALID_GEOMETRY, "entry and stop must align to tick size");
            }
        }
        if (lineage.quantityIncrement() < 1) {
            throw new TradePlanException(TradePlanException.INVALID_GEOMETRY, "quantity increment must be >= 1");
        }
    }

    private static BigDecimal noChasePrice(TradePlanPolicy policy, PlanLineage lineage, BigDecimal trigger) {
        if (policy.noChaseTicks() == null || trigger == null || lineage.tickSize() == null) {
            return null;
        }
        BigDecimal distance = lineage.tickSize().multiply(policy.noChaseTicks());
        return lineage.direction().isLong() ? trigger.add(distance) : trigger.subtract(distance);
    }
}
