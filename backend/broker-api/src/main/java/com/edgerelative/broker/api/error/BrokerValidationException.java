package com.edgerelative.broker.api.error;

/**
 * Deterministic request/validation failure. Never retried as an infrastructure fault.
 */
public final class BrokerValidationException extends BrokerException {
    public BrokerValidationException(
            String message,
            String broker,
            String operation,
            String endpoint,
            String brokerErrorCode,
            Integer httpStatus) {
        super(message, broker, operation, endpoint, brokerErrorCode, httpStatus, false, null);
    }
}
