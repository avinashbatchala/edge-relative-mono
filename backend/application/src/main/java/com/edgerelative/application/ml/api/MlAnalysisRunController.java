package com.edgerelative.application.ml.api;

import com.edgerelative.application.ml.application.MlAnalysisRunService;
import com.edgerelative.application.ml.application.MlAnalysisRunService.CreateRequest;
import com.edgerelative.application.ml.application.MlAnalysisRunService.RunView;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * ML analysis-run queue API. The operator enqueues a configuration; the Python research runner
 * claims it, trains, and reports back. There is no scoring or trading authority here.
 */
@RestController
@RequestMapping("/api/v1/ml/analysis-runs")
public class MlAnalysisRunController {

    private final MlAnalysisRunService service;

    public MlAnalysisRunController(MlAnalysisRunService service) {
        this.service = service;
    }

    public record ClaimRequest(String leaseOwner, Long leaseSeconds) {
    }

    public record CompleteRequest(long modelVersionId, JsonNode metrics) {
    }

    public record FailRequest(String error) {
    }

    @PostMapping
    public RunView create(@RequestBody CreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<RunView> list(@RequestParam(defaultValue = "50") int limit) {
        return service.list(limit);
    }

    @GetMapping("/{key}")
    public RunView get(@PathVariable String key) {
        return service.find(key);
    }

    @PostMapping("/{key}/cancel")
    public Map<String, Object> cancel(@PathVariable String key) {
        return Map.of("cancelled", service.cancel(key));
    }

    /** Runner-only: atomically claims the oldest queued run. Returns 204 when the queue is empty. */
    @PostMapping("/claim")
    public org.springframework.http.ResponseEntity<RunView> claim(@RequestBody ClaimRequest request) {
        long lease = request.leaseSeconds() == null ? 3600L : request.leaseSeconds();
        RunView run = service.claim(request.leaseOwner() == null ? "research-runner" : request.leaseOwner(), lease);
        return run == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(run);
    }

    @PostMapping("/{key}/progress")
    public Map<String, Object> progress(@PathVariable String key, @RequestBody JsonNode progress) {
        service.progress(key, progress);
        return Map.of("ok", true);
    }

    @PostMapping("/{key}/complete")
    public Map<String, Object> complete(@PathVariable String key, @RequestBody CompleteRequest request) {
        service.complete(key, request.modelVersionId(), request.metrics());
        return Map.of("ok", true);
    }

    @PostMapping("/{key}/fail")
    public Map<String, Object> fail(@PathVariable String key, @RequestBody FailRequest request) {
        service.fail(key, request.error() == null ? "unknown failure" : request.error());
        return Map.of("ok", true);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalid(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage(), "code", "ML_ANALYSIS_INVALID");
    }
}
