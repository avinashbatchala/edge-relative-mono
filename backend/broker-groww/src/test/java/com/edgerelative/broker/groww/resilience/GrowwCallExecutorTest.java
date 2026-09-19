package com.edgerelative.broker.groww.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.api.error.BrokerTransientException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.health.GrowwHealthMonitor;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.AdvancingGrowwWaiter;
import com.edgerelative.broker.groww.support.CountingRateLimiter;
import com.edgerelative.broker.groww.support.GrowwPropertiesBuilder;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class GrowwCallExecutorTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
    private final AdvancingGrowwWaiter waiter = new AdvancingGrowwWaiter(clock);
    private final CountingRateLimiter permits = new CountingRateLimiter();
    private final GrowwMetrics metrics = new GrowwMetrics(new SimpleMeterRegistry());
    private final GrowwCooldownManager cooldown =
            new GrowwCooldownManager(clock, waiter, metrics, Duration.ofSeconds(5));
    private final GrowwCircuitBreaker circuitBreaker =
            new GrowwCircuitBreaker(clock, metrics, 3, Duration.ofSeconds(30), 1);
    private final GrowwHealthMonitor health = new GrowwHealthMonitor(clock, cooldown, circuitBreaker);

    @Test
    void retriesConsumeFreshPermits() {
        GrowwCallExecutor executor = executor(retryProperties(3, false));
        int[] calls = {0};

        String result = executor.execute(GrowwOperation.QUOTE, GrowwCallPriority.INTERACTIVE, () -> {
            calls[0]++;
            if (calls[0] < 3) {
                throw new BrokerTransientException("boom", "groww", "QUOTE", "/v1/live-data/quote", 503, null);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls[0]).isEqualTo(3);
        assertThat(permits.permits()).isEqualTo(3);
    }

    @Test
    void mutationsAreNeverRetried() {
        GrowwCallExecutor executor = executor(retryProperties(3, false));
        int[] calls = {0};

        assertThatThrownBy(() -> executor.execute(GrowwOperation.PLACE_ORDER, GrowwCallPriority.INTERACTIVE, () -> {
                    calls[0]++;
                    throw new BrokerTransientException("boom", "groww", "PLACE_ORDER", "/v1/order/create", 503, null);
                }))
                .isInstanceOf(BrokerTransientException.class);
        assertThat(calls[0]).isEqualTo(1);
        assertThat(permits.permits()).isEqualTo(1);
    }

    @Test
    void rateLimitDoesNotOpenCircuitButActivatesCooldown() {
        GrowwCallExecutor executor = executor(retryProperties(1, false));

        assertThatThrownBy(() -> executor.execute(GrowwOperation.QUOTE, GrowwCallPriority.INTERACTIVE, () -> {
                    throw new BrokerRateLimitException(
                            "slow down", "groww", "QUOTE", "/v1/live-data/quote", null, 429, Duration.ofSeconds(2));
                }))
                .isInstanceOf(BrokerRateLimitException.class);

        assertThat(circuitBreaker.state()).isEqualTo(GrowwCircuitBreaker.State.CLOSED);
        assertThat(cooldown.isCoolingDown()).isTrue();
    }

    @Test
    void authenticationFailureIsReportedOnHealth() {
        GrowwCallExecutor executor = executor(retryProperties(1, false));

        assertThatThrownBy(() -> executor.execute(GrowwOperation.QUOTE, GrowwCallPriority.INTERACTIVE, () -> {
                    throw new BrokerAuthenticationException("nope", "groww", "QUOTE", "/v1/live-data/quote", 401, null);
                }))
                .isInstanceOf(BrokerAuthenticationException.class);

        assertThat(health.health().state()).isEqualTo(com.edgerelative.broker.groww.resilience.GrowwBrokerState.AUTH_FAILURE);
    }

    @Test
    void cooldownIsAwaitedBeforeAnyRequest() {
        GrowwCallExecutor executor = executor(retryProperties(1, false));
        cooldown.activate(GrowwRateLimitCategory.LIVE_DATA, Duration.ofSeconds(4));

        String result = executor.execute(GrowwOperation.QUOTE, GrowwCallPriority.INTERACTIVE, () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(clock.instant()).isAfterOrEqualTo(Instant.parse("2025-01-01T00:00:04Z"));
    }

    private GrowwProperties retryProperties(int maxAttempts, boolean jitter) {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setInitialBackoff(Duration.ofMillis(10));
        properties.getRetry().setMaxBackoff(Duration.ofMillis(20));
        properties.getRetry().setBudget(Duration.ofSeconds(10));
        properties.getRetry().setJitter(jitter ? 0.5 : 0.0);
        return properties;
    }

    private GrowwCallExecutor executor(GrowwProperties properties) {
        GrowwRetryPolicy retryPolicy =
                new GrowwRetryPolicy(properties, clock, RandomGenerator.getDefault(), waiter, metrics);
        return new GrowwCallExecutor(
                permits, cooldown, circuitBreaker, retryPolicy, metrics, health, clock, 8, Duration.ofSeconds(5));
    }
}
