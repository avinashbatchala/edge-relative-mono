package com.edgerelative.application.llm.api;

/**
 * Advisory narration request. Callers must not include credentials, account balances, positions, or
 * order state in either field (ADR-007 data-residency rule).
 */
public record LlmNarrationRequest(String question, String facts) {

    public LlmNarrationRequest {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (facts == null || facts.isBlank()) {
            throw new IllegalArgumentException("facts must not be blank");
        }
    }
}
