package com.edgerelative.broker.api.error;

/**
 * Caller interrupted/cancelled the broker operation.
 *
 * <p>The interruption flag is restored by the thrower so it propagates coherently through callers.
 */
public final class BrokerInterruptedException extends BrokerException {
    public BrokerInterruptedException(
            String message, String broker, String operation, String endpoint, Throwable cause) {
        super(message, broker, operation, endpoint, null, null, false, cause);
    }
}
