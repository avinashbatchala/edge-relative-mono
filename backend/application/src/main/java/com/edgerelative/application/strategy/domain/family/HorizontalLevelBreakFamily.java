package com.edgerelative.application.strategy.domain.family;

import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.ReasonCode;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.SetupTrigger;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.domain.StructuralInvalidation;
import java.math.BigDecimal;
import java.util.List;

/**
 * M5 horizontal level break (DD-02 §62/§73). Implemented but disabled by default: the level's
 * building tolerance (ATR/bps/tick aware) is a research parameter, so enabling it requires explicit
 * parameters and a supplied level. Confirmation is a completed M5 close beyond level + buffer;
 * invalidation is the level itself.
 */
public final class HorizontalLevelBreakFamily implements SetupFamilyDetector {

    @Override
    public SetupFamily family() {
        return SetupFamily.M5_HORIZONTAL_LEVEL_BREAK;
    }

    @Override
    public boolean enabled(StrategyParameters parameters) {
        return parameters.isFamilyEnabled(family());
    }

    @Override
    public FamilyDetection detect(
            StrategyEvaluationInput input, StrategyParameters parameters, Direction direction) {
        if (parameters.horizontalPivotWidth() == null || parameters.horizontalToleranceAtr() == null) {
            throw new IllegalStateException(
                    "Horizontal level parameters are required when M5_HORIZONTAL_LEVEL_BREAK is enabled");
        }
        StrategyEvaluationInput.StructureContext structure =
                input.stock() == null ? null : input.stock().structure();
        StrategyEvaluationInput.CompletedCandle candle = input.completedCandle();
        Double atr = input.stock() == null ? null : input.stock().atrM5();
        if (structure == null
                || !structure.horizontalLevelPresent()
                || structure.horizontalLevel() == null
                || candle == null
                || atr == null) {
            return FamilyDetection.notFound(family(), ReasonCode.SETUP_STRUCTURE_NOT_FOUND);
        }
        BigDecimal level = structure.horizontalLevel();
        BigDecimal buffer = FamilyMath.buffer(level, atr, input.stock().tickSize(), parameters);
        StructuralInvalidation invalidation = new StructuralInvalidation(
                "HORIZONTAL_LEVEL", level, null, input.evaluationTimestamp(), "Horizontal pivot cluster level");
        boolean confirmed = direction.isLong()
                ? candle.close().compareTo(level.add(buffer)) > 0
                : candle.close().compareTo(level.subtract(buffer)) < 0;
        if (!confirmed) {
            return new FamilyDetection(
                    family(), true, level, null, invalidation, List.of(ReasonCode.NO_CONFIRMED_TRIGGER));
        }
        Double current = input.stock().lastPrice() != null
                ? input.stock().lastPrice()
                : candle.close().doubleValue();
        SetupTrigger trigger = new SetupTrigger(
                family().name(),
                level,
                buffer,
                candle.close(),
                candle.openTime(),
                candle.closeTime(),
                null,
                FamilyMath.distance(level, current),
                FamilyMath.extension(level, current, atr));
        return new FamilyDetection(family(), true, level, trigger, invalidation, List.of());
    }
}
