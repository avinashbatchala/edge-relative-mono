package com.edgerelative.application.risk.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A strategy-qualified candidate submitted to risk. The candidate supplies intent and lineage; the
 * evaluator must re-validate setup eligibility from authoritative state and never trust a
 * client-supplied {@link #setupValid()} on its own (DD-03 §4, DD-01 authority boundaries).
 *
 * <p>The candidate also carries the point-in-time inputs the strategy owns: entry reference,
 * structural invalidation and its basis, sector mapping and effective date, liquidity/spread
 * observations, tick size and quantity increment.
 */
public record RiskCandidate(
        String candidateKey,
        String candidateId,
        long tenantId,
        long brokerAccountId,
        TradingMode mode,
        String setupInstanceId,
        long setupObservationId,
        String strategyId,
        String strategyVersion,
        int strategyVersionId,
        long instrumentId,
        String symbol,
        Direction direction,
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
        String policyCode,
        Double referenceAtr) {

    public boolean requestedQuantitySupplied() {
        return requestedQuantity != null && requestedQuantity > 0;
    }
}
