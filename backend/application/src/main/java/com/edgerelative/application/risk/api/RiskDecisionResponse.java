package com.edgerelative.application.risk.api;

import com.edgerelative.application.risk.domain.QuantityCap;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Explainable risk decision. It is a permission, never an order. */
public record RiskDecisionResponse(
        String decisionKey,
        Instant decidedAt,
        String mode,
        String decision,
        String riskState,
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
        BigDecimal availableTradeRiskBudget,
        List<String> reasonCodes,
        String primaryReason,
        List<Cap> bindingConstraints,
        List<Constraint> constraints,
        String explanation) {

    public record Cap(String name, long quantity, String reason) {
    }

    public record Constraint(String name, String status, BigDecimal actual, BigDecimal limit, BigDecimal remaining, String reason) {
    }

    public static RiskDecisionResponse from(RiskDecisionProposal proposal) {
        return new RiskDecisionResponse(
                proposal.decisionKey(),
                proposal.decidedAt(),
                proposal.mode() == null ? null : proposal.mode().name(),
                proposal.decision().name(),
                proposal.riskState() == null ? null : proposal.riskState().name(),
                proposal.requestedQuantity(),
                proposal.riskSizedQuantity(),
                proposal.approvedQuantity(),
                proposal.entryPrice(),
                proposal.structuralInvalidation(),
                proposal.protectiveStop(),
                proposal.plannedLossPerUnit(),
                proposal.effectiveLossPerUnit(),
                proposal.stressLossPerUnit(),
                proposal.nominalRisk(),
                proposal.executionAdjustedRisk(),
                proposal.stressRisk(),
                proposal.approvedNotional(),
                proposal.availableTradeRiskBudget(),
                proposal.reasonCodes().stream().map(Enum::name).toList(),
                proposal.primaryReason() == null ? null : proposal.primaryReason().name(),
                proposal.quantityCaps().stream()
                        .filter(QuantityCap::binding)
                        .map(cap -> new Cap(cap.name(), cap.quantity(), cap.reasonCode() == null ? null : cap.reasonCode().name()))
                        .toList(),
                proposal.constraints().stream()
                        .map(constraint -> new Constraint(
                                constraint.name(),
                                constraint.status().name(),
                                constraint.actual(),
                                constraint.limit(),
                                constraint.remaining(),
                                constraint.reasonCode() == null ? null : constraint.reasonCode().name()))
                        .toList(),
                proposal.explanation());
    }
}
