package com.edgerelative.application.feature.engine;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.math.BarSeries;

/**
 * Maps canonical candle quality onto the feature quality vocabulary (DD-05 §132/§133).
 */
public final class BarQuality {

    private BarQuality() {
    }

    public static FeatureQuality quality(BarSeries series, int index) {
        String raw = series.quality(index);
        if (raw == null || raw.isBlank()) {
            return series.complete(index) ? FeatureQuality.GOOD : FeatureQuality.INCOMPLETE;
        }
        return switch (raw.trim().toUpperCase()) {
            case "GOOD" -> series.complete(index) ? FeatureQuality.GOOD : FeatureQuality.INCOMPLETE;
            case "CORRECTED" -> FeatureQuality.CORRECTED;
            case "DEGRADED" -> FeatureQuality.DEGRADED;
            case "SUSPECT" -> FeatureQuality.SUSPECT;
            case "STALE" -> FeatureQuality.STALE;
            case "INCOMPLETE" -> FeatureQuality.INCOMPLETE;
            case "UNAVAILABLE" -> FeatureQuality.UNAVAILABLE;
            // A covered no-trade interval (DD-05 §103): there is no volume to measure, so volume-
            // derived features are unavailable rather than silently zero.
            case "NO_TRADES" -> FeatureQuality.UNAVAILABLE;
            default -> series.complete(index) ? FeatureQuality.SUSPECT : FeatureQuality.INCOMPLETE;
        };
    }

    /**
     * Finalized features require the bar to be a confirmed close.
     */
    public static FeatureAvailability availability(BarSeries series, int index) {
        return series.complete(index) ? FeatureAvailability.VALID : FeatureAvailability.INCOMPLETE;
    }
}
