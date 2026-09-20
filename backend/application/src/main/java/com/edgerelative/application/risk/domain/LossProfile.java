package com.edgerelative.application.risk.domain;

import java.math.BigDecimal;

/**
 * Protective stop and the three per-unit loss views (DD-03 §17–§22). The structural invalidation is
 * carried unchanged; only the protective buffer and tick rounding move the stop, always away from
 * the entry (rounding never moves it inside the protective boundary).
 */
public record LossProfile(
        BigDecimal entryPrice,
        BigDecimal structuralInvalidation,
        BigDecimal protectiveStop,
        BigDecimal plannedLossPerUnit,
        BigDecimal executionAllowancePerUnit,
        BigDecimal effectiveLossPerUnit,
        BigDecimal stressExitPrice,
        BigDecimal stressLossPerUnit,
        boolean valid,
        RiskReasonCode invalidReason) {

    public static LossProfile invalid(BigDecimal entry, BigDecimal invalidation, RiskReasonCode reason) {
        return new LossProfile(entry, invalidation, null, null, null, null, null, null, false, reason);
    }
}
