package com.edgerelative.application.strategy.api;

import com.edgerelative.application.strategy.application.SetupObservationView;
import java.math.BigDecimal;
import java.time.Instant;

/** Broker-neutral read model for a persisted setup observation. */
public record SetupObservationResponse(
        Instant observedAt,
        String direction,
        String setupStatus,
        String entryPattern,
        BigDecimal structuralInvalidation,
        Double structuralRR,
        Integer strategyVersion,
        String explanation) {

    public static SetupObservationResponse from(SetupObservationView view) {
        return new SetupObservationResponse(
                view.observedAt(),
                view.direction(),
                view.setupStatus(),
                view.entryPattern(),
                view.structuralInvalidation(),
                view.structuralRR(),
                view.strategyVersion(),
                view.explanationJson());
    }
}
