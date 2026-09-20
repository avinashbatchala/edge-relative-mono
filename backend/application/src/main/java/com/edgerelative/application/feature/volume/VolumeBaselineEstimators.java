package com.edgerelative.application.feature.volume;

import com.edgerelative.application.feature.math.RollingStatistics;

/**
 * Factory for the baselines the documented research plan needs. Deliberately not a framework.
 */
public final class VolumeBaselineEstimators {

    private VolumeBaselineEstimators() {
    }

    public static VolumeBaselineEstimator of(
            BaselineEstimatorType type, double trimmedFraction, int ewSpan) {
        return switch (type) {
            case MEAN -> new Named("MEAN", RollingStatistics::mean);
            case MEDIAN -> new Named("MEDIAN", RollingStatistics::median);
            case TRIMMED_MEAN -> new Named(
                    "TRIMMED_MEAN", samples -> RollingStatistics.trimmedMean(samples, trimmedFraction));
            case EW -> new Named("EW", samples -> exponential(samples, ewSpan));
        };
    }

    private static double exponential(double[] samples, int span) {
        if (samples.length == 0) {
            return Double.NaN;
        }
        if (span < 1) {
            throw new IllegalArgumentException("EW baseline span must be >= 1");
        }
        double alpha = 2.0 / (span + 1.0);
        double running = samples[0];
        for (int i = 1; i < samples.length; i++) {
            running = alpha * samples[i] + (1.0 - alpha) * running;
        }
        return running;
    }

    private record Named(String code, java.util.function.ToDoubleFunction<double[]> delegate)
            implements VolumeBaselineEstimator {

        @Override
        public double estimate(double[] samples) {
            return delegate.applyAsDouble(samples);
        }
    }
}
