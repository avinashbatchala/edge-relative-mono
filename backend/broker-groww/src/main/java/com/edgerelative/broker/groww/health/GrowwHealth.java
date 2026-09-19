package com.edgerelative.broker.groww.health;

import com.edgerelative.broker.groww.resilience.GrowwBrokerState;
import java.time.Instant;

/** Broker health derived from observed interaction state, not from aggressive probing. */
public record GrowwHealth(GrowwBrokerState state, String detail, Instant observedAt) {

    public boolean isHealthy() {
        return state == GrowwBrokerState.ACTIVE;
    }
}
