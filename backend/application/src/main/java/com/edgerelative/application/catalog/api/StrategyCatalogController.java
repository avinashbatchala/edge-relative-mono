package com.edgerelative.application.catalog.api;

import com.edgerelative.application.catalog.application.StrategyCatalogService;
import com.edgerelative.application.catalog.application.StrategyCatalogService.AddStrategyVersionRequest;
import com.edgerelative.application.catalog.application.StrategyCatalogService.CreateStrategyRequest;
import com.edgerelative.application.catalog.application.StrategyCatalogService.StrategyView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Strategy catalog: create strategies, append immutable versions, retire/restore. */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyCatalogController {

    private final StrategyCatalogService service;

    public StrategyCatalogController(StrategyCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<StrategyView> list(@RequestParam(defaultValue = "false") boolean includeRetired) {
        return service.list(includeRetired);
    }

    /** A valid starting parameter document (research preset) for the editor. */
    @GetMapping("/templates/parameters")
    public com.edgerelative.application.strategy.domain.StrategyParameters templateParameters() {
        return com.edgerelative.application.backtest.application.BacktestPresets
                .strategy(com.edgerelative.application.backtest.application.BacktestPresets.STRATEGY_RS_RESEARCH)
                .orElseThrow();
    }

    @GetMapping("/{code}")
    public StrategyView get(@PathVariable String code) {
        return service.get(code);
    }

    @PostMapping
    public StrategyView create(@RequestBody CreateStrategyRequest request) {
        return service.create(request);
    }

    @PostMapping("/{code}/versions")
    public StrategyView addVersion(@PathVariable String code, @RequestBody AddStrategyVersionRequest request) {
        return service.addVersion(code, request);
    }

    @PostMapping("/{code}/retire")
    public StrategyView retire(@PathVariable String code, @RequestBody(required = false) RetireRequest request) {
        return service.setStatus(code, "RETIRED", request == null ? null : request.reason());
    }

    @PostMapping("/{code}/restore")
    public StrategyView restore(@PathVariable String code) {
        return service.setStatus(code, "ACTIVE", null);
    }

    public record RetireRequest(String reason) {
    }
}
