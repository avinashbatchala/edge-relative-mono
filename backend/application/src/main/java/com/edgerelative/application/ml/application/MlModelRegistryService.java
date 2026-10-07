package com.edgerelative.application.ml.application;

import com.edgerelative.application.ml.persistence.MlModelRegistryRepository;
import com.edgerelative.application.ml.persistence.MlModelRegistryRepository.ModelVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Model registry: registration of trained model versions and lifecycle management. Registration is
 * append-only (a new version per training run); a registered version is never mutated except for its
 * lifecycle state. The artifact is referenced by URI + checksum and loaded by the scorer.
 */
@Service
public class MlModelRegistryService {

    private static final Set<String> LIFECYCLE =
            Set.of("EXPERIMENT", "VALIDATED", "SHADOW", "PAPER", "LIVE_LIMITED", "PRODUCTION", "RETIRED");

    private final MlModelRegistryRepository repository;
    private final JsonMapper json;

    public MlModelRegistryService(MlModelRegistryRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public record RegisterRequest(
            String modelCode,
            String modelName,
            String description,
            String algorithm,
            String lifecycleState,
            String artifactUri,
            String artifactChecksum,
            LocalDate trainingPeriodStart,
            LocalDate trainingPeriodEnd,
            LocalDate validationPeriodStart,
            LocalDate validationPeriodEnd,
            LocalDate testPeriodStart,
            LocalDate testPeriodEnd,
            Map<String, Object> metrics,
            List<String> strategyCompatibility,
            String codeVersion) {
    }

    public record ModelVersionView(
            long modelVersionId,
            String modelCode,
            String modelName,
            int version,
            String lifecycleState,
            String algorithm,
            String artifactUri,
            String artifactChecksum,
            LocalDate trainingPeriodStart,
            LocalDate trainingPeriodEnd,
            LocalDate validationPeriodStart,
            LocalDate validationPeriodEnd,
            LocalDate testPeriodStart,
            LocalDate testPeriodEnd,
            JsonNode metrics,
            Instant createdAt) {
    }

    public ModelVersionView register(RegisterRequest request) {
        if (request.modelCode() == null || request.modelCode().isBlank()) {
            throw new IllegalArgumentException("modelCode is required");
        }
        if (request.artifactUri() == null || request.artifactUri().isBlank()) {
            throw new IllegalArgumentException("artifactUri is required");
        }
        String lifecycle = request.lifecycleState() == null ? "EXPERIMENT" : request.lifecycleState();
        if (!LIFECYCLE.contains(lifecycle)) {
            throw new IllegalArgumentException("invalid lifecycleState: " + lifecycle);
        }
        String algorithm = request.algorithm() == null ? "LIGHTGBM_RANKER" : request.algorithm();
        long modelId = repository.ensureModel(request.modelCode(), request.modelName(), request.description());
        int version = repository.nextVersion(modelId);
        long modelVersionId = repository.insertVersion(
                modelId, version, lifecycle, algorithm, request.artifactUri(), request.artifactChecksum(),
                request.trainingPeriodStart(), request.trainingPeriodEnd(),
                request.validationPeriodStart(), request.validationPeriodEnd(),
                request.testPeriodStart(), request.testPeriodEnd(),
                toJson(request.metrics()), toJson(request.strategyCompatibility()), request.codeVersion());
        return view(repository.find(modelVersionId).orElseThrow());
    }

    public List<ModelVersionView> list() {
        return repository.list().stream().map(this::view).toList();
    }

    public ModelVersionView find(long modelVersionId) {
        return repository.find(modelVersionId).map(this::view).orElse(null);
    }

    public void setLifecycle(long modelVersionId, String lifecycleState) {
        if (!LIFECYCLE.contains(lifecycleState)) {
            throw new IllegalArgumentException("invalid lifecycleState: " + lifecycleState);
        }
        repository.updateLifecycle(modelVersionId, lifecycleState);
    }

    private ModelVersionView view(ModelVersion model) {
        return new ModelVersionView(
                model.modelVersionId(), model.code(), model.name(), model.version(),
                model.lifecycleState(), model.algorithm(), model.artifactUri(), model.artifactChecksum(),
                model.trainingPeriodStart(), model.trainingPeriodEnd(),
                model.validationPeriodStart(), model.validationPeriodEnd(),
                model.testPeriodStart(), model.testPeriodEnd(),
                parse(model.metricsJson()), model.createdAt());
    }

    private JsonNode parse(String value) {
        return value == null || value.isBlank() ? json.createObjectNode() : json.readTree(value);
    }

    private String toJson(Object value) {
        return json.writeValueAsString(value == null ? Map.of() : value);
    }
}
