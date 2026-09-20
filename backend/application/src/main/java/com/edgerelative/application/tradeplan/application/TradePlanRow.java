package com.edgerelative.application.tradeplan.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persisted, immutable trade-plan read model (frozen plan inputs, not current market values). */
public record TradePlanRow(
        String planKey,
        long riskDecisionId,
        String decisionKey,
        String riskDecision,
        Instant decisionAt,
        long approvedQuantity,
        BigDecimal approvedRisk,
        BigDecimal approvedNotional,
        long setupObservationId,
        String setupInstanceId,
        long tenantId,
        long brokerAccountId,
        long instrumentId,
        String symbol,
        String displayName,
        String direction,
        String entryPattern,
        String entryMethod,
        String targetMethod,
        long plannedQuantity,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal structuralInvalidation,
        BigDecimal protectiveStop,
        BigDecimal targetReference,
        BigDecimal expectedRewardRisk,
        BigDecimal plannedRisk,
        BigDecimal plannedNotional,
        BigDecimal expectedCost,
        BigDecimal expectedSlippage,
        String invalidationReason,
        UUID correlationId,
        Instant createdAt,
        Instant validFrom,
        Instant expiresAt,
        Instant entryCutoffAt,
        BigDecimal entryTriggerPrice,
        BigDecimal noChasePrice,
        String noChaseBasis,
        String stopBufferMethod,
        String targetRationale,
        String explanation,
        String featureSchemaVersion,
        String planPolicyReference,
        String marketRegime,
        String sectorCode,
        BigDecimal tickSize,
        Long quantityIncrement,
        String strategyId,
        String strategyVersion,
        List<String> reasonCodes) {

    public TradePlanRow {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
