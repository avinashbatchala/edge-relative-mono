package com.edgerelative.application.strategy.api;

import com.edgerelative.application.strategy.application.SetupEvaluationService;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral setup observation API. Exact, instrument-scoped lookups only; there is no setup
 * screener and no execution surface. A quiet setup is represented by its last persisted state.
 */
@RestController
@RequestMapping("/api/v1/setups")
public class SetupQueryController {

    private final SetupEvaluationService service;

    public SetupQueryController(SetupEvaluationService service) {
        this.service = service;
    }

    @GetMapping("/{instrumentId}")
    public List<SetupObservationResponse> latest(@PathVariable long instrumentId) {
        return service.observations(instrumentId).stream()
                .map(SetupObservationResponse::from)
                .toList();
    }

    /** On-demand evaluation for an instrument (operator/replay trigger), producing observations. */
    @PostMapping("/{instrumentId}/evaluate")
    public List<SetupEvaluationResultResponse> evaluate(
            @PathVariable long instrumentId,
            @RequestParam(required = false) Instant anchor) {
        return service.evaluate(instrumentId, anchor).stream()
                .map(SetupEvaluationResultResponse::from)
                .toList();
    }
}
