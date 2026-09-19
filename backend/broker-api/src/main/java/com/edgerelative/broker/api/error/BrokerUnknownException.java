package com.edgerelative.broker.api.error;

/** Unclassified broker failure. Treated conservatively as non-retryable. */
public final class BrokerUnknownException extends BrokerException {
    public BrokerUnknownException(
            String message,
            String broker,
            String operation,
            String endpoint,
            String brokerErrorCode,
            Integer httpStatus,
            Throwable cause) {
        super(message, broker, operation, endpoint, brokerErrorCode, httpStatus, false, cause);
    }
}
