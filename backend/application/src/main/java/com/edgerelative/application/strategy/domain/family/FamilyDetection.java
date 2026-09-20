package com.edgerelative.application.strategy.domain.family;

import com.edgerelative.application.strategy.domain.ReasonCode;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.SetupTrigger;
import com.edgerelative.application.strategy.domain.StructuralInvalidation;
import java.math.BigDecimal;
import java.util.List;

/**
 * Family detection result: whether a recognized structure exists, the trigger level, a confirmed
 * trigger (only on a completed candle), and the family-specific structural invalidation.
 */
public record FamilyDetection(
        SetupFamily family,
        boolean structurePresent,
        BigDecimal triggerLevel,
        SetupTrigger trigger,
        StructuralInvalidation invalidation,
        List<ReasonCode> reasons) {

    public FamilyDetection {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static FamilyDetection notFound(SetupFamily family, ReasonCode reason) {
        return new FamilyDetection(family, false, null, null, null, List.of(reason));
    }

    public boolean triggerConfirmed() {
        return trigger != null;
    }
}
