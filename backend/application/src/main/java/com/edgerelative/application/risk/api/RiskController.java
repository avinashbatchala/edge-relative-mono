package com.edgerelative.application.risk.api;

import com.edgerelative.application.risk.application.RiskApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral risk API. {@code preview} is non-authorizing and writes nothing; {@code approve}
 * persists immutable evidence and reserves capacity atomically. Neither creates an order or trade.
 */
@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskApplicationService service;

    public RiskController(RiskApplicationService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public RiskDecisionResponse preview(@RequestBody RiskCandidateRequest request) {
        return RiskDecisionResponse.from(service.preview(request.toCandidate()));
    }

    @PostMapping("/approve")
    public RiskDecisionResponse approve(@RequestBody RiskCandidateRequest request) {
        return RiskDecisionResponse.from(service.approve(request.toCandidate()));
    }
}
