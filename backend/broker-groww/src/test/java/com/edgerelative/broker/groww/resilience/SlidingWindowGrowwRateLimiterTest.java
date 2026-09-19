package com.edgerelative.broker.groww.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.AdvancingGrowwWaiter;
import com.edgerelative.broker.groww.support.GrowwPropertiesBuilder;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlidingWindowGrowwRateLimiterTest {

    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void enforcesEveryWindowNotJustThePerSecondOne() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.setOperationTimeout(Duration.ofMinutes(5));
        properties.getRateLimits().setOrders(List.of(
                GrowwProperties.Window.of(Duration.ofSeconds(1), 2),
                GrowwProperties.Window.of(Duration.ofMinutes(1), 3)));
        MutableClock clock = new MutableClock(START);
        SlidingWindowGrowwRateLimiter limiter = new SlidingWindowGrowwRateLimiter(
                properties, clock, new AdvancingGrowwWaiter(clock), new GrowwMetrics(new SimpleMeterRegistry()));

        assertThat(limiter.acquire(GrowwRateLimitCategory.ORDERS).admittedAt()).isEqualTo(START);
        assertThat(limiter.acquire(GrowwRateLimitCategory.ORDERS).admittedAt()).isEqualTo(START);
        // per-second is full; per-minute still has room, so admission waits one second.
        assertThat(limiter.acquire(GrowwRateLimitCategory.ORDERS).admittedAt()).isEqualTo(START.plusSeconds(1));
        // now the per-minute window (limit 3) is full, so admission waits until the oldest entry expires.
        assertThat(limiter.acquire(GrowwRateLimitCategory.ORDERS).admittedAt()).isEqualTo(START.plusSeconds(60));
    }

    @Test
    void failsTypedWhenAdmissionExceedsTheOperationBudget() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.setOperationTimeout(Duration.ofSeconds(5));
        properties.getRateLimits().setOrders(List.of(
                GrowwProperties.Window.of(Duration.ofSeconds(1), 1),
                GrowwProperties.Window.of(Duration.ofMinutes(1), 1)));
        MutableClock clock = new MutableClock(START);
        SlidingWindowGrowwRateLimiter limiter = new SlidingWindowGrowwRateLimiter(
                properties, clock, new AdvancingGrowwWaiter(clock), new GrowwMetrics(new SimpleMeterRegistry()));

        limiter.acquire(GrowwRateLimitCategory.ORDERS);
        assertThatThrownBy(() -> limiter.acquire(GrowwRateLimitCategory.ORDERS))
                .isInstanceOf(BrokerRateLimitException.class)
                .satisfies(e -> assertThat(((BrokerRateLimitException) e).retryAfter()).isNotNull());
    }

    @Test
    void windowsAreIndependentPerCategory() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.getRateLimits().setOrders(List.of(GrowwProperties.Window.of(Duration.ofSeconds(1), 1)));
        properties.getRateLimits().setLiveData(List.of(GrowwProperties.Window.of(Duration.ofSeconds(1), 1)));
        MutableClock clock = new MutableClock(START);
        SlidingWindowGrowwRateLimiter limiter = new SlidingWindowGrowwRateLimiter(
                properties, clock, new AdvancingGrowwWaiter(clock), new GrowwMetrics(new SimpleMeterRegistry()));

        limiter.acquire(GrowwRateLimitCategory.ORDERS);
        // Live data has its own allowance and must not be blocked by the orders category.
        assertThat(limiter.acquire(GrowwRateLimitCategory.LIVE_DATA).admittedAt()).isEqualTo(START);
    }
}
