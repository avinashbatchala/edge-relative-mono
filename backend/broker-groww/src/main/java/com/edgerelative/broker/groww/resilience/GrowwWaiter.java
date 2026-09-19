package com.edgerelative.broker.groww.resilience;

import java.time.Duration;

/**
 * Sleeping strategy for admission control. Injectable so tests can advance a fake clock instead of
 * sleeping for real seconds/minutes.
 */
@FunctionalInterface
public interface GrowwWaiter {

    void await(Duration duration) throws InterruptedException;
}
