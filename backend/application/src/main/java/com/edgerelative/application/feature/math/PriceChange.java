package com.edgerelative.application.feature.math;

/**
 * Price-change definition for RRS. It is part of the feature version, never an implicit default.
 */
public enum PriceChange {
    /**
     * {@code close_t - close_{t-1}}; the first bar has no change.
     */
    CLOSE_TO_CLOSE,
    /**
     * {@code close_t - open_t}.
     */
    CLOSE_TO_OPEN;

    public double of(BarSeries series, int index) {
        return switch (this) {
            case CLOSE_TO_CLOSE -> index == 0 ? Double.NaN : series.close(index) - series.close(index - 1);
            case CLOSE_TO_OPEN -> series.close(index) - series.open(index);
        };
    }

    public static PriceChange parse(String value) {
        return PriceChange.valueOf(value.trim().toUpperCase());
    }
}
