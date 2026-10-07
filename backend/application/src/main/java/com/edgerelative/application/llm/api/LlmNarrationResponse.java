package com.edgerelative.application.llm.api;

import com.edgerelative.llm.api.model.LlmCompletion;

/**
 * Advisory narration response. {@code advisory} is always true; the text never carries trading
 * authority.
 */
public record LlmNarrationResponse(boolean advisory, String provider, String model, String text) {

    public static LlmNarrationResponse from(LlmCompletion completion) {
        return new LlmNarrationResponse(true, completion.provider(), completion.model(), completion.text());
    }
}
