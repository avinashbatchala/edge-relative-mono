package com.edgerelative.broker.api.error;

/** Downstream call exceeded its explicit timeout budget. */
public final class BrokerTimeoutException extends BrokerTransientException {
    public BrokerTimeoutException(
            String message, String broker, String operation, String endpoint, Throwable cause) {
        super(message, broker, operation, endpoint, null, cause);
    }
}
