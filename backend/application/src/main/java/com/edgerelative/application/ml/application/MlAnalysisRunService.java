package com.edgerelative.application.ml.application;

import com.edgerelative.application.ml.persistence.MlAnalysisRunRepository;
import com.edgerelative.application.ml.persistence.MlAnalysisRunRepository.Run;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ML analysis-run orchestration. The UI enqueues an immutable configuration; the Python research
 * runner claims it, trains, and writes back the registered model version. Nothing here scores a
 * setup or grants authority.
 */
@Service
public class MlAnalysisRunService {

    private static final Set<String> SETUP_TIMEFRAMES = Set.of("M5", "M15", "M30");

    private final MlAnalysisRunRepository repository;
    private final JsonMapper json;

    public MlAnalysisRunService(MlAnalysisRunRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public record CreateRequest(Map<String, Object> config, String requestedBy) {
    }

    public record RunView(
            long id,
            String key,
            String status,
            String requestedBy,
            JsonNode config,
            JsonNode progress,
            JsonNode metrics,
            Long modelVersionId,
            String error,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {
    }

    public RunView create(CreateRequest request) {
        Map<String, Object> config = request.config() == null ? Map.of() : request.config();
        validate(config);
        String key = repository.enqueue(toJson(config), request.requestedBy());
        return findRequired(key);
    }

    public RunView find(String key) {
        return repository.find(key).map(this::toView).orElse(null);
    }

    private RunView findRequired(String key) {
        return repository.find(key).map(this::toView)
                .orElseThrow(() -> new IllegalStateException("enqueued run not found: " + key));
    }

    public List<RunView> list(int limit) {
        return repository.list(Math.min(Math.max(limit, 1), 200)).stream().map(this::toView).toList();
    }

    /** Atomically claims the oldest queued run for a worker; null when the queue is empty. */
    public RunView claim(String leaseOwner, long leaseSeconds) {
        return repository.claimNext(leaseOwner, leaseSeconds).map(this::toView).orElse(null);
    }

    public void progress(String key, JsonNode progress) {
        repository.updateProgress(key, toJson(progress));
    }

    public void complete(String key, long modelVersionId, JsonNode metrics) {
        repository.complete(key, modelVersionId, toJson(metrics));
    }

    public void fail(String key, String error) {
        repository.fail(key, error);
    }

    public boolean cancel(String key) {
        return repository.cancel(key);
    }

    private void validate(Map<String, Object> config) {
        Object symbols = config.get("symbols");
        if (!(symbols instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("config.symbols must be a non-empty list");
        }
        Object timeframe = config.get("setupTimeframe");
        if (timeframe == null || !SETUP_TIMEFRAMES.contains(String.valueOf(timeframe))) {
            throw new IllegalArgumentException("config.setupTimeframe must be one of " + SETUP_TIMEFRAMES);
        }
        LocalDate start = parseDate(config.get("startDate"), "config.startDate");
        LocalDate end = parseDate(config.get("endDate"), "config.endDate");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("config.startDate must be before config.endDate");
        }
    }

    private static LocalDate parseDate(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be an ISO date");
        }
    }

    private RunView toView(Run run) {
        return new RunView(
                run.id(), run.key(), run.status(), run.requestedBy(),
                parse(run.configJson()), parse(run.progressJson()), parse(run.metricsJson()),
                run.modelVersionId(), run.error(), run.createdAt(), run.startedAt(), run.completedAt());
    }

    private JsonNode parse(String value) {
        return value == null || value.isBlank() ? json.createObjectNode() : json.readTree(value);
    }

    private String toJson(Object value) {
        return json.writeValueAsString(value == null ? Map.of() : value);
    }
}
