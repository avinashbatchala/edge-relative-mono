package com.edgerelative.application.strategy.context;

/**
 * Versioned assumptions used to derive strategy decision context from canonical feature
 * measurements (DD-02 §22/§24/§28). These thresholds are research parameters, never production
 * defaults; changing any of them changes {@link #version} and the run manifest.
 *
 * <p>{@code assumeEventRiskClear} encodes the honest gap that no event-calendar producer exists yet:
 * research runs take the explicit assumption "no event blocks at this anchor", while strict runs
 * leave event risk unknown and therefore fail closed.
 */
public record DecisionContextPolicy(
        String version,
        double trendEfficiencyMin,
        int liquidityWindow,
        int technicalVoidWindow,
        int emaFastLength,
        int emaSlowLength,
        boolean assumeEventRiskClear) {

    public DecisionContextPolicy {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        if (!Double.isFinite(trendEfficiencyMin) || trendEfficiencyMin < 0.0 || trendEfficiencyMin > 1.0) {
            throw new IllegalArgumentException("trendEfficiencyMin must be within [0, 1]");
        }
        if (liquidityWindow < 1) {
            throw new IllegalArgumentException("liquidityWindow must be >= 1");
        }
        if (technicalVoidWindow < 1) {
            throw new IllegalArgumentException("technicalVoidWindow must be >= 1");
        }
        if (emaFastLength < 1 || emaSlowLength <= emaFastLength) {
            throw new IllegalArgumentException("EMA lengths must satisfy 1 <= fast < slow");
        }
    }

    /** Deterministic research derivation with an explicit "no event calendar" assumption. */
    public static DecisionContextPolicy research() {
        return new DecisionContextPolicy("er-decision-context-v1", 0.35, 20, 20, 3, 8, true);
    }

    /** Strict production derivation: no research fallback, event risk unknown fails closed. */
    public static DecisionContextPolicy strict() {
        return new DecisionContextPolicy("er-decision-context-v1", 0.35, 20, 20, 3, 8, false);
    }
}
