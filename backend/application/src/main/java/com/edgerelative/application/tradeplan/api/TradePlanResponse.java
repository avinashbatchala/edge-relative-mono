package com.edgerelative.application.tradeplan.api;

import com.edgerelative.application.tradeplan.application.TradePlanRow;
import com.edgerelative.application.tradeplan.domain.PlanEligibility;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Broker-neutral trade-plan read contract. Frozen plan inputs are returned as persisted; current
 * market values are never merged in. Null means "not available", never zero.
 */
public record TradePlanResponse(
        String planKey,
        String decisionKey,
        String riskDecision,
        Instant decisionAt,
        long setupObservationId,
        String setupInstanceId,
        long tenantId,
        long brokerAccountId,
        long instrumentId,
        String symbol,
        String displayName,
        String direction,
        String strategyId,
        String strategyVersion,
        String entryPattern,
        String entryMethod,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal structuralInvalidation,
        String invalidationReason,
        BigDecimal protectiveStop,
        String stopBufferMethod,
        String targetMethod,
        BigDecimal targetReference,
        String targetRationale,
        BigDecimal expectedRewardRisk,
        BigDecimal entryTriggerPrice,
        BigDecimal noChasePrice,
        String noChaseBasis,
        long plannedQuantity,
        long approvedQuantityCeiling,
        Long quantityIncrement,
        BigDecimal tickSize,
        BigDecimal plannedRisk,
        BigDecimal approvedRiskCeiling,
        BigDecimal plannedNotional,
        BigDecimal approvedNotionalCeiling,
        BigDecimal maximumPlannedLoss,
        BigDecimal expectedCost,
        BigDecimal expectedSlippage,
        Instant createdAt,
        Instant validFrom,
        Instant expiresAt,
        Instant entryCutoffAt,
        String marketRegime,
        String sectorCode,
        UUID correlationId,
        String featureSchemaVersion,
        String policyReference,
        List<String> reasonCodes,
        String explanation,
        Eligibility eligibility) {

    public record Eligibility(
            String status, boolean permitsEntry, Instant evaluatedAt, Instant expiresAt, List<String> reasons) {
    }

    public static TradePlanResponse from(TradePlanRow row, PlanEligibility eligibility) {
        return new TradePlanResponse(
                row.planKey(), row.decisionKey(), row.riskDecision(), row.decisionAt(), row.setupObservationId(),
                row.setupInstanceId(), row.tenantId(), row.brokerAccountId(), row.instrumentId(), row.symbol(),
                row.displayName(), row.direction(), row.strategyId(), row.strategyVersion(), row.entryPattern(),
                row.entryMethod(), row.entryLow(), row.entryHigh(), row.structuralInvalidation(),
                row.invalidationReason(), row.protectiveStop(), row.stopBufferMethod(), row.targetMethod(),
                row.targetReference(), row.targetRationale(), row.expectedRewardRisk(), row.entryTriggerPrice(),
                row.noChasePrice(), row.noChaseBasis(),                 row.plannedQuantity(), row.approvedQuantity(),
                row.quantityIncrement(), row.tickSize(), row.plannedRisk(), row.approvedRisk(),
                row.plannedNotional(), row.approvedNotional(), row.plannedRisk(), row.expectedCost(),
                row.expectedSlippage(), row.createdAt(), row.validFrom(), row.expiresAt(), row.entryCutoffAt(),
                row.marketRegime(), row.sectorCode(), row.correlationId(), row.featureSchemaVersion(),
                row.planPolicyReference(), row.reasonCodes(), row.explanation(),
                new Eligibility(
                        eligibility.status().name(),
                        eligibility.permitsEntry(),
                        eligibility.evaluatedAt(),
                        eligibility.expiresAt(),
                        eligibility.reasons()));
    }
}
