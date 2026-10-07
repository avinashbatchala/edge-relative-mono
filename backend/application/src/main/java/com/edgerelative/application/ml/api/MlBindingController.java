package com.edgerelative.application.ml.api;

import com.edgerelative.application.ml.application.MlBindingService;
import com.edgerelative.application.ml.application.MlBindingService.BindingView;
import com.edgerelative.application.ml.application.MlBindingService.CreateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Effective-dated per-instrument ML model bindings. The live/backtest path resolves the binding
 * effective at the decision date, keeping point-in-time scoring reproducible.
 */
@RestController
@RequestMapping("/api/v1/ml/bindings")
public class MlBindingController {

    private final MlBindingService service;

    public MlBindingController(MlBindingService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateRequest request) {
        return Map.of("bindingId", service.create(request));
    }

    @GetMapping("/{instrumentId}/effective")
    public ResponseEntity<BindingView> effective(
            @PathVariable long instrumentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.effective(instrumentId, asOf)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{instrumentId}")
    public List<BindingView> list(@PathVariable long instrumentId) {
        return service.list(instrumentId);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
        IllegalArgumentException.class,
        org.springframework.dao.DataIntegrityViolationException.class
    })
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public Map<String, Object> invalid(RuntimeException exception) {
        return Map.of("error", "Binding rejected: invalid values or an overlapping effective window.");
    }
}
