package com.edgerelative.fundamentals.api.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A single statement line item.
 *
 * <p>Money uses {@link BigDecimal}, never binary floating point (DD-06 §5). {@code unit} and
 * {@code scale} make the stored magnitude explicit rather than guessed at display time.
 *
 * @param lineCode stable standardised code, for example {@code total_revenue}
 * @param label human-readable label
 * @param value scaled magnitude
 * @param unit ISO-ish unit, for example {@code INR}
 * @param scale display scale, for example {@code crore}
 */
public record StatementLine(String lineCode, String label, BigDecimal value, String unit, String scale) {

    public StatementLine {
        if (lineCode == null || lineCode.isBlank()) {
            throw new IllegalArgumentException("lineCode must not be blank");
        }
        Objects.requireNonNull(value, "value");
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit must not be blank");
        }
        if (scale == null || scale.isBlank()) {
            throw new IllegalArgumentException("scale must not be blank");
        }
    }
}
