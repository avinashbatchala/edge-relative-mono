package com.edgerelative.fundamentals.api.error;

/**
 * The provider rejected the credentials or none were configured.
 */
public class FundamentalAuthenticationException extends FundamentalException {

    public FundamentalAuthenticationException(
            String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, 401, false, cause);
    }
}
