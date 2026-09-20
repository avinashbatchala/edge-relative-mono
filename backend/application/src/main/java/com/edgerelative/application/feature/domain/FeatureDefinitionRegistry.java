package com.edgerelative.application.feature.domain;

import static com.edgerelative.application.feature.domain.FeatureKeys.ATR;
import static com.edgerelative.application.feature.domain.FeatureKeys.DIRECTIONAL_VOLUME_LONG;
import static com.edgerelative.application.feature.domain.FeatureKeys.DIRECTIONAL_VOLUME_SHORT;
import static com.edgerelative.application.feature.domain.FeatureKeys.MARKET_ATR;
import static com.edgerelative.application.feature.domain.FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY;
import static com.edgerelative.application.feature.domain.FeatureKeys.MARKET_PRICE_STRUCTURE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_ACCELERATION;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_FAST;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_PERCENTILE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_PERSISTENCE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_RAW;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_SLOW;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_SLOPE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_TREND_STATE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RRS_VS_SECTOR_RAW;
import static com.edgerelative.application.feature.domain.FeatureKeys.RVE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RVOL_CUMULATIVE;
import static com.edgerelative.application.feature.domain.FeatureKeys.RVOL_D1;
import static com.edgerelative.application.feature.domain.FeatureKeys.RVOL_INTERVAL;
import static com.edgerelative.application.feature.domain.FeatureKeys.SECTOR_DIRECTIONAL_EFFICIENCY;
import static com.edgerelative.application.feature.domain.FeatureKeys.SECTOR_PRICE_STRUCTURE;
import static com.edgerelative.application.feature.domain.FeatureKeys.SECTOR_RRS_RAW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The immutable feature registry. One entry per canonical feature key (DD-05 §120).
 */
public final class FeatureDefinitionRegistry {

    private static final Map<String, FeatureDefinition> DEFINITIONS = build();

    private FeatureDefinitionRegistry() {
    }

    private static Map<String, FeatureDefinition> build() {
        Map<String, FeatureDefinition> definitions = new LinkedHashMap<>();
        register(definitions, FeatureDefinition.numeric(
                ATR, "Average True Range", "Volatility baseline used by RRS and normalisation.", List.of()));
        register(definitions, FeatureDefinition.numeric(
                RRS_RAW, "RRS raw", "Volatility-adjusted excess movement vs the broad benchmark.", List.of(ATR)));
        register(definitions, FeatureDefinition.numeric(
                RRS_FAST, "RRS fast", "Fast EMA of RRS raw.", List.of(RRS_RAW)));
        register(definitions, FeatureDefinition.numeric(
                RRS_SLOW, "RRS slow", "Slow EMA of RRS raw.", List.of(RRS_RAW)));
        register(definitions, FeatureDefinition.numeric(
                RRS_PERSISTENCE, "RRS persistence", "Share of trailing RRS raw bars agreeing with RRS fast sign.",
                List.of(RRS_RAW, RRS_FAST)));
        register(definitions, FeatureDefinition.numeric(
                RRS_SLOPE, "RRS slope", "Smoothed slope of RRS fast.", List.of(RRS_FAST)));
        register(definitions, FeatureDefinition.numeric(
                RRS_ACCELERATION, "RRS acceleration", "One-bar change in RRS fast.", List.of(RRS_FAST)));
        register(definitions, FeatureDefinition.numeric(
                RRS_PERCENTILE, "RRS percentile", "Trailing point-in-time percentile of RRS raw.",
                List.of(RRS_RAW)));
        register(definitions, FeatureDefinition.categorical(
                RRS_TREND_STATE, "RRS trend state", "Direction and change of RRS fast.",
                List.of(RRS_FAST, RRS_ACCELERATION)));
        register(definitions, FeatureDefinition.numeric(
                RRS_VS_SECTOR_RAW, "RRS vs sector", "Same RRS formula using the stock's sector benchmark.",
                List.of(ATR)));
        register(definitions, FeatureDefinition.numeric(
                SECTOR_RRS_RAW, "Sector RRS", "Same RRS formula, sector benchmark vs broad market.", List.of()));
        register(definitions, FeatureDefinition.numeric(
                RVOL_D1, "Daily RVOL", "Current session volume versus prior-session daily baseline.", List.of()));
        register(definitions, FeatureDefinition.numeric(
                RVOL_INTERVAL, "Interval RVOL", "Current slot volume versus same-slot prior-session baseline.",
                List.of()));
        register(definitions, FeatureDefinition.numeric(
                RVOL_CUMULATIVE, "Cumulative RVOL",
                "Cumulative session volume versus equivalent session-relative baseline.", List.of()));
        register(definitions, FeatureDefinition.numeric(
                RVE, "Relative Volume Expansion", "Fast minus slow EWMA of log interval RVOL.",
                List.of(RVOL_INTERVAL)));
        register(definitions, FeatureDefinition.numeric(
                DIRECTIONAL_VOLUME_LONG, "Directional volume long", "Up-bar volume divided by down-bar volume.",
                List.of()));
        register(definitions, FeatureDefinition.numeric(
                DIRECTIONAL_VOLUME_SHORT, "Directional volume short", "Down-bar volume divided by up-bar volume.",
                List.of()));
        register(definitions, FeatureDefinition.numeric(
                MARKET_ATR, "Market ATR", "Broad-market volatility context.", List.of()));
        register(definitions, FeatureDefinition.numeric(
                MARKET_DIRECTIONAL_EFFICIENCY, "Market directional efficiency",
                "Trend versus chop for the broad market.", List.of()));
        register(definitions, FeatureDefinition.categorical(
                MARKET_PRICE_STRUCTURE, "Market price structure", "Confirmed swing structure of the broad market.",
                List.of()));
        register(definitions, FeatureDefinition.numeric(
                SECTOR_DIRECTIONAL_EFFICIENCY, "Sector directional efficiency",
                "Trend versus chop for the sector benchmark.", List.of()));
        register(definitions, FeatureDefinition.categorical(
                SECTOR_PRICE_STRUCTURE, "Sector price structure", "Confirmed swing structure of the sector.",
                List.of()));
        return Map.copyOf(definitions);
    }

    private static void register(Map<String, FeatureDefinition> definitions, FeatureDefinition definition) {
        if (definitions.put(definition.featureKey(), definition) != null) {
            throw new IllegalStateException("Duplicate feature definition: " + definition.featureKey());
        }
    }

    public static FeatureDefinition require(String featureKey) {
        FeatureDefinition definition = DEFINITIONS.get(featureKey);
        if (definition == null) {
            throw new IllegalArgumentException("Unregistered feature: " + featureKey);
        }
        return definition;
    }

    public static Map<String, FeatureDefinition> all() {
        return DEFINITIONS;
    }
}
