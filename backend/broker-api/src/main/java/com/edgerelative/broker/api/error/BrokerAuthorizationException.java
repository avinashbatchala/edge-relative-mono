package com.edgerelative.broker.api.error;

/**
 * Authenticated caller is not permitted to perform the operation.
 */
public final class BrokerAuthorizationException extends BrokerException {
    public BrokerAuthorizationException(
            String message,
            String broker,
            String operation,
            String endpoint,
            String brokerErrorCode,
            Integer httpStatus) {
        super(message, broker, operation, endpoint, brokerErrorCode, httpStatus, false, null);
    }
}
