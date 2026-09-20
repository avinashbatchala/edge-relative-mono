package com.edgerelative.broker.api.error;

/**
 * Broker is unreachable or returned persistent server failures.
 */
public final class BrokerUnavailableException extends BrokerTransientException {
    public BrokerUnavailableException(
            String message,
            String broker,
            String operation,
            String endpoint,
            Integer httpStatus,
            Throwable cause) {
        super(message, broker, operation, endpoint, httpStatus, cause);
    }
}
