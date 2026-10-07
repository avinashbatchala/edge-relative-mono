# ADR-007: LLM Provider Abstraction and Secret Handling

- **Status:** Accepted
- **Date:** 2026-10-07
- **Related:** DD-06, DD-04 frontend authority, `secrets.properties.example`, broker adapter pattern

## Context

DD-06 introduces LLM-assisted narration of code-computed fundamentals. The operator holds a
DeepSeek API key. Edge Relative already has a single git-ignored `secrets.properties`
(Spring-imported; Compose `--env-file`) that holds PostgreSQL and Groww credentials.

Two questions must be settled: where the key lives, and whether the provider is fixed.

Constraints:

- The Vue frontend is a browser SPA; a macOS app built with Tauri (ADR-004) cannot hide a
  bundled key from its own user. Any client-side key is therefore unacceptable.
- AGENTS.md forbids introducing infrastructure/frameworks without a demonstrated need.
- India data-residency may make a different or local provider preferable later.

## Decision

1. Define a vendor-neutral `LlmClient` port in a new framework-free `llm-api` module.
   Implement it first for DeepSeek in `llm-deepseek`, selected by `LLM_PROVIDER`.
2. **The Java backend is the only process that calls the provider** for the application.
   The browser calls the backend; it never receives the key.
3. Bind `LLM_PROVIDER`, `LLM_API_KEY`, and `DEEPSEEK_*` from `secrets.properties` / the
   environment, following the Groww `@ConfigurationProperties` pattern: empty placeholders,
   and the application boots without a key with LLM features disabled rather than failing.
4. The Python research layer reads the same `LLM_*` variables from its environment (for
   example `uv run --env-file ../secrets.properties`). It is the same key value, but a
   separate process.
5. Keys are never written to Git, logs, the database, dataset artifacts, or `VITE_*`
   variables.

## Data-residency note

DeepSeek is hosted in China. Sending Indian market and company data to it is a compliance
consideration, not just a technical one. The provider abstraction exists so a local or
differently-hosted model can be substituted by configuration. Prompts must exclude
order/position and other account-sensitive state, and LLM output is advisory only.

## Consequences

- One secret file remains the single local source of truth for all credentials.
- Provider changes are configuration changes, not code changes.
- CI injects `LLM_API_KEY` from GitHub Actions secrets as an environment variable; it is
  never committed.
- Narrated output is stored with provenance and cannot affect setup validity, sizing, or
  risk.
