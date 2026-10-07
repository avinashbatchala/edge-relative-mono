package com.edgerelative.fundamentals.api.error;

import java.util.Objects;

/**
 * Base of all fundamental-data failures. Never carries credentials or an {@code Authorization}
 * header.
 */
public class FundamentalException extends RuntimeException {

    private final String provider;
    private final String operation;
    private final String endpoint;
    private final String providerErrorCode;
    private final Integer httpStatus;
    private final boolean retryable;

    public FundamentalException(
            String message,
            String provider,
            String operation,
            String endpoint,
            String providerErrorCode,
            Integer httpStatus,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.operation = operation;
        this.endpoint = endpoint;
        this.providerErrorCode = providerErrorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String provider() {
        return provider;
    }

    public String operation() {
        return operation;
    }

    public String endpoint() {
        return endpoint;
    }

    public String providerErrorCode() {
        return providerErrorCode;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    public String safeDescription() {
        return "%s provider=%s operation=%s endpoint=%s code=%s http=%s retryable=%s"
                .formatted(
                        getClass().getSimpleName(),
                        Objects.toString(provider, "-"),
                        Objects.toString(operation, "-"),
                        Objects.toString(endpoint, "-"),
                        Objects.toString(providerErrorCode, "-"),
                        Objects.toString(httpStatus, "-"),
                        retryable);
    }
}
