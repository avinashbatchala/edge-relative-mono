package com.edgerelative.application.risk.api;

import com.edgerelative.application.risk.domain.RiskCandidate;
import com.edgerelative.application.risk.domain.TradingMode;
import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Explicit API contract for a risk candidate. */
public record RiskCandidateRequest(
        String candidateKey,
        String candidateId,
        long tenantId,
        long brokerAccountId,
        String mode,
        String setupInstanceId,
        long setupObservationId,
        String strategyId,
        String strategyVersion,
        int strategyVersionId,
        long instrumentId,
        String symbol,
        String direction,
        BigDecimal tickSize,
        long quantityIncrement,
        BigDecimal proposedEntryPrice,
        BigDecimal structuralInvalidation,
        String invalidationBasis,
        Instant candidateAt,
        String marketRegime,
        boolean marketRegimeKnown,
        boolean eventRiskKnown,
        boolean eventRiskBlocked,
        Long sectorId,
        String sectorCode,
        LocalDate sectorEffectiveDate,
        Double spreadBps,
        Double expectedExecutableVolume,
        Double medianDailyVolume,
        Long brokerMaxQuantity,
        Long requestedQuantity,
        boolean setupValid,
        String setupStatus,
        String featureSchemaVersion,
        String policyCode) {

    public RiskCandidate toCandidate() {
        return new RiskCandidate(
                candidateKey, candidateId, tenantId, brokerAccountId, TradingMode.valueOf(mode), setupInstanceId,
                setupObservationId, strategyId, strategyVersion, strategyVersionId, instrumentId, symbol,
                Direction.valueOf(direction), tickSize, quantityIncrement, proposedEntryPrice, structuralInvalidation,
                invalidationBasis, candidateAt, marketRegime, marketRegimeKnown, eventRiskKnown, eventRiskBlocked,
                sectorId, sectorCode, sectorEffectiveDate, spreadBps, expectedExecutableVolume, medianDailyVolume,
                brokerMaxQuantity, requestedQuantity, setupValid, setupStatus, featureSchemaVersion, policyCode);
    }
}
