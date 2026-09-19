package com.edgerelative.broker.groww.health;

import com.edgerelative.broker.groww.resilience.GrowwBrokerState;
import com.edgerelative.broker.groww.resilience.GrowwCircuitBreaker;
import com.edgerelative.broker.groww.resilience.GrowwCooldownManager;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the most recent broker interaction outcomes and derives health.
 *
 * <p>Health is passive: it never consumes Groww quota. Auth failures dominate; then cooldown; then an
 * open circuit is reported as degraded/unavailable.
 */
public class GrowwHealthMonitor implements GrowwHealthStateProvider {

    private final Clock clock;
    private final GrowwCooldownManager cooldown;
    private final GrowwCircuitBreaker circuitBreaker;
    private final AtomicReference<GrowwHealth> lastAuthFailure = new AtomicReference<>();

    public GrowwHealthMonitor(
            Clock clock, GrowwCooldownManager cooldown, GrowwCircuitBreaker circuitBreaker) {
        this.clock = clock;
        this.cooldown = cooldown;
        this.circuitBreaker = circuitBreaker;
    }

    public void onSuccess() {
        lastAuthFailure.set(null);
    }

    public void onAuthenticationFailure(String detail) {
        lastAuthFailure.set(new GrowwHealth(GrowwBrokerState.AUTH_FAILURE, detail, clock.instant()));
    }

    public void onUnavailable(String detail) {
        lastAuthFailure.set(new GrowwHealth(GrowwBrokerState.UNAVAILABLE, detail, clock.instant()));
    }

    @Override
    public GrowwHealth health() {
        GrowwHealth failure = lastAuthFailure.get();
        if (failure != null) {
            return failure;
        }
        GrowwBrokerState cooldownState = cooldown.state();
        if (cooldownState != GrowwBrokerState.ACTIVE) {
            return new GrowwHealth(
                    cooldownState, "Cooldown remaining " + cooldown.remaining(), clock.instant());
        }
        if (circuitBreaker.isOpen()) {
            return new GrowwHealth(GrowwBrokerState.DEGRADED, "Circuit breaker open", clock.instant());
        }
        if (circuitBreaker.state() == GrowwCircuitBreaker.State.HALF_OPEN) {
            return new GrowwHealth(GrowwBrokerState.DEGRADED, "Circuit breaker half-open", clock.instant());
        }
        return new GrowwHealth(GrowwBrokerState.ACTIVE, "Nominal", clock.instant());
    }
}
