package com.edgerelative.application.ml.api;

import com.edgerelative.application.ml.application.MlModelRegistryService;
import com.edgerelative.application.ml.application.MlModelRegistryService.ModelVersionView;
import com.edgerelative.application.ml.application.MlModelRegistryService.RegisterRequest;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Model registry API. Registration is append-only; a registered version's artifact is immutable.
 */
@RestController
@RequestMapping("/api/v1/ml/models")
public class MlModelController {

    private final MlModelRegistryService service;

    public MlModelController(MlModelRegistryService service) {
        this.service = service;
    }

    public record LifecycleRequest(String lifecycleState) {
    }

    @PostMapping
    public ModelVersionView register(@RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @GetMapping
    public List<ModelVersionView> list() {
        return service.list();
    }

    @GetMapping("/{modelVersionId}")
    public ModelVersionView get(@PathVariable long modelVersionId) {
        return service.find(modelVersionId);
    }

    @PostMapping("/{modelVersionId}/lifecycle")
    public Map<String, Object> lifecycle(@PathVariable long modelVersionId, @RequestBody LifecycleRequest request) {
        service.setLifecycle(modelVersionId, request.lifecycleState());
        return Map.of("ok", true);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalid(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}
