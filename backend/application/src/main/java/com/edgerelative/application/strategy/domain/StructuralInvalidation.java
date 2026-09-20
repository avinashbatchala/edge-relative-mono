package com.edgerelative.application.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The market structure that disproves the setup thesis. Derived from structure known at the
 * evaluation timestamp; risk may later add a protective buffer but must not rewrite it (DD-02 §11).
 */
public record StructuralInvalidation(
        String invalidationType,
        BigDecimal invalidationLevel,
        String referenceObservation,
        Instant referenceTime,
        String basis) {
}
