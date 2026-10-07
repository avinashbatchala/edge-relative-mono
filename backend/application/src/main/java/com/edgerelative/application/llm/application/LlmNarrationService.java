package com.edgerelative.application.llm.application;

import com.edgerelative.llm.api.error.LlmUnavailableException;
import com.edgerelative.llm.api.model.LlmCompletion;
import com.edgerelative.llm.api.model.LlmPrompt;
import com.edgerelative.llm.api.port.LlmClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Advisory narration of code-computed numbers.
 *
 * <p>The boundary is enforced in the system prompt and by construction: the model explains facts it
 * is given and never invents or alters them. Output is advisory text; it is never parsed into a
 * trading decision (DD-06 §2, ADR-007).
 */
@Service
public class LlmNarrationService {

    static final String SYSTEM_PROMPT = """
            You are an advisory research assistant for an Indian equities (NSE/BSE) operator.
            You explain numbers that were already computed by deterministic code. Never invent,
            estimate, or alter any number, and never issue buy/sell instructions, position sizes,
            or risk decisions. If the facts are insufficient, say so plainly. Answer in concise
            plain language and state the reporting period and basis when relevant.
            """;

    private final ObjectProvider<LlmClient> clientProvider;

    public LlmNarrationService(ObjectProvider<LlmClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    public boolean available() {
        LlmClient client = clientProvider.getIfAvailable();
        return client != null && client.available();
    }

    public LlmCompletion narrate(String question, String facts) {
        LlmClient client = clientProvider.getIfAvailable();
        if (client == null || !client.available()) {
            throw new LlmUnavailableException(
                    "No LLM provider is configured", "none", "narrate", null, null, null);
        }
        String user = "Question: " + question + "\n\nCode-computed facts:\n" + facts;
        return client.complete(LlmPrompt.of(SYSTEM_PROMPT, user));
    }
}
