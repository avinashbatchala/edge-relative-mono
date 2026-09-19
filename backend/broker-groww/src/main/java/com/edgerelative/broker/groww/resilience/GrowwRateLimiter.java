package com.edgerelative.broker.groww.resilience;

/**
 * Admits requests against a Groww rate-limit category, enforcing every configured window.
 *
 * <p>This is admission control for a scarce downstream resource. It is intentionally not a thread
 * pool: virtual threads provide concurrency, this provides permission.
 */
public interface GrowwRateLimiter {

    GrowwPermit acquire(GrowwRateLimitCategory category);
}
