package com.edgerelative.fundamentals.api.error;

/**
 * The provider has no fundamentals for the requested instrument.
 */
public class FundamentalNotFoundException extends FundamentalException {

    public FundamentalNotFoundException(
            String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, 404, false, cause);
    }
}
