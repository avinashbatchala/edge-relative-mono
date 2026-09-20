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
 * M5 3/8 EMA confirmation (DD-02 §69), the one family whose rules DD-02 specifies precisely.
 *
 * <p>Long confirmation: EMA3 crosses from at-or-below EMA8 to above it on a completed candle. Short
 * is symmetric. The trigger level is EMA8; the family invalidation is an EMA3 recross of EMA8.
 * The "retest and separate" variant needs a tolerance that DD-02 leaves to research, so it is not
 * implemented here (reported as a gap) rather than guessed.
 */
public final class ThreeEightConfirmationFamily implements SetupFamilyDetector {

    @Override
    public SetupFamily family() {
        return SetupFamily.M5_3_8_CONFIRMATION;
    }

    @Override
    public boolean enabled(StrategyParameters parameters) {
        return parameters.isFamilyEnabled(family());
    }

    @Override
    public FamilyDetection detect(
            StrategyEvaluationInput input, StrategyParameters parameters, Direction direction) {
        StrategyEvaluationInput.StructureContext structure =
                input.stock() == null ? null : input.stock().structure();
        if (structure == null
                || !structure.ema38Present()
                || structure.ema3() == null
                || structure.ema8() == null
                || structure.ema3Previous() == null
                || structure.ema8Previous() == null) {
            return FamilyDetection.notFound(family(), ReasonCode.SETUP_STRUCTURE_NOT_FOUND);
        }
        BigDecimal level = structure.ema8();
        StructuralInvalidation invalidation = new StructuralInvalidation(
                "EMA8_RECROSS", level, null, input.evaluationTimestamp(), "EMA3 recrosses EMA8");

        boolean crossed = direction.isLong()
                ? structure.ema3Previous().compareTo(structure.ema8Previous()) <= 0
                        && structure.ema3().compareTo(structure.ema8()) > 0
                : structure.ema3Previous().compareTo(structure.ema8Previous()) >= 0
                        && structure.ema3().compareTo(structure.ema8()) < 0;

        StrategyEvaluationInput.CompletedCandle candle = input.completedCandle();
        if (!crossed || candle == null) {
            return new FamilyDetection(
                    family(), true, level, null, invalidation, List.of(ReasonCode.NO_CONFIRMED_TRIGGER));
        }
        Double atr = input.stock().atrM5();
        Double current = input.stock().lastPrice() != null
                ? input.stock().lastPrice()
                : candle.close().doubleValue();
        SetupTrigger trigger = new SetupTrigger(
                family().name(),
                level,
                BigDecimal.ZERO,
                candle.close(),
                candle.openTime(),
                candle.closeTime(),
                null,
                FamilyMath.distance(level, current),
                FamilyMath.extension(level, current, atr));
        return new FamilyDetection(family(), true, level, trigger, invalidation, List.of());
    }
}
