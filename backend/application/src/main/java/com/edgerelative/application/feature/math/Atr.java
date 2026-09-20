package com.edgerelative.application.feature.math;

import java.util.Arrays;

/**
 * The single canonical Average True Range implementation (DD-02 §34, DD-05 §144).
 *
 * <p>True range for bar 0 is {@code high - low}; later bars use the Wilder definition. The smoothing
 * convention and length are parameters of the feature version. Entries before warmup are
 * {@link Double#NaN}; callers must treat that as unavailable rather than zero.
 */
public final class Atr {

    private Atr() {
    }

    public static double[] series(
            double[] high, double[] low, double[] close, int length, AtrSmoothing smoothing) {
        if (high.length != low.length || high.length != close.length) {
            throw new IllegalArgumentException("high/low/close must have equal length");
        }
        if (length < 1) {
            throw new IllegalArgumentException("ATR length must be >= 1");
        }
        double[] result = new double[high.length];
        Arrays.fill(result, Double.NaN);
        if (high.length < length) {
            return result;
        }
        double[] trueRange = new double[high.length];
        trueRange[0] = high[0] - low[0];
        for (int i = 1; i < high.length; i++) {
            double previousClose = close[i - 1];
            trueRange[i] = Math.max(
                    high[i] - low[i],
                    Math.max(Math.abs(high[i] - previousClose), Math.abs(low[i] - previousClose)));
        }
        if (smoothing == AtrSmoothing.SIMPLE) {
            double window = 0.0;
            for (int i = 0; i < length; i++) {
                window += trueRange[i];
            }
            result[length - 1] = window / length;
            for (int i = length; i < high.length; i++) {
                window += trueRange[i] - trueRange[i - length];
                result[i] = window / length;
            }
            return result;
        }
        double running = 0.0;
        for (int i = 0; i < length; i++) {
            running += trueRange[i];
        }
        result[length - 1] = running / length;
        for (int i = length; i < high.length; i++) {
            result[i] = (result[i - 1] * (length - 1) + trueRange[i]) / length;
        }
        return result;
    }

    /**
     * Number of bars required before the first ATR value is defined.
     */
    public static int warmupBars(int length) {
        return length;
    }
}
