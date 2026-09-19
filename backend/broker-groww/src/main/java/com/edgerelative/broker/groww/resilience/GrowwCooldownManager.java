package com.edgerelative.broker.groww.resilience;

import com.edgerelative.broker.groww.observability.GrowwMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit cool-off. On a 429 (or locally exhausted allowance) the adapter stops issuing requests
 * until the earliest safe retry, honouring {@code Retry-After} when supplied.
 *
 * <p>Threads do not spin: they wait on the shared waiter and re-check. State is a single atomic
 * reference so many callers observe the same cooldown without a coarse lock.
 */
public class GrowwCooldownManager {

    private final Clock clock;
    private final GrowwWaiter waiter;
    private final GrowwMetrics metrics;
    private final Duration defaultDuration;
    private final AtomicReference<Cooldown> cooldown = new AtomicReference<>();

    public GrowwCooldownManager(
            Clock clock, GrowwWaiter waiter, GrowwMetrics metrics, Duration defaultDuration) {
        this.clock = clock;
        this.waiter = waiter;
        this.metrics = metrics;
        this.defaultDuration = defaultDuration;
    }

    /** Activates/extend cooldown. Concurrent 429s extend to the furthest safe retry. */
    public void activate(GrowwRateLimitCategory category, Duration retryAfter) {
        Duration effective = (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative())
                ? defaultDuration
                : retryAfter;
        Instant until = clock.instant().plus(effective);
        cooldown.updateAndGet(current -> {
            if (current == null || current.until().isBefore(until)) {
                return new Cooldown(until, category);
            }
            return current;
        });
        metrics.recordCooldownActivation(category, effective);
    }

    public void clear() {
        cooldown.set(null);
    }

    public boolean isCoolingDown() {
        return remaining().compareTo(Duration.ZERO) > 0;
    }

    public Duration remaining() {
        Cooldown current = cooldown.get();
        if (current == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), current.until());
        if (remaining.isNegative()) {
            cooldown.compareAndSet(current, null);
            return Duration.ZERO;
        }
        return remaining;
    }

    /** Blocks the calling virtual thread until the cooldown lifts (bounded by the cooldown itself). */
    public void awaitExpiry() {
        Duration remaining = remaining();
        while (remaining.compareTo(Duration.ZERO) > 0) {
            try {
                waiter.await(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            remaining = remaining();
        }
    }

    public GrowwBrokerState state() {
        Cooldown current = cooldown.get();
        if (current == null) {
            return GrowwBrokerState.ACTIVE;
        }
        if (current.category() == GrowwRateLimitCategory.AUTHENTICATION) {
            return GrowwBrokerState.RATE_LIMITED;
        }
        return GrowwBrokerState.COOLING_DOWN;
    }

    private record Cooldown(Instant until, GrowwRateLimitCategory category) {
    }
}
