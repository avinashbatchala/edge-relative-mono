package com.edgerelative.application.feature.math;

import java.util.ArrayList;
import java.util.List;

/**
 * DD-02 §23/§24 confirmed-pivot price structure.
 *
 * <p>A pivot of width {@code k} is confirmed only once the required right-side candles have closed,
 * which prevents look-ahead leakage. Two most recent confirmed highs and lows give
 * {@code BULL_STRUCTURE} (higher high + higher low), {@code BEAR_STRUCTURE} (lower high + lower low),
 * or {@code MIXED}. Fewer than two confirmed pivots of either kind is insufficient history.
 */
public final class PriceStructure {

    public static final String BULL = "BULL_STRUCTURE";
    public static final String BEAR = "BEAR_STRUCTURE";
    public static final String MIXED = "MIXED";

    private PriceStructure() {
    }

    /**
     * {@code null} label means insufficient confirmed pivots.
     */
    public record Result(String state) {

        public boolean sufficient() {
            return state != null;
        }
    }

    public static Result classify(double[] highs, double[] lows, int width) {
        return classify(highs, lows, highs.length, width);
    }

    /**
     * Causal prefix variant: uses only {@code values[0..length-1]}.
     */
    public static Result classify(double[] highs, double[] lows, int length, int width) {
        List<Integer> swingHighs = confirmedPivots(highs, length, width, true);
        List<Integer> swingLows = confirmedPivots(lows, length, width, false);
        if (swingHighs.size() < 2 || swingLows.size() < 2) {
            return new Result(null);
        }
        double lastHigh = highs[swingHighs.get(swingHighs.size() - 1)];
        double priorHigh = highs[swingHighs.get(swingHighs.size() - 2)];
        double lastLow = lows[swingLows.get(swingLows.size() - 1)];
        double priorLow = lows[swingLows.get(swingLows.size() - 2)];
        boolean higherHigh = lastHigh > priorHigh;
        boolean higherLow = lastLow > priorLow;
        boolean lowerHigh = lastHigh < priorHigh;
        boolean lowerLow = lastLow < priorLow;
        if (higherHigh && higherLow) {
            return new Result(BULL);
        }
        if (lowerHigh && lowerLow) {
            return new Result(BEAR);
        }
        return new Result(MIXED);
    }

    private static List<Integer> confirmedPivots(double[] values, int length, int width, boolean high) {
        List<Integer> pivots = new ArrayList<>();
        for (int i = width; i + width < length; i++) {
            boolean pivot = true;
            for (int j = i - width; j <= i + width; j++) {
                if (j == i) {
                    continue;
                }
                if (high ? values[j] >= values[i] : values[j] <= values[i]) {
                    pivot = false;
                    break;
                }
            }
            if (pivot) {
                pivots.add(i);
            }
        }
        return pivots;
    }
}
