package com.edgerelative.application.strategy.application;

import com.edgerelative.application.catalog.application.StrategyParametersJson;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.persistence.StrategyBindingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Controlled write/read access to per-instrument strategy bindings. Parameters are validated by the
 * {@link StrategyParameters} constructor before storage; the binding is append-only and
 * effective-dated, so a promoted configuration cannot silently rewrite history.
 */
@Service
public class StrategyBindingService {

    private final StrategyBindingRepository repository;
    private final StrategyParametersJson parametersJson;

    public StrategyBindingService(StrategyBindingRepository repository, StrategyParametersJson parametersJson) {
        this.repository = repository;
        this.parametersJson = parametersJson;
    }

    public record CreateRequest(
            long instrumentId,
            long strategyVersionId,
            Map<String, Object> parameters,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String lifecycleState,
            String source) {
    }

    public record BindingView(
            long instrumentId,
            long strategyVersionId,
            String lifecycleState,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            StrategyParameters parameters) {
    }

    public long create(CreateRequest request) {
        if (request.effectiveFrom() == null) {
            throw new IllegalArgumentException("effectiveFrom is required");
        }
        StrategyParameters parsed = parametersJson.read(request.parameters(), "INSTRUMENT_BINDING", 1);
        String lifecycleState = request.lifecycleState() == null ? "RESEARCH" : request.lifecycleState();
        return repository.insert(
                request.instrumentId(),
                request.strategyVersionId(),
                parametersJson.write(parsed),
                request.effectiveFrom(),
                request.effectiveTo(),
                lifecycleState,
                request.source());
    }

    public Optional<BindingView> effective(long instrumentId, LocalDate asOf) {
        return repository.findEffective(instrumentId, asOf).map(binding -> new BindingView(
                instrumentId,
                binding.strategyVersionId(),
                binding.lifecycleState(),
                binding.effectiveFrom(),
                binding.effectiveTo(),
                parametersJson.readCanonical(binding.parametersJson())));
    }

    public List<BindingView> list(long instrumentId) {
        return repository.listForInstrument(instrumentId).stream()
                .map(binding -> new BindingView(
                        instrumentId,
                        binding.strategyVersionId(),
                        binding.lifecycleState(),
                        binding.effectiveFrom(),
                        binding.effectiveTo(),
                        parametersJson.readCanonical(binding.parametersJson())))
                .toList();
    }
}
