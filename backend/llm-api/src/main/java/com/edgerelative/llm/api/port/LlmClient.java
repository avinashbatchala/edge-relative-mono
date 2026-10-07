package com.edgerelative.llm.api.port;

import com.edgerelative.llm.api.model.LlmCompletion;
import com.edgerelative.llm.api.model.LlmPrompt;

/**
 * A single-shot text completion provider.
 *
 * <p>Implementations are blocking and are intended to run on a virtual thread. The port is
 * deliberately narrow: Edge Relative only needs advisory narration of code-computed numbers, never
 * function-calling or tool loops that could reach trading state.
 */
public interface LlmClient {

    /** Stable provider identifier, for example {@code deepseek}. Never a credential. */
    String providerName();

    /** Whether the provider is configured well enough to be called (for example, a key is present). */
    default boolean available() {
        return true;
    }

    /**
     * Completes the prompt synchronously.
     *
     * @throws com.edgerelative.llm.api.error.LlmException on any provider or transport failure
     */
    LlmCompletion complete(LlmPrompt prompt);
}
