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
 * M5 compression breakout (DD-02 §66–68). Implemented but disabled by default: DD-02 states no
 * compression threshold is fixed before research, so enabling it requires explicit parameters and a
 * supplied compression structure. Confirmation is a completed M5 close beyond the boundary plus the
 * tick/ATR-aware buffer; invalidation is the opposite compression boundary.
 */
public final class CompressionBreakoutFamily implements SetupFamilyDetector {

    @Override
    public SetupFamily family() {
        return SetupFamily.M5_COMPRESSION_BREAKOUT;
    }

    @Override
    public boolean enabled(StrategyParameters parameters) {
        return parameters.isFamilyEnabled(family());
    }

    @Override
    public FamilyDetection detect(
            StrategyEvaluationInput input, StrategyParameters parameters, Direction direction) {
        if (parameters.compressionMaxRangeAtr() == null
                || parameters.compressionMaxEfficiency() == null
                || parameters.compressionMinOverlap() == null) {
            throw new IllegalStateException(
                    "Compression parameters are required when M5_COMPRESSION_BREAKOUT is enabled");
        }
        StrategyEvaluationInput.StructureContext structure =
                input.stock() == null ? null : input.stock().structure();
        StrategyEvaluationInput.CompletedCandle candle = input.completedCandle();
        Double atr = input.stock() == null ? null : input.stock().atrM5();
        if (structure == null
                || !structure.compressionPresent()
                || structure.compressionHigh() == null
                || structure.compressionLow() == null
                || structure.compressionEfficiency() == null
                || structure.compressionOverlap() == null
                || candle == null
                || atr == null) {
            return FamilyDetection.notFound(family(), ReasonCode.SETUP_STRUCTURE_NOT_FOUND);
        }
        double rangeAtr = structure.compressionHigh()
                        .subtract(structure.compressionLow())
                        .doubleValue()
                / atr;
        if (rangeAtr > parameters.compressionMaxRangeAtr()
                || structure.compressionEfficiency() > parameters.compressionMaxEfficiency()
                || structure.compressionOverlap() < parameters.compressionMinOverlap()) {
            return FamilyDetection.notFound(family(), ReasonCode.SETUP_STRUCTURE_FAILED);
        }
        BigDecimal level = direction.isLong() ? structure.compressionHigh() : structure.compressionLow();
        BigDecimal buffer = FamilyMath.buffer(level, atr, input.stock().tickSize(), parameters);
        StructuralInvalidation invalidation = new StructuralInvalidation(
                "COMPRESSION_OPPOSITE_BOUNDARY",
                direction.isLong() ? structure.compressionLow() : structure.compressionHigh(),
                null,
                input.evaluationTimestamp(),
                "Opposite side of the compression range");
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
