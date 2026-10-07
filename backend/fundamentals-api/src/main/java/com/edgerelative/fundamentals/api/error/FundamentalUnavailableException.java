package com.edgerelative.fundamentals.api.error;

/**
 * The provider is temporarily unreachable or returned a retryable server error.
 */
public class FundamentalUnavailableException extends FundamentalException {

    public FundamentalUnavailableException(
            String message,
            String provider,
            String operation,
            String endpoint,
            Integer httpStatus,
            Throwable cause) {
        super(message, provider, operation, endpoint, null, httpStatus, true, cause);
    }
}
