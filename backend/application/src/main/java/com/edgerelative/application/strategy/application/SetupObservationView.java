package com.edgerelative.application.strategy.application;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for a persisted setup observation. Keeps persistence records out of the domain. */
public record SetupObservationView(
        Instant observedAt,
        String direction,
        String setupStatus,
        String entryPattern,
        BigDecimal structuralInvalidation,
        Double structuralRR,
        Integer strategyVersion,
        String explanationJson) {
}
