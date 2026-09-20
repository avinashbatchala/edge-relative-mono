package com.edgerelative.application.risk.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, explainable output of one risk evaluation (DD-03 §152). It is a proposal only: it
 * authorizes no order and creates no trade. {@code decisionKey} is the stable idempotency identity
 * for the candidate/context/policy combination.
 */
public record RiskDecisionProposal(
        String decisionKey,
        String candidateKey,
        String candidateId,
        Instant decidedAt,
        TradingMode mode,
        String policyCode,
        int policyVersion,
        PolicyState policyState,
        String contextKey,
        long contextVersion,
        long setupObservationId,
        String setupInstanceId,
        long strategyVersionId,
        long brokerAccountId,
        long instrumentId,
        String symbol,
        Direction direction,
        RiskState riskState,
        RiskDecisionType decision,
        Long requestedQuantity,
        long riskSizedQuantity,
        long approvedQuantity,
        BigDecimal entryPrice,
        BigDecimal structuralInvalidation,
        BigDecimal protectiveStop,
        BigDecimal plannedLossPerUnit,
        BigDecimal effectiveLossPerUnit,
        BigDecimal stressLossPerUnit,
        BigDecimal nominalRisk,
        BigDecimal executionAdjustedRisk,
        BigDecimal stressRisk,
        BigDecimal approvedNotional,
        BigDecimal marginRequirement,
        BigDecimal availableTradeRiskBudget,
        BigDecimal riskReferenceEquity,
        Map<String, BigDecimal> preDecisionCapacity,
        Map<String, BigDecimal> projectedCapacity,
        List<QuantityCap> quantityCaps,
        List<ConstraintEvaluation> constraints,
        List<RiskReasonCode> reasonCodes,
        RiskReasonCode primaryReason,
        String explanation) {

    public RiskDecisionProposal {
        preDecisionCapacity = preDecisionCapacity == null ? Map.of() : Map.copyOf(preDecisionCapacity);
        projectedCapacity = projectedCapacity == null ? Map.of() : Map.copyOf(projectedCapacity);
        quantityCaps = quantityCaps == null ? List.of() : List.copyOf(quantityCaps);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
