package com.edgerelative.broker.groww.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class GrowwCircuitBreakerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
    private final GrowwCircuitBreaker breaker =
            new GrowwCircuitBreaker(clock, new GrowwMetrics(new SimpleMeterRegistry()), 3, Duration.ofSeconds(30), 1);

    @Test
    void opensAfterThresholdAndHalfOpensAfterOpenDuration() {
        assertThat(breaker.allowCall()).isTrue();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(GrowwCircuitBreaker.State.CLOSED);

        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(GrowwCircuitBreaker.State.OPEN);
        assertThat(breaker.isOpen()).isTrue();
        assertThat(breaker.allowCall()).isFalse();

        clock.advance(Duration.ofSeconds(30));
        assertThat(breaker.state()).isEqualTo(GrowwCircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.allowCall()).isTrue();
        // bounded half-open probes: only one concurrent probe is permitted.
        assertThat(breaker.allowCall()).isFalse();
    }

    @Test
    void halfOpenFailureReopensAndSuccessCloses() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        clock.advance(Duration.ofSeconds(30));
        assertThat(breaker.allowCall()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(GrowwCircuitBreaker.State.OPEN);

        clock.advance(Duration.ofSeconds(30));
        assertThat(breaker.allowCall()).isTrue();
        breaker.recordSuccess();
        assertThat(breaker.state()).isEqualTo(GrowwCircuitBreaker.State.CLOSED);
        assertThat(breaker.isOpen()).isFalse();
    }
}
