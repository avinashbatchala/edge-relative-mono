package com.edgerelative.application.feature.math;

/**
 * Deterministic exponential moving average.
 *
 * <p>Warmup: the first {@code span} values are seeded with their arithmetic mean so the series is
 * defined from index {@code span - 1} onward; earlier entries are {@link Double#NaN}. This is the one
 * canonical EMA used by RRS smoothing and RVE so no second smoothing convention can drift in.
 */
public final class Ema {

    private Ema() {
    }

    public static double[] series(double[] values, int span) {
        if (span < 1) {
            throw new IllegalArgumentException("EMA span must be >= 1");
        }
        double[] result = new double[values.length];
        java.util.Arrays.fill(result, Double.NaN);
        if (values.length < span) {
            return result;
        }
        double seed = 0.0;
        for (int i = 0; i < span; i++) {
            seed += values[i];
        }
        seed /= span;
        result[span - 1] = seed;
        double alpha = 2.0 / (span + 1.0);
        for (int i = span; i < values.length; i++) {
            result[i] = alpha * values[i] + (1.0 - alpha) * result[i - 1];
        }
        return result;
    }

    /**
     * Gap-tolerant EMA. {@link Double#NaN} inputs are skipped (the running state is retained and the
     * output stays {@code NaN} at that index), so an early missing benchmark bar cannot poison every
     * later value. The seed is the mean of the first {@code span} valid observations.
     */
    public static double[] seriesSparse(double[] values, int span) {
        if (span < 1) {
            throw new IllegalArgumentException("EMA span must be >= 1");
        }
        double[] result = new double[values.length];
        java.util.Arrays.fill(result, Double.NaN);
        double alpha = 2.0 / (span + 1.0);
        double seedSum = 0.0;
        int valid = 0;
        boolean seeded = false;
        double running = Double.NaN;
        for (int i = 0; i < values.length; i++) {
            if (Double.isNaN(values[i])) {
                continue;
            }
            if (!seeded) {
                seedSum += values[i];
                valid++;
                if (valid == span) {
                    running = seedSum / span;
                    seeded = true;
                    result[i] = running;
                }
                continue;
            }
            running = alpha * values[i] + (1.0 - alpha) * running;
            result[i] = running;
        }
        return result;
    }

    public static int warmup(int span) {
        return span - 1;
    }
}
