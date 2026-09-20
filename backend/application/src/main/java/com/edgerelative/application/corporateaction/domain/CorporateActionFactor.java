package com.edgerelative.application.corporateaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One explicit, versioned corporate-action adjustment factor (DD-05 §113).
 *
 * <p>{@code priceFactor} multiplies prices for bars strictly before {@code exDate}; {@code
 * quantityFactor} multiplies volume/quantity for those bars. The two are separate because dividend
 * and split/bonus semantics differ; only SPLIT and BONUS are supported today.
 *
 * <p>{@code availableAt} is the point-in-time coordinate: a factor is applied to an as-of read only
 * if it was known at that instant (DD-05 §260).
 */
public record CorporateActionFactor(
        long corporateActionId,
        long instrumentId,
        String actionType,
        LocalDate exDate,
        BigDecimal priceFactor,
        BigDecimal quantityFactor,
        Instant availableAt,
        String definition) {
}
