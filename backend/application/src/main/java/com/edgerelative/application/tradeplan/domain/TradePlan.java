package com.edgerelative.application.tradeplan.domain;

import com.edgerelative.application.risk.domain.RiskReasonCode;
import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable, broker-independent approved trade intent (DD01 §67–69, DD02 §86–91). It is created only
 * from an APPROVE/REDUCE risk decision and is point-in-time: current market values are never merged
 * into it. Null means "not available" for a price/target/metric — never zero.
 *
 * <p>Thesis invalidation ({@code structuralInvalidation}) is distinct from the protective stop
 * ({@code protectiveStop}). Maximum planned loss is a planning estimate, not a guaranteed cap.
 */
public record TradePlan(
        String planKey,
        String decisionKey,
        long riskDecisionId,
        long setupObservationId,
        String setupInstanceId,
        String setupFamily,
        long tenantId,
        long brokerAccountId,
        long instrumentId,
        String symbol,
        Direction direction,
        String strategyId,
        String strategyVersion,
        int strategyVersionId,
        long marketObservationId,
        String featureSchemaVersion,
        String marketObservationLineage,
        Instant createdAt,
        Instant decisionAsOf,
        Instant observedAt,
        Instant validFrom,
        Instant expiresAt,
        Instant entryCutoffAt,
        String entryMethod,
        String entryPattern,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal entryReference,
        BigDecimal entryTriggerPrice,
        BigDecimal noChasePrice,
        String noChaseBasis,
        BigDecimal structuralInvalidation,
        String invalidationReason,
        String invalidationBasis,
        BigDecimal protectiveStop,
        String stopBufferMethod,
        String targetMethod,
        BigDecimal targetReference,
        String targetRationale,
        BigDecimal expectedRewardRisk,
        long plannedQuantity,
        long approvedQuantityCeiling,
        long quantityIncrement,
        BigDecimal tickSize,
        BigDecimal plannedRisk,
        BigDecimal approvedRiskCeiling,
        BigDecimal plannedNotional,
        BigDecimal approvedNotionalCeiling,
        BigDecimal maximumPlannedLoss,
        BigDecimal expectedCost,
        BigDecimal expectedSlippage,
        String policyReference,
        String marketRegime,
        String sectorCode,
        UUID correlationId,
        List<RiskReasonCode> reasonCodes,
        String explanation) {

    public TradePlan {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        if (planKey == null || planKey.isBlank()) {
            throw new IllegalArgumentException("planKey is required");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction is required");
        }
        if (entryReference == null || protectiveStop == null || structuralInvalidation == null) {
            throw new IllegalArgumentException("entry, protective stop, and invalidation are required");
        }
        if (plannedQuantity < 1) {
            throw new IllegalArgumentException("plannedQuantity must be positive");
        }
        if (plannedQuantity > approvedQuantityCeiling
                || plannedRisk.compareTo(approvedRiskCeiling) > 0
                || plannedNotional.compareTo(approvedNotionalCeiling) > 0) {
            throw new IllegalArgumentException("planned values must not exceed approved ceilings");
        }
    }
}
