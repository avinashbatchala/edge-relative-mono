package com.edgerelative.broker.groww.resilience;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerAuthorizationException;
import com.edgerelative.broker.api.error.BrokerException;
import com.edgerelative.broker.api.error.BrokerInterruptedException;
import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.api.error.BrokerUnavailableException;
import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.groww.health.GrowwHealthMonitor;
import com.edgerelative.broker.groww.observability.GrowwMetrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The single admission pipeline every Groww call passes through.
 *
 * <p>Ordering rationale:
 * <ol>
 *   <li>cooldown is awaited before consuming a permit, so cooling-down does not burn allowance;
 *   <li>circuit breaker rejects when Groww is genuinely unhealthy;
 *   <li>the category rate limiter grants a permit only if every window has room;
 *   <li>a bounded, fair in-flight semaphore caps concurrent downstream connections;
 *   <li>retries re-enter the whole pipeline.
 * </ol>
 * <p>
 * Virtual threads provide the concurrency; this class provides permission. Locks protect only small
 * state and are never held across the HTTP call.
 */
public class GrowwCallExecutor {

    private final GrowwRateLimiter rateLimiter;
    private final GrowwCooldownManager cooldown;
    private final GrowwCircuitBreaker circuitBreaker;
    private final GrowwRetryPolicy retryPolicy;
    private final GrowwMetrics metrics;
    private final GrowwHealthMonitor health;
    private final Clock clock;
    private final Semaphore inFlight;
    private final Duration inFlightTimeout;

    public GrowwCallExecutor(
            GrowwRateLimiter rateLimiter,
            GrowwCooldownManager cooldown,
            GrowwCircuitBreaker circuitBreaker,
            GrowwRetryPolicy retryPolicy,
            GrowwMetrics metrics,
            GrowwHealthMonitor health,
            Clock clock,
            int maxInFlight,
            Duration inFlightTimeout) {
        this.rateLimiter = rateLimiter;
        this.cooldown = cooldown;
        this.circuitBreaker = circuitBreaker;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.health = health;
        this.clock = clock;
        this.inFlight = new Semaphore(maxInFlight, true);
        this.inFlightTimeout = inFlightTimeout;
    }

    public <T> T execute(GrowwOperation operation, GrowwCallPriority priority, Supplier<T> call) {
        Instant firstAttemptAt = clock.instant();
        int maxAttempts = operation.retrySafe() ? Integer.MAX_VALUE : 1;
        int attempt = 0;
        while (true) {
            attempt++;
            prepare(operation);
            long start = System.nanoTime();
            try {
                T result = call.get();
                metrics.recordRequest(operation, operation.category(), Duration.ofNanos(System.nanoTime() - start), "success");
                circuitBreaker.recordSuccess();
                health.onSuccess();
                return result;
            } catch (BrokerException failure) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
                metrics.recordRequest(operation, operation.category(), elapsed, outcomeOf(failure));
                classify(operation, failure);
                if (attempt >= maxAttempts
                        || !retryPolicy.shouldRetry(operation, failure, attempt, firstAttemptAt)) {
                    throw failure;
                }
                metrics.recordRetry(operation);
                retryPolicy.awaitBackoff(attempt);
            } finally {
                inFlight.release();
                metrics.decrementInFlight();
            }
        }
    }

    private void prepare(GrowwOperation operation) {
        cooldown.awaitExpiry();
        if (!circuitBreaker.allowCall()) {
            throw new BrokerUnavailableException(
                    "Groww circuit breaker is open", "groww", operation.name(), null, null, null);
        }
        rateLimiter.acquire(operation.category());
        acquireInFlight(operation);
    }

    private void acquireInFlight(GrowwOperation operation) {
        try {
            if (!inFlight.tryAcquire(inFlightTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BrokerUnavailableException(
                        "Groww in-flight limit saturated", "groww", operation.name(), null, null, null);
            }
            metrics.incrementInFlight();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerInterruptedException(
                    "Interrupted while awaiting Groww in-flight capacity", "groww", operation.name(), null, e);
        }
    }

    private void classify(GrowwOperation operation, BrokerException failure) {
        if (failure instanceof BrokerRateLimitException rateLimit) {
            metrics.recordHttp429(operation);
            // Rate limiting is not downstream instability: it must not open the circuit, but it does
            // activate explicit cool-off valid for every category caller.
            cooldown.activate(operation.category(), rateLimit.retryAfter());
            circuitBreaker.recordSuccess();
            return;
        }
        if (failure instanceof BrokerValidationException || failure instanceof BrokerAuthorizationException) {
            // Deterministic client errors are not infrastructure failures.
            circuitBreaker.recordSuccess();
            return;
        }
        if (failure instanceof BrokerAuthenticationException) {
            health.onAuthenticationFailure(failure.safeDescription());
            circuitBreaker.recordSuccess();
            return;
        }
        if (failure.retryable()) {
            circuitBreaker.recordFailure();
            health.onUnavailable(failure.safeDescription());
        }
    }

    private static String outcomeOf(BrokerException failure) {
        if (failure instanceof BrokerRateLimitException) {
            return "rate_limited";
        }
        if (failure instanceof BrokerAuthenticationException) {
            return "auth_failure";
        }
        if (failure instanceof BrokerValidationException) {
            return "validation";
        }
        if (failure instanceof BrokerUnavailableException) {
            return "unavailable";
        }
        return "failure";
    }
}
