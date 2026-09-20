package com.edgerelative.broker.groww.resilience;

import com.edgerelative.broker.api.error.BrokerException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;

/**
 * Safety-aware retry policy.
 *
 * <p>Only retry-safe (read-only) operations with retryable failures are replayed. Mutations are never
 * retried: their ambiguous outcomes must be resolved by idempotency/reconciliation. Retries are
 * bounded by attempts and an overall time budget, use exponential backoff with jitter, and each retry
 * re-enters admission control so it consumes a fresh rate-limit permit and respects cooldown.
 */
public class GrowwRetryPolicy {

    private final GrowwProperties.Retry config;
    private final Clock clock;
    private final RandomGenerator random;
    private final GrowwWaiter waiter;

    public GrowwRetryPolicy(
            GrowwProperties properties, Clock clock, RandomGenerator random, GrowwWaiter waiter, GrowwMetrics metrics) {
        this.config = properties.getRetry();
        this.clock = clock;
        this.random = random;
        this.waiter = waiter;
    }

    public boolean shouldRetry(GrowwOperation operation, BrokerException failure, int attempt, Instant firstAttemptAt) {
        if (!operation.retrySafe()) {
            return false;
        }
        if (!failure.retryable()) {
            return false;
        }
        if (attempt >= config.getMaxAttempts()) {
            return false;
        }
        return Duration.between(firstAttemptAt, clock.instant()).compareTo(config.getBudget()) < 0;
    }

    public Duration backoff(int attempt) {
        double base = config.getInitialBackoff().toMillis() * Math.pow(config.getMultiplier(), (double) attempt - 1);
        double capped = Math.min(base, config.getMaxBackoff().toMillis());
        double jitterFactor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * config.getJitter();
        long millis = Math.max(1L, (long) (capped * jitterFactor));
        return Duration.ofMillis(millis);
    }

    public void awaitBackoff(int attempt) {
        try {
            waiter.await(backoff(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.edgerelative.broker.api.error.BrokerInterruptedException(
                    "Interrupted during Groww retry backoff", "groww", null, null, e);
        }
    }
}
