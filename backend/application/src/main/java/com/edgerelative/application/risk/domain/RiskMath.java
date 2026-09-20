package com.edgerelative.application.risk.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Declared monetary/quantity arithmetic. Risk is always rounded toward lower risk. */
public final class RiskMath {

    public static final int MONEY_SCALE = 8;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.DOWN;

    private RiskMath() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return null;
        }
        return money(a.multiply(b));
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, MONEY_SCALE, MONEY_ROUNDING);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.min(b);
    }

    /** Whole shares for a budget: floor(budget / perUnit), never negative, capped to long range. */
    public static long floorQuantity(BigDecimal budget, BigDecimal perUnit) {
        if (budget == null || perUnit == null || perUnit.signum() <= 0 || budget.signum() <= 0) {
            return 0;
        }
        BigDecimal raw = budget.divide(perUnit, 0, RoundingMode.DOWN);
        if (raw.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0) {
            return Long.MAX_VALUE;
        }
        return raw.longValueExact();
    }

    public static BigDecimal floorToTick(BigDecimal value, BigDecimal tick) {
        if (value == null || tick == null || tick.signum() <= 0) {
            return value;
        }
        return value.divide(tick, 0, RoundingMode.FLOOR).multiply(tick).setScale(MONEY_SCALE, RoundingMode.DOWN);
    }

    public static BigDecimal ceilToTick(BigDecimal value, BigDecimal tick) {
        if (value == null || tick == null || tick.signum() <= 0) {
            return value;
        }
        return value.divide(tick, 0, RoundingMode.CEILING).multiply(tick).setScale(MONEY_SCALE, RoundingMode.DOWN);
    }

    public static long roundDownToIncrement(long quantity, long increment) {
        if (increment <= 1) {
            return Math.max(quantity, 0);
        }
        return Math.max(quantity / increment * increment, 0);
    }
}
