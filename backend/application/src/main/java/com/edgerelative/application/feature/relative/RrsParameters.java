package com.edgerelative.application.feature.relative;

import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.PriceChange;

/**
 * Resolved parameters for the one canonical RRS calculation (DD-02 §33–§42).
 */
public record RrsParameters(
        int atrLength,
        AtrSmoothing atrSmoothing,
        PriceChange priceChange,
        int fastLength,
        int slowLength,
        int persistenceWindow,
        int slopeLookback,
        int percentileWindow,
        int percentileMinSamples) {
}
