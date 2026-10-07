package com.edgerelative.llm.api.error;

/**
 * The provider did not respond within the configured timeout.
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, null, true, cause);
    }
}
