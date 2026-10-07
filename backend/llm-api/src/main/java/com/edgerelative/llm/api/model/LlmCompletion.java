package com.edgerelative.llm.api.model;

import java.util.Objects;

/**
 * A completed narration. Advisory text only; it is never parsed into a trading decision.
 */
public record LlmCompletion(
        String provider,
        String model,
        String text,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens) {

    public LlmCompletion {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(text, "text");
    }
}
