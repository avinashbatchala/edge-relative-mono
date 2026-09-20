package com.edgerelative.application.feature.math;

import java.util.Arrays;

/**
 * Small, explicit statistical helpers. No hidden normalisation; every window is caller-supplied.
 */
public final class RollingStatistics {

    private RollingStatistics() {
    }

    public static double mean(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    public static double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double[] copy = values.clone();
        Arrays.sort(copy);
        int middle = copy.length / 2;
        if (copy.length % 2 == 1) {
            return copy[middle];
        }
        return (copy[middle - 1] + copy[middle]) / 2.0;
    }

    public static double trimmedMean(double[] values, double trimFraction) {
        if (values.length == 0) {
            return Double.NaN;
        }
        if (trimFraction <= 0.0) {
            return mean(values);
        }
        double[] copy = values.clone();
        Arrays.sort(copy);
        int trim = (int) Math.floor(copy.length * trimFraction);
        if (trim * 2 >= copy.length) {
            return median(copy);
        }
        double sum = 0.0;
        for (int i = trim; i < copy.length - trim; i++) {
            sum += copy[i];
        }
        return sum / (copy.length - 2 * trim);
    }

    /**
     * Point-in-time percentile rank in {@code [0, 1]}: the share of the trailing window at or below
     * the current observation. The caller supplies only observations available at the anchor.
     */
    public static double percentileRank(double[] windowValues, double current) {
        if (windowValues.length == 0) {
            return Double.NaN;
        }
        int count = 0;
        for (double value : windowValues) {
            if (value <= current) {
                count++;
            }
        }
        return (double) count / windowValues.length;
    }
}
