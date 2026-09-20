package com.edgerelative.broker.api.error;

/**
 * Credentials or token are missing, invalid or expired and could not be refreshed.
 */
public final class BrokerAuthenticationException extends BrokerException {
    public BrokerAuthenticationException(
            String message, String broker, String operation, String endpoint, Integer httpStatus, Throwable cause) {
        super(message, broker, operation, endpoint, null, httpStatus, false, cause);
    }
}
