package com.edgerelative.broker.groww.resilience;

import com.edgerelative.broker.api.error.BrokerInterruptedException;
import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Deterministic sliding-window limiter enforcing every configured window per category.
 *
 * <p>Capacity is only consumed after all windows have room, so a request can never violate a longer
 * window while satisfying a shorter one. Waits are bounded by the configured operation timeout and
 * never busy-spin. The lock guards only the small window state, never a network call.
 */
public class SlidingWindowGrowwRateLimiter implements GrowwRateLimiter {

    private final Map<GrowwRateLimitCategory, List<WindowState>> windows;
    private final Clock clock;
    private final GrowwWaiter waiter;
    private final GrowwMetrics metrics;
    private final Duration maxWait;
    private final ReentrantLock lock = new ReentrantLock(true);

    public SlidingWindowGrowwRateLimiter(
            GrowwProperties properties, Clock clock, GrowwWaiter waiter, GrowwMetrics metrics) {
        this.clock = clock;
        this.waiter = waiter;
        this.metrics = metrics;
        this.maxWait = GrowwOperation.maxWaitFor(properties);
        this.windows = new EnumMap<>(GrowwRateLimitCategory.class);
        for (GrowwRateLimitCategory category : GrowwRateLimitCategory.values()) {
            List<WindowState> states = new ArrayList<>();
            for (GrowwProperties.Window window : GrowwOperation.windowsFor(properties, category)) {
                states.add(new WindowState(window.getWindow(), window.getLimit()));
            }
            windows.put(category, states);
        }
    }

    @Override
    public GrowwPermit acquire(GrowwRateLimitCategory category) {
        List<WindowState> states = windows.get(category);
        Instant firstWaitStart = null;
        while (true) {
            Duration wait;
            lock.lock();
            try {
                Instant now = clock.instant();
                wait = Duration.ZERO;
                Duration retryAfter = Duration.ZERO;
                for (WindowState state : states) {
                    state.purge(now);
                    if (state.isSaturated()) {
                        Duration until = state.untilFree(now);
                        if (until.compareTo(retryAfter) > 0) {
                            retryAfter = until;
                        }
                    }
                }
                if (retryAfter.isZero() || retryAfter.isNegative()) {
                    for (WindowState state : states) {
                        state.record(now);
                    }
                    if (firstWaitStart != null) {
                        metrics.recordRateLimitWait(category, Duration.between(firstWaitStart, now));
                    }
                    return new GrowwPermit(category, now);
                }
                wait = retryAfter;
                if (firstWaitStart == null) {
                    firstWaitStart = now;
                }
                if (Duration.between(firstWaitStart, now.plus(wait)).compareTo(maxWait) > 0) {
                    metrics.recordRateLimitRejection(category);
                    throw new BrokerRateLimitException(
                            "Rate-limit admission for %s exceeded the operation budget".formatted(category),
                            "groww",
                            category.name(),
                            null,
                            null,
                            429,
                            wait);
                }
            } finally {
                lock.unlock();
            }
            await(wait);
        }
    }

    private void await(Duration duration) {
        try {
            waiter.await(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerInterruptedException(
                    "Interrupted while waiting for Groww rate-limit admission", "groww", null, null, e);
        }
    }

    private static final class WindowState {
        private final Duration window;
        private final int limit;
        private final Deque<Instant> entries = new ArrayDeque<>();

        WindowState(Duration window, int limit) {
            this.window = window;
            this.limit = limit;
        }

        void purge(Instant now) {
            Instant cutoff = now.minus(window);
            while (!entries.isEmpty() && !entries.peekFirst().isAfter(cutoff)) {
                entries.removeFirst();
            }
        }

        boolean isSaturated() {
            return entries.size() >= limit;
        }

        Duration untilFree(Instant now) {
            Instant oldest = entries.peekFirst();
            if (oldest == null) {
                return Duration.ZERO;
            }
            return Duration.between(now, oldest.plus(window));
        }

        void record(Instant now) {
            entries.addLast(now);
        }
    }
}
