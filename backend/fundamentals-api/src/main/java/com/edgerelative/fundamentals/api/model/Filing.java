package com.edgerelative.fundamentals.api.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The point-in-time boundary for a fundamental fact: when it became publicly knowable.
 *
 * @param filedAt the filing/announcement timestamp (UTC)
 * @param source human-readable source, for example {@code NSE} or {@code Yahoo}
 * @param documentReference provider document identifier where available
 * @param revision opaque source revision so restatements are distinguishable
 */
public record Filing(Instant filedAt, String source, String documentReference, String revision) {

    public Filing {
        Objects.requireNonNull(filedAt, "filedAt");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}
