package com.edgerelative.broker.api.error;

import java.util.Objects;

/**
 * Base of all broker integration failures.
 *
 * <p>Carries classification metadata so callers can make safety decisions without parsing messages.
 * Never carries credentials, tokens or {@code Authorization} headers.
 */
public class BrokerException extends RuntimeException {

    private final String broker;
    private final String operation;
    private final String endpoint;
    private final String brokerErrorCode;
    private final Integer httpStatus;
    private final boolean retryable;

    public BrokerException(
            String message,
            String broker,
            String operation,
            String endpoint,
            String brokerErrorCode,
            Integer httpStatus,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.broker = broker;
        this.operation = operation;
        this.endpoint = endpoint;
        this.brokerErrorCode = brokerErrorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String broker() {
        return broker;
    }

    public String operation() {
        return operation;
    }

    public String endpoint() {
        return endpoint;
    }

    public String brokerErrorCode() {
        return brokerErrorCode;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    /**
     * Safe, single-line description without secrets for logs.
     */
    public String safeDescription() {
        return "%s broker=%s operation=%s endpoint=%s code=%s http=%s retryable=%s"
                .formatted(
                        getClass().getSimpleName(),
                        Objects.toString(broker, "-"),
                        Objects.toString(operation, "-"),
                        Objects.toString(endpoint, "-"),
                        Objects.toString(brokerErrorCode, "-"),
                        Objects.toString(httpStatus, "-"),
                        retryable);
    }
}
