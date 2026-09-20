package com.edgerelative.application.feature.volume;

/**
 * Research must be able to compare baselines, so the estimator is versioned (DD-02 §47).
 */
public enum BaselineEstimatorType {
    MEAN,
    MEDIAN,
    TRIMMED_MEAN,
    EW;

    public static BaselineEstimatorType parse(String value) {
        return BaselineEstimatorType.valueOf(value.trim().toUpperCase());
    }
}
