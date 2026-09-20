package com.edgerelative.broker.groww.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Deterministic clock for time-based resilience tests. Not thread-safe by accident: synchronized.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public synchronized ZoneId getZone() {
        return zone;
    }

    @Override
    public synchronized Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public synchronized Instant instant() {
        return instant;
    }

    public synchronized void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    public synchronized void set(Instant newInstant) {
        this.instant = newInstant;
    }
}
