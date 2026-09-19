package com.edgerelative.broker.groww.resilience;

import com.edgerelative.broker.groww.observability.GrowwMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Guards against sustained downstream instability.
 *
 * <p>Rate limiting and circuit breaking are deliberately separate: exceeding our own allowance marks
 * the request rate-limited, not the broker unhealthy, so it never opens this circuit. Only genuine
 * infrastructure failures (connection, timeout, 5xx, malformed response) count here.
 */
public class GrowwCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final Clock clock;
    private final GrowwMetrics metrics;
    private final int failureThreshold;
    private final Duration openDuration;
    private final int halfOpenProbes;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private int halfOpenInFlight;
    private Instant openedUntil = Instant.EPOCH;

    public GrowwCircuitBreaker(
            Clock clock, GrowwMetrics metrics, int failureThreshold, Duration openDuration, int halfOpenProbes) {
        this.clock = clock;
        this.metrics = metrics;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.halfOpenProbes = halfOpenProbes;
    }

    /** Returns true if a bounded probe/call is permitted. */
    public synchronized boolean allowCall() {
        Instant now = clock.instant();
        if (state == State.OPEN) {
            if (!now.isBefore(openedUntil)) {
                transition(State.HALF_OPEN);
                halfOpenInFlight = 0;
            } else {
                return false;
            }
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenInFlight >= halfOpenProbes) {
                return false;
            }
            halfOpenInFlight++;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN || state == State.CLOSED) {
            consecutiveFailures = 0;
            halfOpenInFlight = 0;
            transition(State.CLOSED);
        }
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open(clock.instant());
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open(clock.instant());
        }
    }

    public synchronized State state() {
        if (state == State.OPEN && !clock.instant().isBefore(openedUntil)) {
            return State.HALF_OPEN;
        }
        return state;
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN && clock.instant().isBefore(openedUntil);
    }

    private void open(Instant now) {
        openedUntil = now.plus(openDuration);
        halfOpenInFlight = 0;
        transition(State.OPEN);
    }

    private void transition(State next) {
        if (state != next) {
            state = next;
            metrics.recordCircuitTransition(next.name());
        }
    }
}
