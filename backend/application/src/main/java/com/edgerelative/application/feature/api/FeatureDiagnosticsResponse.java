package com.edgerelative.application.feature.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Trust diagnostics for the displayed feature state (DD-05 observability). Never decorative. */
public record FeatureDiagnosticsResponse(
        Instant generatedAt,
        String engineStatus,
        String timeframe,
        int watchlistCount,
        int healthyCount,
        Map<String, Integer> stateCounts,
        Map<String, Integer> metricGaps,
        Long latestObservationSeconds,
        Long oldestObservationSeconds,
        long persistenceQueueDepth,
        long persistenceDropped,
        Counters counters,
        Versions versions,
        List<InstrumentState> instruments,
        List<String> notes,
        String calculationMode,
        TradingImpact tradingImpact,
        Freshness freshness,
        List<MetricAvailability> metricAvailability) {

    /** Low-cardinality counters surfaced from the feature metrics registry. */
    public record Counters(
            long snapshots,
            long warmupFailures,
            long missingDependencies,
            long alignmentFailures,
            long qualityDowngrades) {
    }

    /** Active feature/schema versions. Individual feature versions are on each row. */
    public record Versions(String featureSchemaVersion, String calculationVersion) {
    }

    /** Per-instrument trust state with a machine-readable reason code. */
    public record InstrumentState(
            long instrumentId,
            String symbol,
            String state,
            String reasonCode,
            Long staleSeconds,
            String quality) {
    }

    /**
     * Authoritative trading-impact gate. This dashboard is observational and no setup/risk gate
     * producer is wired yet, so it reports {@code NOT_EVALUATED} and never invents a decision.
     */
    public record TradingImpact(String status, String label, int scopeCount, String detail) {
    }

    /**
     * Underlying market-data freshness, using the backend freshness policy and NSE session context.
     * {@code policySeconds} is null when no policy is configured (no hard-coded threshold).
     */
    public record Freshness(
            String state,
            String sessionContext,
            Long newestAgeSeconds,
            Long oldestAgeSeconds,
            Long policySeconds,
            Instant asOf,
            String basis) {
    }

    /** One grouped availability issue: metric + state + reason across unique affected instruments. */
    public record MetricAvailability(String metric, String state, String reason, int affectedCount) {
    }
}
