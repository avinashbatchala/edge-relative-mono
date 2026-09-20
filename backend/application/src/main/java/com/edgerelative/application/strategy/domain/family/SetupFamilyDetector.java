package com.edgerelative.application.strategy.domain.family;

import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyParameters;

/**
 * One setup family: an explicit structure, trigger level/confirmation, and family-specific
 * invalidation. Families are registered, not branches in a single method.
 */
public interface SetupFamilyDetector {

    SetupFamily family();

    /** Whether the family is enabled by the active parameter set. Disabled families never qualify. */
    boolean enabled(StrategyParameters parameters);

    FamilyDetection detect(StrategyEvaluationInput input, StrategyParameters parameters, Direction direction);
}
