package com.edgerelative.application.strategy.domain;

import java.util.Set;

/**
 * Immutable, validated strategy parameters. Every value here is a research parameter supplied by
 * configuration — none is invented in code. Threshold comparisons in the engine are documented as
 * inclusive or strict; the engine never assumes a default.
 */
public record StrategyParameters(
        String parameterSetId,
        int parameterVersion,
        Set<SetupFamily> enabledFamilies,
        double rrsM5PersistenceLongMin,
        double rrsM5PersistenceShortMin,
        double minRvolDaily,
        double minRvolInterval,
        double minRvolCumulative,
        double minLiquidityMedianTradedValue,
        double minTechnicalVoidAtr,
        double maxEntryExtensionAtr,
        double nearTriggerDistanceAtr,
        double triggerBufferAtrFraction,
        int triggerBufferTicks,
        int maxBarsSinceTrigger,
        int maxBarsInState,
        int openingBlackoutMinutes,
        int entryCutoffMinutesBeforeClose,
        double dataStalenessSeconds,
        NeutralMarketPolicy neutralMarketPolicy,
        double neutralRrsPersistenceExtra,
        Double compressionMaxRangeAtr,
        Double compressionMaxEfficiency,
        Double compressionMinOverlap,
        Integer horizontalPivotWidth,
        Double horizontalToleranceAtr) {

    /** How the engine treats a NEUTRAL market for Strategy V1 (DD-02 §29/§9). */
    public enum NeutralMarketPolicy {
        /** Conservative default: neutral market blocks validation until calibrated thresholds exist. */
        BLOCK,
        /** Require additionally strengthened stock independence (extra persistence) in a neutral market. */
        REQUIRE_STRONGER
    }

    public StrategyParameters {
        if (parameterSetId == null || parameterSetId.isBlank()) {
            throw new IllegalArgumentException("parameterSetId is required");
        }
        if (parameterVersion < 1) {
            throw new IllegalArgumentException("parameterVersion must be >= 1");
        }
        if (enabledFamilies == null) {
            throw new IllegalArgumentException("enabledFamilies is required");
        }
        enabledFamilies = Set.copyOf(enabledFamilies);
        requireUnitInterval(rrsM5PersistenceLongMin, "rrsM5PersistenceLongMin");
        requireUnitInterval(rrsM5PersistenceShortMin, "rrsM5PersistenceShortMin");
        requireNonNegative(minRvolDaily, "minRvolDaily");
        requireNonNegative(minRvolInterval, "minRvolInterval");
        requireNonNegative(minRvolCumulative, "minRvolCumulative");
        requireNonNegative(minLiquidityMedianTradedValue, "minLiquidityMedianTradedValue");
        requireNonNegative(minTechnicalVoidAtr, "minTechnicalVoidAtr");
        requirePositive(maxEntryExtensionAtr, "maxEntryExtensionAtr");
        requireNonNegative(nearTriggerDistanceAtr, "nearTriggerDistanceAtr");
        requireNonNegative(triggerBufferAtrFraction, "triggerBufferAtrFraction");
        if (triggerBufferTicks < 0) {
            throw new IllegalArgumentException("triggerBufferTicks must be >= 0");
        }
        if (maxBarsSinceTrigger < 1) {
            throw new IllegalArgumentException("maxBarsSinceTrigger must be >= 1");
        }
        if (maxBarsInState < 1) {
            throw new IllegalArgumentException("maxBarsInState must be >= 1");
        }
        if (openingBlackoutMinutes < 0) {
            throw new IllegalArgumentException("openingBlackoutMinutes must be >= 0");
        }
        if (entryCutoffMinutesBeforeClose < 0) {
            throw new IllegalArgumentException("entryCutoffMinutesBeforeClose must be >= 0");
        }
        requirePositive(dataStalenessSeconds, "dataStalenessSeconds");
        if (neutralMarketPolicy == null) {
            throw new IllegalArgumentException("neutralMarketPolicy is required");
        }
        requireNonNegative(neutralRrsPersistenceExtra, "neutralRrsPersistenceExtra");
    }

    public boolean isFamilyEnabled(SetupFamily family) {
        return enabledFamilies.contains(family);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    /**
     * RRS persistence is the share of the window whose RRS sign agrees with RRS fast — a magnitude in
     * {@code [0, 1]}, independent of direction. A minimum threshold expresses "how persistent", and
     * never a signed value: a persistent bearish stock has RRS {@code < 0} and persistence near 1.0.
     */
    private static void requireUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be within [0, 1]");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
