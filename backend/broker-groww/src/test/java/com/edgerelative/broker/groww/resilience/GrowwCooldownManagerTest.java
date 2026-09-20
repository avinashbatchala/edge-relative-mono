package com.edgerelative.broker.groww.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.AdvancingGrowwWaiter;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class GrowwCooldownManagerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
    private final GrowwCooldownManager manager = new GrowwCooldownManager(
            clock,
            new AdvancingGrowwWaiter(clock),
            new GrowwMetrics(new SimpleMeterRegistry()),
            Duration.ofSeconds(5));

    @Test
    void activatesExtendsAndClearsCooldown() {
        assertThat(manager.state()).isEqualTo(GrowwBrokerState.ACTIVE);

        manager.activate(GrowwRateLimitCategory.LIVE_DATA, Duration.ofSeconds(10));
        assertThat(manager.isCoolingDown()).isTrue();
        assertThat(manager.state()).isEqualTo(GrowwBrokerState.COOLING_DOWN);
        assertThat(manager.remaining()).isEqualTo(Duration.ofSeconds(10));

        // A concurrent 429 with a shorter retry must not shorten the existing cooldown.
        manager.activate(GrowwRateLimitCategory.LIVE_DATA, Duration.ofSeconds(2));
        assertThat(manager.remaining()).isEqualTo(Duration.ofSeconds(10));

        manager.activate(GrowwRateLimitCategory.LIVE_DATA, Duration.ofSeconds(20));
        assertThat(manager.remaining()).isEqualTo(Duration.ofSeconds(20));

        manager.clear();
        assertThat(manager.state()).isEqualTo(GrowwBrokerState.ACTIVE);
    }

    @Test
    void usesDefaultDurationWhenNoRetryAfterIsSupplied() {
        manager.activate(GrowwRateLimitCategory.ORDERS, null);
        assertThat(manager.remaining()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void authenticationCooldownIsReportedAsRateLimited() {
        manager.activate(GrowwRateLimitCategory.AUTHENTICATION, Duration.ofSeconds(3));
        assertThat(manager.state()).isEqualTo(GrowwBrokerState.RATE_LIMITED);
    }

    @Test
    void awaitExpiryAdvancesPastTheCooldown() {
        manager.activate(GrowwRateLimitCategory.LIVE_DATA, Duration.ofSeconds(10));
        manager.awaitExpiry();
        assertThat(manager.isCoolingDown()).isFalse();
    }
}
