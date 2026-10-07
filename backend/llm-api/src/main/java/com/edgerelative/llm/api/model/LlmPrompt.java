package com.edgerelative.llm.api.model;

/**
 * An advisory narration request.
 *
 * <p>{@code system} states the boundary (explain code-computed numbers, never invent or alter them);
 * {@code user} carries the already-computed facts. Callers must not place credentials, account
 * balances, positions, or order state in either field (ADR-007 data-residency rule).
 */
public record LlmPrompt(String system, String user, double temperature, int maxOutputTokens) {

    public LlmPrompt {
        if (system == null || system.isBlank()) {
            throw new IllegalArgumentException("system prompt must not be blank");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user prompt must not be blank");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be within [0, 2]");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be >= 1");
        }
    }

    /** Conservative default: low temperature, bounded output. */
    public static LlmPrompt of(String system, String user) {
        return new LlmPrompt(system, user, 0.2, 1024);
    }
}
