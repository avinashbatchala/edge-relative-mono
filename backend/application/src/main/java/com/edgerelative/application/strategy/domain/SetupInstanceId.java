package com.edgerelative.application.strategy.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stable identity of a setup instance, derived deterministically from its origin (strategy,
 * instrument, direction, family, session and originating structure). Distinct from an individual
 * setup observation: a transition produces a new observation referencing the same instance.
 *
 * <p>Identity is derived, never random, so replay reconstructs the same instance.
 */
public record SetupInstanceId(UUID value) {

    public SetupInstanceId {
        if (value == null) {
            throw new IllegalArgumentException("setup instance id is required");
        }
    }

    public static SetupInstanceId derive(
            String strategyVersion,
            long instrumentId,
            Direction direction,
            SetupFamily family,
            LocalDate tradingDate,
            String originatingStructure,
            Instant originTimestamp) {
        String seed = String.join(
                "|",
                strategyVersion,
                Long.toString(instrumentId),
                direction.name(),
                family.name(),
                tradingDate == null ? "" : tradingDate.toString(),
                originatingStructure == null ? "" : originatingStructure,
                originTimestamp == null ? "" : originTimestamp.toString());
        return new SetupInstanceId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }
}
