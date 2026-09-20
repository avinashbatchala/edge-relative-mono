package com.edgerelative.application.feature.math;

/**
 * ATR smoothing convention is part of the versioned definition, not a hidden default (DD-05 §144).
 */
public enum AtrSmoothing {
    WILDER,
    SIMPLE;

    public static AtrSmoothing parse(String value) {
        return AtrSmoothing.valueOf(value.trim().toUpperCase());
    }
}
