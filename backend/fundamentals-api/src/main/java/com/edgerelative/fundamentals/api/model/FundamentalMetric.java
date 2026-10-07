package com.edgerelative.fundamentals.api.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A derived or reported metric (ratio, multiple, growth rate, per-share figure).
 *
 * @param metricCode stable code, for example {@code trailing_pe}
 * @param value the value; ratios are unitless, per-share values are {@code INR}
 * @param unit unit label, or {@code ratio} for unitless values
 * @param decimals display precision when known
 */
public record FundamentalMetric(String metricCode, BigDecimal value, String unit, Integer decimals) {

    public FundamentalMetric {
        if (metricCode == null || metricCode.isBlank()) {
            throw new IllegalArgumentException("metricCode must not be blank");
        }
        Objects.requireNonNull(value, "value");
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit must not be blank");
        }
    }
}
