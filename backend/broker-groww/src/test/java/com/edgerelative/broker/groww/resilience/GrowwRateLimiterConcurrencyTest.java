package com.edgerelative.broker.groww.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.AdvancingGrowwWaiter;
import com.edgerelative.broker.groww.support.GrowwPropertiesBuilder;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GrowwRateLimiterConcurrencyTest {

    @Test
    void manyVirtualThreadCallersNeverViolateThePerSecondWindow() throws Exception {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.setOperationTimeout(Duration.ofHours(2));
        properties.getRateLimits().setLiveData(List.of(
                GrowwProperties.Window.of(Duration.ofSeconds(1), 10),
                GrowwProperties.Window.of(Duration.ofMinutes(1), 1000)));
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        GrowwRateLimiter limiter = new SlidingWindowGrowwRateLimiter(
                properties, clock, new AdvancingGrowwWaiter(clock), new GrowwMetrics(new SimpleMeterRegistry()));

        int callers = 120;
        List<Instant> admissions = Collections.synchronizedList(new ArrayList<>());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> admissions.add(limiter.acquire(GrowwRateLimitCategory.LIVE_DATA).admittedAt()));
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(admissions).hasSize(callers);
        List<Instant> sorted = new ArrayList<>(admissions);
        sorted.sort(Instant::compareTo);
        for (int i = 0; i < sorted.size(); i++) {
            Instant windowStart = sorted.get(i);
            Instant windowEnd = windowStart.plusSeconds(1);
            long inWindow = sorted.stream()
                    .filter(t -> !t.isBefore(windowStart) && t.isBefore(windowEnd))
                    .count();
            assertThat(inWindow).isLessThanOrEqualTo(10);
        }
    }
}
