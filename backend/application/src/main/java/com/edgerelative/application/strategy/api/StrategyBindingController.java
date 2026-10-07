package com.edgerelative.application.strategy.api;

import com.edgerelative.application.strategy.application.StrategyBindingService;
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
 * Controlled per-instrument strategy binding API. Writes are validated and append-only; the live
 * strategy resolves the binding effective at the decision date. There is no execution surface here.
 */
@RestController
@RequestMapping("/api/v1/strategy-bindings")
public class StrategyBindingController {

    private final StrategyBindingService service;

    public StrategyBindingController(StrategyBindingService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody StrategyBindingService.CreateRequest request) {
        long bindingId = service.create(request);
        return Map.of("bindingId", bindingId);
    }

    @GetMapping("/{instrumentId}/effective")
    public ResponseEntity<StrategyBindingService.BindingView> effective(
            @PathVariable long instrumentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.effective(instrumentId, asOf)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{instrumentId}")
    public List<StrategyBindingService.BindingView> list(@PathVariable long instrumentId) {
        return service.list(instrumentId);
    }

    /** Invalid parameters (bad value/enum) or an overlapping effective window both fail cleanly. */
    @org.springframework.web.bind.annotation.ExceptionHandler({
        IllegalArgumentException.class,
        org.springframework.dao.DataIntegrityViolationException.class
    })
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public Map<String, Object> invalid(RuntimeException exception) {
        return Map.of("error", "Binding rejected: invalid parameters or overlapping effective window.");
    }
}
