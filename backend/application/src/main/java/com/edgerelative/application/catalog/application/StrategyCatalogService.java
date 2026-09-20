package com.edgerelative.application.catalog.application;

import com.edgerelative.application.catalog.persistence.StrategyCatalogRepository;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog management for strategy configurations. Versions are immutable, so "update" appends a new
 * version and "delete" retires the mutable parent. Existing versions stay resolvable by id.
 */
@Service
public class StrategyCatalogService {

    private static final Set<String> LIFECYCLE_STATES = Set.of(
            "RESEARCH", "EXPERIMENTAL", "BACKTESTED", "VALIDATED", "SHADOW", "PAPER", "LIVE_LIMITED",
            "PRODUCTION", "RETIRED");

    private final StrategyCatalogRepository repository;
    private final StrategyParametersJson parametersJson;

    public StrategyCatalogService(StrategyCatalogRepository repository, StrategyParametersJson parametersJson) {
        this.repository = repository;
        this.parametersJson = parametersJson;
    }

    public record StrategyVersionView(
            long strategyVersionId,
            int version,
            String lifecycleState,
            String primaryTimeframe,
            Long featureSchemaVersionId,
            StrategyParameters parameters,
            String parametersError,
            Instant createdAt) {
    }

    public record StrategyView(
            long strategyId,
            String code,
            String name,
            String description,
            String setupFamily,
            String status,
            Instant retiredAt,
            String retiredReason,
            Instant createdAt,
            List<StrategyVersionView> versions) {
    }

    public record CreateStrategyRequest(
            String code,
            String name,
            String description,
            String setupFamily,
            String lifecycleState,
            String primaryTimeframe,
            Map<String, Object> parameters) {
    }

    public record AddStrategyVersionRequest(
            String lifecycleState, String primaryTimeframe, Map<String, Object> parameters) {
    }

    /** Resolves a specific version's parameters for a reproducible reference (e.g. a backtest). */
    public record ResolvedStrategy(long strategyVersionId, String code, int version, StrategyParameters parameters) {
    }

    public ResolvedStrategy resolveVersion(long strategyVersionId) {
        var version = repository.findVersion(strategyVersionId)
                .orElseThrow(() -> new CatalogNotFoundException("Strategy version " + strategyVersionId + " not found."));
        String code = repository.codeForStrategyId(version.strategyId());
        if (code == null) {
            throw new CatalogNotFoundException("Strategy for version " + strategyVersionId + " not found.");
        }
        try {
            return new ResolvedStrategy(strategyVersionId, code, version.version(),
                    parametersJson.read(version.parametersJson(), code, version.version()));
        } catch (RuntimeException failure) {
            // A version stored without complete parameters (e.g. a catalog placeholder) is not
            // runnable; surface a clear 422 rather than a 500.
            throw new CatalogValidationException("Strategy version " + strategyVersionId
                    + " has incomplete or invalid parameters and cannot be used.");
        }
    }

    public List<StrategyView> list(boolean includeRetired) {
        return repository.list(includeRetired).stream().map(this::view).toList();
    }

    public StrategyView get(String code) {
        return repository.find(code).map(this::view)
                .orElseThrow(() -> new CatalogNotFoundException("Strategy '" + code + "' not found."));
    }

    @Transactional
    public StrategyView create(CreateStrategyRequest request) {
        requireText(request.code(), "Strategy code is required.");
        requireText(request.name(), "Strategy name is required.");
        if (repository.codeExists(request.code())) {
            throw new CatalogValidationException("Strategy code '" + request.code() + "' already exists.");
        }
        String lifecycle = lifecycle(request.lifecycleState());
        long strategyId = repository.insertStrategy(
                request.code(), request.name(), request.description(), request.setupFamily());
        appendVersion(strategyId, request.code(), lifecycle, request.primaryTimeframe(), request.parameters(), 1);
        return get(request.code());
    }

    @Transactional
    public StrategyView addVersion(String code, AddStrategyVersionRequest request) {
        var strategy = repository.find(code)
                .orElseThrow(() -> new CatalogNotFoundException("Strategy '" + code + "' not found."));
        if ("RETIRED".equals(strategy.status())) {
            throw new CatalogValidationException("Strategy '" + code + "' is retired; restore it before adding versions.");
        }
        int version = repository.nextVersion(strategy.strategyId());
        appendVersion(strategy.strategyId(), code, lifecycle(request.lifecycleState()), request.primaryTimeframe(),
                request.parameters(), version);
        return get(code);
    }

    @Transactional
    public StrategyView setStatus(String code, String status, String reason) {
        if (!repository.codeExists(code)) {
            throw new CatalogNotFoundException("Strategy '" + code + "' not found.");
        }
        repository.setStatus(code, status, reason);
        return get(code);
    }

    private void appendVersion(
            long strategyId, String code, String lifecycle, String primaryTimeframe, Map<String, Object> parameters,
            int version) {
        StrategyParameters resolved = validateParameters(parameters, code, version);
        Long timeframeId = repository.timeframeId(primaryTimeframe == null || primaryTimeframe.isBlank()
                ? "M5" : primaryTimeframe);
        if (timeframeId == null) {
            throw new CatalogValidationException("Unknown timeframe '" + primaryTimeframe + "'.");
        }
        repository.insertVersion(
                strategyId, version, lifecycle, timeframeId, repository.latestFeatureSchemaVersionId(),
                parametersJson.write(resolved));
    }

    private StrategyParameters validateParameters(Map<String, Object> parameters, String code, int version) {
        try {
            return parametersJson.read(parameters, code, version);
        } catch (RuntimeException failure) {
            throw new CatalogValidationException(
                    "Invalid strategy parameters: " + (failure.getMessage() == null ? "validation failed" : failure.getMessage()));
        }
    }

    private String lifecycle(String requested) {
        String state = requested == null || requested.isBlank() ? "RESEARCH" : requested.trim().toUpperCase();
        if (!LIFECYCLE_STATES.contains(state)) {
            throw new CatalogValidationException("Unknown lifecycle state '" + requested + "'.");
        }
        if ("RETIRED".equals(state)) {
            throw new CatalogValidationException("Use the retire action instead of a RETIRED version.");
        }
        return state;
    }

    private StrategyView view(StrategyCatalogRepository.StrategyRecord record) {
        List<StrategyVersionView> versions = record.versions().stream().map(version -> {
            StrategyParameters parsed = null;
            String error = null;
            try {
                parsed = parametersJson.read(version.parametersJson(), record.code(), version.version());
            } catch (RuntimeException failure) {
                error = failure.getMessage();
            }
            return new StrategyVersionView(
                    version.strategyVersionId(), version.version(), version.lifecycleState(),
                    version.primaryTimeframeCode(), version.featureSchemaVersionId(), parsed, error,
                    version.createdAt());
        }).toList();
        return new StrategyView(
                record.strategyId(), record.code(), record.name(), record.description(), record.setupFamily(),
                record.status(), record.retiredAt(), record.retiredReason(), record.createdAt(), versions);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CatalogValidationException(message);
        }
    }
}
