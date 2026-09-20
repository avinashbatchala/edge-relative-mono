package com.edgerelative.broker.groww.resilience;

import java.time.Instant;

/**
 * Proof that a request was admitted against a category's full window set.
 */
public record GrowwPermit(GrowwRateLimitCategory category, Instant admittedAt) {
}
