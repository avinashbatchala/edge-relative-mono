package com.edgerelative.broker.api.error;

/**
 * Requested entity does not exist at the broker.
 */
public final class BrokerNotFoundException extends BrokerException {
    public BrokerNotFoundException(
            String message,
            String broker,
            String operation,
            String endpoint,
            String brokerErrorCode,
            Integer httpStatus) {
        super(message, broker, operation, endpoint, brokerErrorCode, httpStatus, false, null);
    }
}
