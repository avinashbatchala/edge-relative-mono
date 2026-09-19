package com.edgerelative.application.broker;

import com.edgerelative.broker.groww.health.GrowwHealth;
import com.edgerelative.broker.groww.health.GrowwHealthStateProvider;
import com.edgerelative.broker.groww.resilience.GrowwBrokerState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Passive Groww health. Derived from recent interaction state, so it never spends API quota probing.
 */
@Component
public class GrowwHealthIndicator implements HealthIndicator {

    private final GrowwHealthStateProvider healthStateProvider;

    public GrowwHealthIndicator(GrowwHealthStateProvider healthStateProvider) {
        this.healthStateProvider = healthStateProvider;
    }

    @Override
    public Health health() {
        GrowwHealth health = healthStateProvider.health();
        Health.Builder builder = health.isHealthy() ? Health.up() : statusFor(health.state());
        return builder.withDetail("state", health.state().name())
                .withDetail("detail", health.detail())
                .withDetail("observedAt", health.observedAt().toString())
                .build();
    }

    private static Health.Builder statusFor(GrowwBrokerState state) {
        return switch (state) {
            case AUTH_FAILURE, UNAVAILABLE -> Health.down();
            case RATE_LIMITED, COOLING_DOWN, DEGRADED -> Health.status("DEGRADED");
            case ACTIVE -> Health.up();
        };
    }
}
