package com.edgerelative.application.feature.policy;

import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.PriceChange;
import com.edgerelative.application.feature.volume.BaselineEstimatorType;

import java.util.Map;

/**
 * Immutable, fully-resolved feature parameters (DD-05 §120–§122).
 *
 * <p>Every field here is empirical unless a design document fixes it. Nothing in the calculation
 * code chooses a threshold; changing a value changes the feature version's parameter hash, so old
 * results cannot be silently reinterpreted (DD-04 configuration rules).
 */
public record FeaturePolicy(
        int historyDays,
        Benchmark benchmark,
        Atr atr,
        Rrs rrs,
        Rvol rvol,
        Rve rve,
        Structure structure,
        DirectionalVolume directionalVolume) {

    public record Benchmark(String marketCode) {
    }

    public record Atr(Map<String, Integer> lengthByTimeframe, int defaultLength, AtrSmoothing smoothing) {
        public Atr {
            lengthByTimeframe = Map.copyOf(lengthByTimeframe);
            if (defaultLength < 1) {
                throw new IllegalArgumentException("ATR default length must be >= 1");
            }
        }

        public int lengthFor(String timeframe) {
            return lengthByTimeframe.getOrDefault(timeframe, defaultLength);
        }
    }

    public record Rrs(
            PriceChange priceChange,
            int fastLength,
            int slowLength,
            int persistenceWindow,
            int slopeLookback,
            int percentileWindow,
            int percentileMinSamples) {
    }

    public record Rvol(
            BaselineEstimatorType estimator,
            int dailyLookback,
            int intervalLookback,
            int cumulativeLookback,
            int minSamples,
            double trimmedFraction,
            int ewSpan) {
    }

    public record Rve(int fastLength, int slowLength) {
    }

    public record Structure(int pivotWidth, int efficiencyWindow) {
    }

    public record DirectionalVolume(int window) {
    }
}
