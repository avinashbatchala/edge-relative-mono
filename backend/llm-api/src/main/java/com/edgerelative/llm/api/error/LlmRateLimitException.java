package com.edgerelative.llm.api.error;

/**
 * The provider rate-limited the request.
 */
public class LlmRateLimitException extends LlmException {

    public LlmRateLimitException(
            String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, 429, true, cause);
    }
}
