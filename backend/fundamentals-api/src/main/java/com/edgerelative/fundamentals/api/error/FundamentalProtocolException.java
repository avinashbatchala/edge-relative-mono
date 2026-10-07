package com.edgerelative.fundamentals.api.error;

/**
 * The provider response could not be parsed into a fundamental snapshot.
 */
public class FundamentalProtocolException extends FundamentalException {

    public FundamentalProtocolException(
            String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, null, false, cause);
    }
}
