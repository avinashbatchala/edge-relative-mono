package com.edgerelative.application.strategy.application;

import com.edgerelative.application.catalog.application.StrategyParametersJson;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.persistence.StrategyBindingRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves the strategy parameter set for an instrument at a decision date: the point-in-time
 * per-instrument binding if one is effective, otherwise the configured global research parameters.
 * The binding wins so a stock can diverge from the shared default without a separate strategy
 * definition (DD-01 §51).
 */
@Service
public class StrategyBindingResolver {

    private final StrategyBindingRepository repository;
    private final StrategyParametersJson parametersJson;
    private final StrategyParametersProvider fallback;

    public StrategyBindingResolver(
            StrategyBindingRepository repository,
            StrategyParametersJson parametersJson,
            StrategyParametersProvider fallback) {
        this.repository = repository;
        this.parametersJson = parametersJson;
        this.fallback = fallback;
    }

    public record Resolved(StrategyParameters parameters, Long strategyVersionId, String source) {
    }

    public Optional<Resolved> resolve(long instrumentId, LocalDate asOf) {
        Optional<StrategyBindingRepository.Binding> binding = repository.findEffective(instrumentId, asOf);
        if (binding.isPresent()) {
            StrategyParameters parameters = parametersJson.readCanonical(binding.get().parametersJson());
            return Optional.of(new Resolved(parameters, binding.get().strategyVersionId(), "BINDING"));
        }
        return fallback.parameters().map(parameters -> new Resolved(parameters, null, "GLOBAL"));
    }

    public boolean configured() {
        return fallback.enabled() || repository.hasBindings();
    }
}
