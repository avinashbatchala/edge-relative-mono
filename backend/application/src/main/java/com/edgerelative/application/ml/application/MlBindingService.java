package com.edgerelative.application.ml.application;

import com.edgerelative.application.ml.persistence.MlBindingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled write/read access to per-instrument ML model bindings. Append-only and effective-dated,
 * so promoting a survivor cannot rewrite history. Resolution is point-in-time.
 *
 * <p>Promoting a new model <em>supersedes</em> the current binding: prior bindings whose window
 * overlaps the new effective date are closed at that date (an empty window retires a same-day binding
 * without deleting it). Bindings that start after the new date cannot be superseded and are rejected.
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

    @Transactional
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
        LocalDate from = request.effectiveFrom();
        // An open-ended binding can be superseded; a binding that starts on or after the new effective
        // date has no earlier window to close and would still overlap, so it must be resolved first.
        List<MlBindingRepository.Window> future = repository.startingAfter(request.instrumentId(), from);
        if (!future.isEmpty()) {
            MlBindingRepository.Window first = future.get(0);
            throw new IllegalArgumentException(
                    "A model binding for this instrument is already effective from " + first.effectiveFrom()
                            + "; choose an effective date on or before it, or retire it first.");
        }
        for (MlBindingRepository.Window window : repository.overlapping(request.instrumentId(), from)) {
            repository.close(window.bindingId(), from);
        }
        return repository.insert(
                request.instrumentId(), request.modelVersionId(), authority,
                from, request.effectiveTo(), lifecycle, request.source());
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
