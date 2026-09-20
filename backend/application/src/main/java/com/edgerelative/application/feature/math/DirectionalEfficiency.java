package com.edgerelative.application.feature.math;

/**
 * DD-02 §25 directional efficiency: {@code |C_t - C_{t-n}| / sum(|C_i - C_{i-1}|)} over {@code n}
 * bars. {@code 0} is choppy, {@code 1} is fully directional. Returns {@link Double#NaN} when there is
 * not enough history; a flat series has zero path length and is defined as {@code 0}.
 */
public final class DirectionalEfficiency {

    private DirectionalEfficiency() {
    }

    public static double compute(double[] closes, int window) {
        return compute(closes, closes.length, window);
    }

    /**
     * Causal prefix variant: uses only {@code closes[0..length-1]}.
     */
    public static double compute(double[] closes, int length, int window) {
        if (window < 1 || length < window + 1) {
            return Double.NaN;
        }
        int last = length - 1;
        int start = length - window;
        double net = Math.abs(closes[last] - closes[start - 1]);
        double path = 0.0;
        for (int i = start; i < length; i++) {
            path += Math.abs(closes[i] - closes[i - 1]);
        }
        if (path == 0.0) {
            return 0.0;
        }
        return net / path;
    }
}
