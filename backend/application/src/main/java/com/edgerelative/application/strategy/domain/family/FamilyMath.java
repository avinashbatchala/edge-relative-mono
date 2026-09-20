package com.edgerelative.application.strategy.domain.family;

import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.math.BigDecimal;

/** Shared, tick-size-aware trigger buffer and extension math. */
public final class FamilyMath {

    private FamilyMath() {
    }

    /** Buffer = tradeable ticks + configured ATR fraction. tickSize may be absent (ticks contribute 0). */
    public static BigDecimal buffer(BigDecimal triggerLevel, Double atr, Double tickSize, StrategyParameters parameters) {
        BigDecimal tickPart = BigDecimal.valueOf(parameters.triggerBufferTicks())
                .multiply(BigDecimal.valueOf(tickSize == null ? 0.0 : tickSize));
        BigDecimal atrPart = atr == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(parameters.triggerBufferAtrFraction() * atr);
        return tickPart.add(atrPart);
    }

    /** {@code abs(current - level) / ATR}; null when ATR is missing so the gate reports UNAVAILABLE. */
    public static Double extension(BigDecimal level, Double current, Double atr) {
        if (level == null || current == null || atr == null || atr <= 0) {
            return null;
        }
        return Math.abs(current - level.doubleValue()) / atr;
    }

    public static BigDecimal distance(BigDecimal level, Double current) {
        if (level == null || current == null) {
            return null;
        }
        return BigDecimal.valueOf(current - level.doubleValue());
    }
}
