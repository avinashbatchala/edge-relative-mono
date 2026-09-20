package com.edgerelative.application.feature.volume;

/**
 * A reusable volume baseline estimator. Implementations receive samples in chronological order so
 * an exponentially weighted estimator is well-defined. This is the only place averaging happens; RVOL
 * features do not call {@code .average()} directly.
 */
public interface VolumeBaselineEstimator {

    String code();

    /**
     * @return the baseline value, or {@link Double#NaN} if no usable samples.
     */
    double estimate(double[] samples);
}
