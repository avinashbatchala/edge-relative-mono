package com.edgerelative.broker.api.error;

import java.time.Duration;

/** Broker rejected or locally refused a call because allowance is exhausted. */
public final class BrokerRateLimitException extends BrokerException {

    private final Duration retryAfter;

    public BrokerRateLimitException(
            String message,
            String broker,
            String operation,
            String endpoint,
            String brokerErrorCode,
            Integer httpStatus,
            Duration retryAfter) {
        super(message, broker, operation, endpoint, brokerErrorCode, httpStatus, true, null);
        this.retryAfter = retryAfter;
    }

    /** Earliest safe retry, when the broker supplied {@code Retry-After}. May be {@code null}. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
