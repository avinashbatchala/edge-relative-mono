package com.edgerelative.broker.groww.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerTransientException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.AdvancingGrowwWaiter;
import com.edgerelative.broker.groww.support.GrowwPropertiesBuilder;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class GrowwRetryPolicyTest {

    private final GrowwProperties properties = retryProperties();
    private final MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
    private final GrowwRetryPolicy policy = new GrowwRetryPolicy(
            properties,
            clock,
            RandomGenerator.getDefault(),
            new AdvancingGrowwWaiter(clock),
            new GrowwMetrics(new SimpleMeterRegistry()));

    @Test
    void retriesOnlyRetrySafeOperationsWithRetryableFailures() {
        BrokerTransientException transientFailure =
                new BrokerTransientException("boom", "groww", "QUOTE", "/v1/live-data/quote", 503, null);
        BrokerAuthenticationException authFailure =
                new BrokerAuthenticationException("nope", "groww", "QUOTE", "/v1/live-data/quote", 401, null);

        assertThat(policy.shouldRetry(GrowwOperation.QUOTE, transientFailure, 1, clock.instant()))
                .isTrue();
        assertThat(policy.shouldRetry(GrowwOperation.PLACE_ORDER, transientFailure, 1, clock.instant()))
                .isFalse();
        assertThat(policy.shouldRetry(GrowwOperation.QUOTE, authFailure, 1, clock.instant()))
                .isFalse();
        assertThat(policy.shouldRetry(GrowwOperation.QUOTE, transientFailure, 3, clock.instant()))
                .isFalse();
    }

    @Test
    void stopsRetryingOnceTheTimeBudgetIsExhausted() {
        BrokerTransientException failure =
                new BrokerTransientException("boom", "groww", "QUOTE", "/v1/live-data/quote", 503, null);
        Instant first = clock.instant();
        clock.advance(Duration.ofSeconds(11));
        assertThat(policy.shouldRetry(GrowwOperation.QUOTE, failure, 1, first)).isFalse();
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(policy.backoff(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.backoff(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.backoff(3)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.backoff(4)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.backoff(5)).isEqualTo(Duration.ofMillis(1000));
    }

    private static GrowwProperties retryProperties() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setInitialBackoff(Duration.ofMillis(100));
        properties.getRetry().setMultiplier(2.0);
        properties.getRetry().setMaxBackoff(Duration.ofMillis(1000));
        properties.getRetry().setBudget(Duration.ofSeconds(10));
        properties.getRetry().setJitter(0.0);
        return properties;
    }
}
