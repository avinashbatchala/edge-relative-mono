package com.edgerelative.broker.api.error;

/** Response could not be parsed or violated the documented envelope/schema. */
public final class BrokerProtocolException extends BrokerException {
    public BrokerProtocolException(
            String message, String broker, String operation, String endpoint, Throwable cause) {
        super(message, broker, operation, endpoint, null, null, false, cause);
    }
}
