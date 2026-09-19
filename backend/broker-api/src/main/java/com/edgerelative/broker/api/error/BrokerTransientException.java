package com.edgerelative.broker.api.error;

/** Transient broker/network failure that may be retried when the operation is safe to retry. */
public class BrokerTransientException extends BrokerException {
    public BrokerTransientException(
            String message,
            String broker,
            String operation,
            String endpoint,
            Integer httpStatus,
            Throwable cause) {
        super(message, broker, operation, endpoint, null, httpStatus, true, cause);
    }
}
