package com.edgerelative.application.strategy.application;

import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.util.Optional;

/** Supplies the active research parameter set, or empty when the strategy is not configured. */
public record StrategyParametersProvider(Optional<StrategyParameters> parameters) {

    public boolean enabled() {
        return parameters.isPresent();
    }
}
