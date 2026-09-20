package com.edgerelative.application.strategy.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Structured, auditable result of one evaluation. A {@code VALID} result means a strategy-qualified
 * opportunity only — never risk approval, sizing, an order, or a fill (DD-02 setup lifecycle).
 */
public record StrategyEvaluationResult(
        String evaluationId,
        UUID setupInstanceId,
        String strategyId,
        String strategyVersion,
        String parameterSetId,
        int parameterVersion,
        long instrumentId,
        long featureSnapshotId,
        String marketObservationKey,
        Instant evaluationTimestamp,
        LocalDate tradingDate,
        Direction direction,
        SetupState previousState,
        SetupState setupState,
        boolean transitioned,
        SetupInitialization initialization,
        SetupFamily setupFamily,
        boolean valid,
        String marketBias,
        String marketRegime,
        String marketPhase,
        String sectorState,
        String dailyStructure,
        Double rrsD1,
        Double rrsM5,
        Double rrsM5Persistence,
        String rrsMultitimeframe,
        Double rvolDaily,
        Double rvolInterval,
        Double rvolCumulative,
        Double rve,
        String liquidityState,
        SetupTrigger trigger,
        StructuralInvalidation invalidation,
        Double structuralRR,
        String targetReference,
        List<HardGateResult> hardGates,
        List<QualityFactorResult> qualityFactors,
        List<ReasonCode> reasonCodes,
        ReasonCode primaryReasonCode,
        String explanation,
        List<String> lineage) {

    public StrategyEvaluationResult {
        hardGates = hardGates == null ? List.of() : List.copyOf(hardGates);
        qualityFactors = qualityFactors == null ? List.of() : List.copyOf(qualityFactors);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        lineage = lineage == null ? List.of() : List.copyOf(lineage);
    }
}
