package com.edgerelative.broker.groww.resilience;

import java.time.Duration;

/**
 * Production waiter. No busy-spin: it blocks the virtual thread until the window frees capacity.
 */
public final class ThreadSleepingGrowwWaiter implements GrowwWaiter {

    @Override
    public void await(Duration duration) throws InterruptedException {
        long millis = Math.max(1L, duration.toMillis());
        Thread.sleep(millis);
    }
}
