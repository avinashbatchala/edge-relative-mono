package com.edgerelative.llm.api.error;

/**
 * The provider response could not be parsed into a completion.
 */
public class LlmProtocolException extends LlmException {

    public LlmProtocolException(
            String message, String provider, String operation, String endpoint, Throwable cause) {
        super(message, provider, operation, endpoint, null, null, false, cause);
    }
}
