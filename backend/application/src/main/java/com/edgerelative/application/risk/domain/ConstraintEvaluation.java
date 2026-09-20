package com.edgerelative.application.risk.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Evidence for one evaluated constraint: actual value, limit, remaining capacity, result and the
 * reason it produced. Recorded for every evaluated constraint, not only failures (DD-03 §13).
 */
public record ConstraintEvaluation(
        String name,
        ConstraintKind kind,
        ConstraintStatus status,
        BigDecimal actual,
        BigDecimal limit,
        BigDecimal remaining,
        RiskReasonCode reasonCode,
        boolean binding,
        Map<String, String> details) {

    public ConstraintEvaluation {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ConstraintEvaluation pass(
            String name, ConstraintKind kind, BigDecimal actual, BigDecimal limit, BigDecimal remaining) {
        return new ConstraintEvaluation(name, kind, ConstraintStatus.PASS, actual, limit, remaining, null, false, Map.of());
    }

    public static ConstraintEvaluation fail(
            String name,
            ConstraintKind kind,
            BigDecimal actual,
            BigDecimal limit,
            BigDecimal remaining,
            RiskReasonCode reasonCode,
            boolean binding) {
        return new ConstraintEvaluation(name, kind, ConstraintStatus.FAIL, actual, limit, remaining, reasonCode, binding, Map.of());
    }

    public static ConstraintEvaluation notApplicable(String name, ConstraintKind kind) {
        return new ConstraintEvaluation(name, kind, ConstraintStatus.NOT_APPLICABLE, null, null, null, null, false, Map.of());
    }

    public static ConstraintEvaluation unavailable(String name, ConstraintKind kind, RiskReasonCode reasonCode) {
        return new ConstraintEvaluation(name, kind, ConstraintStatus.UNAVAILABLE, null, null, null, reasonCode, false, Map.of());
    }

    public ConstraintEvaluation withDetail(String key, String value) {
        var merged = new java.util.LinkedHashMap<>(details);
        merged.put(key, value);
        return new ConstraintEvaluation(name, kind, status, actual, limit, remaining, reasonCode, binding, merged);
    }
}
