package com.edgerelative.broker.groww.support;

import com.edgerelative.broker.groww.resilience.GrowwWaiter;
import java.time.Duration;

/**
 * Waiter that advances a {@link MutableClock} instead of sleeping, so window/cooldown tests are
 * deterministic and instantaneous.
 */
public final class AdvancingGrowwWaiter implements GrowwWaiter {

    private final MutableClock clock;

    public AdvancingGrowwWaiter(MutableClock clock) {
        this.clock = clock;
    }

    @Override
    public void await(Duration duration) {
        clock.advance(duration.isNegative() || duration.isZero() ? Duration.ofNanos(1) : duration);
    }
}
