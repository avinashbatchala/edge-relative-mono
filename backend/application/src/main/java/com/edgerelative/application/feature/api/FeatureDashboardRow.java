package com.edgerelative.application.feature.api;

import java.time.Instant;
import java.util.Map;

/**
 * One watchlist row for the operator Feature Dashboard.
 *
 * <p>Observational only: canonical identity, market observation, calculated feature values, quality
 * and version lineage. It carries no strategy, risk or execution decision. Missing metrics are
 * {@code null} with a reason in {@link #unavailableReasons}; they are never coerced to zero.
 */
public record FeatureDashboardRow(
        long instrumentId,
        String instrumentKey,
        String symbol,
        String displayName,
        String exchange,
        String segment,
        String instrumentType,
        String timeframe,
        Instant observationTime,
        Instant generatedAt,
        Double lastPrice,
        Double previousClose,
        Double priceChange,
        Double priceChangePercent,
        Double rrsRaw,
        Double rrsFast,
        Double rrsSlow,
        Double rrsPersistence,
        String rrsTrendState,
        String dailyRrsState,
        Double rvolInterval,
        Double rvolCumulative,
        Double rve,
        Double atr,
        Double atrPercent,
        Double vwapDistanceAtr,
        String marketState,
        String sectorState,
        Double sectorRrsRaw,
        String quality,
        String availability,
        String qualityReason,
        Long staleSeconds,
        String featureSchemaVersion,
        Map<String, String> featureVersions,
        Map<String, String> unavailableReasons,
        Map<String, String> unavailableStates) {

    public FeatureDashboardRow {
        featureVersions = featureVersions == null ? Map.of() : Map.copyOf(featureVersions);
        unavailableReasons = unavailableReasons == null ? Map.of() : Map.copyOf(unavailableReasons);
        unavailableStates = unavailableStates == null ? Map.of() : Map.copyOf(unavailableStates);
    }
}
