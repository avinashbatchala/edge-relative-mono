package com.edgerelative.application.tradeplan.api;

import com.edgerelative.application.tradeplan.application.TradePlanRow;
import com.edgerelative.application.tradeplan.application.TradePlanService;
import com.edgerelative.application.tradeplan.application.TradePlanTrustPort;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read and idempotent-creation contracts for immutable trade plans. There is no endpoint accepting
 * client-supplied approved quantity/risk: plan creation only happens inside the risk-approval
 * transaction. {@code ensure} returns an existing plan or 404 — it never fabricates approval.
 */
@RestController
@RequestMapping("/api/v1/trade-plans")
public class TradePlanController {

    private final TradePlanService service;
    private final TradePlanTrustPort trustPort;

    public TradePlanController(TradePlanService service, TradePlanTrustPort trustPort) {
        this.service = service;
        this.trustPort = trustPort;
    }

    @GetMapping("/{planKey}")
    public ResponseEntity<TradePlanResponse> byKey(@PathVariable String planKey) {
        return service.byKey(planKey)
                .map(row -> ResponseEntity.ok(respond(row)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-decision/{decisionKey}")
    public ResponseEntity<TradePlanResponse> byDecision(@PathVariable String decisionKey) {
        return service.byDecision(decisionKey)
                .map(row -> ResponseEntity.ok(respond(row)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-setup/{setupObservationId}")
    public List<TradePlanResponse> bySetup(@PathVariable long setupObservationId) {
        return service.bySetup(setupObservationId).stream().map(this::respond).toList();
    }

    @GetMapping("/{planKey}/eligibility")
    public ResponseEntity<TradePlanResponse.Eligibility> eligibility(@PathVariable String planKey) {
        return service.byKey(planKey)
                .map(row -> ResponseEntity.ok(respond(row).eligibility()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Idempotent creation from an approved decision; returns the existing plan or 404. */
    @PostMapping("/by-decision/{decisionKey}/ensure")
    public ResponseEntity<TradePlanResponse> ensure(@PathVariable String decisionKey) {
        return service.byDecision(decisionKey)
                .map(row -> ResponseEntity.ok(respond(row)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private TradePlanResponse respond(TradePlanRow row) {
        return TradePlanResponse.from(row, service.eligibility(row, trustPort.requiredInputsFresh(row)));
    }
}
