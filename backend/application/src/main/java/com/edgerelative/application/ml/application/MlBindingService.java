package com.edgerelative.application.ml.application;

import com.edgerelative.application.ml.persistence.MlBindingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Controlled write/read access to per-instrument ML model bindings. Append-only and effective-dated,
 * so promoting a survivor cannot rewrite history. Resolution is point-in-time.
 */
@Service
public class MlBindingService {

    private static final Set<String> AUTHORITY = Set.of("OBSERVER", "RANKER", "FILTER", "RISK_REDUCER");
    private static final Set<String> LIFECYCLE =
            Set.of("RESEARCH", "VALIDATED", "SHADOW", "PAPER", "LIVE_LIMITED", "PRODUCTION", "RETIRED");

    private final MlBindingRepository repository;

    public MlBindingService(MlBindingRepository repository) {
        this.repository = repository;
    }

    public record CreateRequest(
            long instrumentId,
            long modelVersionId,
            String authorityLevel,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String lifecycleState,
            String source) {
    }

    public record BindingView(
            long instrumentId,
            long modelVersionId,
            String authorityLevel,
            String lifecycleState,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public long create(CreateRequest request) {
        if (request.effectiveFrom() == null) {
            throw new IllegalArgumentException("effectiveFrom is required");
        }
        String authority = request.authorityLevel() == null ? "RANKER" : request.authorityLevel();
        if (!AUTHORITY.contains(authority)) {
            throw new IllegalArgumentException("invalid authorityLevel: " + authority);
        }
        String lifecycle = request.lifecycleState() == null ? "RESEARCH" : request.lifecycleState();
        if (!LIFECYCLE.contains(lifecycle)) {
            throw new IllegalArgumentException("invalid lifecycleState: " + lifecycle);
        }
        return repository.insert(
                request.instrumentId(), request.modelVersionId(), authority,
                request.effectiveFrom(), request.effectiveTo(), lifecycle, request.source());
    }

    public Optional<BindingView> effective(long instrumentId, LocalDate asOf) {
        return repository.findEffective(instrumentId, asOf).map(binding -> new BindingView(
                instrumentId, binding.modelVersionId(), binding.authorityLevel(),
                binding.lifecycleState(), binding.effectiveFrom(), binding.effectiveTo()));
    }

    public List<BindingView> list(long instrumentId) {
        return repository.listForInstrument(instrumentId).stream()
                .map(binding -> new BindingView(
                        instrumentId, binding.modelVersionId(), binding.authorityLevel(),
                        binding.lifecycleState(), binding.effectiveFrom(), binding.effectiveTo()))
                .toList();
    }
}
