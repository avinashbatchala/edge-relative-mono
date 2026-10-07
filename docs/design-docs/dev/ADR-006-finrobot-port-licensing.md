# ADR-006: FinRobot Integration — Targeted Port, Not a Full Fork

- **Status:** Accepted
- **Date:** 2026-10-07
- **Related:** DD-06, `research/` package, AGENTS.md stack constraints

## Context

FinRobot (AI4Finance-Foundation, Apache-2.0) is an agentic equity-research platform. Its
production generation (`finrobot_desktop`) is PydanticAI + FastAPI + React/Tauri/Rust with
US-centric data providers (FMP, Finnhub, yfinance, SEC EDGAR). Its V0 package is AutoGen on
Python 3.10/3.11.

Edge Relative is Java 25 + Spring Boot + jOOQ/PostgreSQL + Vue 3 + Python 3.13 research,
targeting NSE/BSE. Vendoring the full FinRobot repository would import a second agent
framework, a second frontend stack, a Rust shell, an older Python runtime, and US data
semantics, and would risk the DD-01 authority boundary (an LLM/agent system adjacent to
trading).

## Decision

1. Do **not** fork or submodule the full FinRobot repository into the monorepo.
2. Port only selected **deterministic** compute operators and the
   "code-computed / LLM-narrated" pattern into `research/src/edge_relative_research/` as
   needed, adapting them to Edge Relative's point-in-time and provenance rules.
3. Keep a local reference copy of FinRobot **outside** the monorepo if desired; do not
   commit its tree.
4. Preserve Apache-2.0 obligations for any ported code: retain the upstream `LICENSE` and
   `NOTICE`, add a `NOTICE`/`third_party` entry naming the ported files and stating that
   they were modified, and do not use the FinRobot trademark.
5. Ported operators live in the Python research layer only; they never gain trading
   authority and are not referenced by Java strategy code.

## Consequences

- No new runtime, no React/Rust/agent-framework dependency in the monorepo.
- Porting is manual but bounded; each operator is reviewed against DD-02/DD-05 semantics.
- Attribution is explicit and auditable.
- If FinRobot's coverage later proves broad enough to justify reuse, this ADR is the
  starting point for a new decision rather than a silent expansion.
