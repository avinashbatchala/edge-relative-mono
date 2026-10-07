package com.edgerelative.llm.api.error;

/**
 * The provider rejected the credentials, or none were configured.
 */
public class LlmAuthenticationException extends LlmException {

    public LlmAuthenticationException(
            String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, 401, false, cause);
    }
}
