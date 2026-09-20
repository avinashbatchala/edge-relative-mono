package com.edgerelative.broker.groww.support;

import com.edgerelative.broker.groww.resilience.GrowwPermit;
import com.edgerelative.broker.groww.resilience.GrowwRateLimitCategory;
import com.edgerelative.broker.groww.resilience.GrowwRateLimiter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test rate limiter that records how many permits were consumed.
 */
public final class CountingRateLimiter implements GrowwRateLimiter {

    private final AtomicInteger permits = new AtomicInteger();

    @Override
    public GrowwPermit acquire(GrowwRateLimitCategory category) {
        permits.incrementAndGet();
        return new GrowwPermit(category, Instant.EPOCH);
    }

    public int permits() {
        return permits.get();
    }
}
