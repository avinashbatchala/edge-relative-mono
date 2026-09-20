package com.edgerelative.application.tradeplan.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Exact point-in-time lineage the plan must preserve: the setup observation, its market/feature
 * observation, the canonical instrument, and the setup family. The plan never reads the latest
 * measurement at display time.
 */
public record PlanLineage(
        long setupObservationId,
        String setupInstanceId,
        String setupFamily,
        String setupStatus,
        long tenantId,
        long brokerAccountId,
        long marketObservationId,
        String featureSchemaVersion,
        String marketObservationLineage,
        long instrumentId,
        String symbol,
        Direction direction,
        String strategyId,
        String strategyVersion,
        int strategyVersionId,
        BigDecimal tickSize,
        long quantityIncrement,
        String marketRegime,
        String sectorCode,
        UUID correlationId,
        Instant observedAt,
        String triggerType,
        BigDecimal triggerLevel,
        Double entryExtensionAtr,
        String invalidationType,
        BigDecimal invalidationLevel,
        String invalidationBasis) {
}
