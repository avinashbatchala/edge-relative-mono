package com.edgerelative.application.catalog.api;

import com.edgerelative.application.catalog.application.RiskPolicyCatalogService;
import com.edgerelative.application.catalog.application.RiskPolicyCatalogService.AddRiskPolicyVersionRequest;
import com.edgerelative.application.catalog.application.RiskPolicyCatalogService.CreateRiskPolicyRequest;
import com.edgerelative.application.catalog.application.RiskPolicyCatalogService.RiskPolicyView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Risk-policy catalog: create policies, append immutable versions, retire/restore. */
@RestController
@RequestMapping("/api/v1/risk-policies")
public class RiskPolicyCatalogController {

    private final RiskPolicyCatalogService service;

    public RiskPolicyCatalogController(RiskPolicyCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<RiskPolicyView> list(@RequestParam(defaultValue = "false") boolean includeRetired) {
        return service.list(includeRetired);
    }

    /** A valid starting parameter document (research preset) for the editor. */
    @GetMapping("/templates/parameters")
    public com.edgerelative.application.risk.domain.RiskPolicy templateParameters() {
        return com.edgerelative.application.backtest.application.BacktestPresets
                .risk(com.edgerelative.application.backtest.application.BacktestPresets.RISK_RESEARCH_PERMISSIVE)
                .orElseThrow();
    }

    @GetMapping("/{code}")
    public RiskPolicyView get(@PathVariable String code) {
        return service.get(code);
    }

    @PostMapping
    public RiskPolicyView create(@RequestBody CreateRiskPolicyRequest request) {
        return service.create(request);
    }

    @PostMapping("/{code}/versions")
    public RiskPolicyView addVersion(
            @PathVariable String code, @RequestBody AddRiskPolicyVersionRequest request) {
        return service.addVersion(code, request);
    }

    @PostMapping("/{code}/retire")
    public RiskPolicyView retire(@PathVariable String code, @RequestBody(required = false) RetireRequest request) {
        return service.setStatus(code, "RETIRED", request == null ? null : request.reason());
    }

    @PostMapping("/{code}/restore")
    public RiskPolicyView restore(@PathVariable String code) {
        return service.setStatus(code, "ACTIVE", null);
    }

    public record RetireRequest(String reason) {
    }
}
