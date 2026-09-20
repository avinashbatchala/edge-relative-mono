# Repository Guidance

## Current State

Edge Relative is a planned algorithmic trading platform for Indian equities and
equity derivatives, initially focused on NSE and a single operator.

The repository contains an initial engineering scaffold: `backend/` has a Java 25
Maven reactor with an empty framework-free `domain` module, a framework-free
`broker-api` module, a Spring Boot `broker-groww` adapter, and a Spring Boot
`application` with jOOQ/PostgreSQL connectivity and local Actuator health;
`frontend/` has a static Vue workstation;
`research/` has a Python package and import smoke test. GitHub Actions runs checks
for all three projects. See `README.md` for setup. A deterministic, versioned feature
engine exists (ATR, RRS family, RVOL daily/interval/cumulative, RVE, directional
volume, market/sector context) under `application/feature`, with a broker-neutral
`/api/v1/features` API and shared fixtures in `contracts/fixtures/features`. There is
still no strategy, risk, broker execution, or frontend/backend integration. Root
`compose.yaml` provides local PostgreSQL; backend integration tests use Testcontainers
PostgreSQL.

Broker integration has two modules: `broker-api` (framework-free, broker-neutral ports and models)
and `broker-groww` (the Groww adapter). Groww read-only capabilities are implemented; every
broker-side mutation is exposed through an Edge Relative contract but refused with
`BROKER_OPERATION_NOT_ENABLED` and emits zero downstream HTTP. See
`docs/design-docs/dev/groww-endpoint-matrix.md`.

## Design Documents

Read the relevant document sections before implementing a feature:

- `design-docs/DD01 - Product Spec and Features.md`: product scope, operating modes, authority boundaries, broker abstraction, and trading lifecycle.
- `design-docs/DD02 - Algorithm Research and Strategy Principles.md`: feature mathematics, deterministic setup qualification, and strategy exits.
- `design-docs/DD03 - Risk Management and Position Sizing.md`: risk permission, sizing, portfolio constraints, and safety controls.
- `design-docs/DD04 - Dev Stack.md`: planned technology stack, module boundaries, engineering standards, testing, and deployment. Its internal document ID is DD-04A.
- `design-docs/DD05 - Market Data & Feature Architecture.md`: canonical events, candles, point-in-time correctness, data quality, storage, and lineage.

The documents refer to future specifications such as DD-06 and DD-09 that are not
currently present. Do not invent their requirements. Preserve the distinction
between methodological sources, Edge formalizations, NSE adaptations, safety
rules, and research hypotheses. Illustrative thresholds are not production defaults.
Raise material ambiguities or conflicts rather than silently redefining a formula
or granting trading authority.

## Planned Stack

- Backend: Java 25 LTS, Spring Boot, Maven multi-module build with Maven Wrapper; initially a modular monolith.
- Persistence: PostgreSQL, SQL Flyway migrations, and jOOQ adapters; no initial JPA/Hibernate.
- Research: Python managed with `uv`; Python does not own authoritative production trading state.
- Frontend: Vue 3, TypeScript, Composition API with `<script setup>`, Vite, Pinia, and `pnpm`. Vue is intentionally selected over React.
- Contracts: OpenAPI for HTTP APIs and explicit versioned WebSocket schemas; shared Java/Python calculation fixtures.
- Analytical history: PostgreSQL is the authoritative canonical candle store for the watched universe (<50 instruments, M1 base only, higher timeframes derived on read). Parquet and object storage remain the eventual store for broad analytical history and are deferred with documented triggers (`docs/design-docs/dev/ADR-001-candle-storage.md`); do not treat PostgreSQL as an unlimited tick warehouse. Derived feature snapshots follow the same scoped exception (`docs/design-docs/dev/ADR-002-feature-snapshot-storage.md`); they are append-only, rebuildable from canonical candles, and never read by the calculation path.
- Operations: Docker/Compose, Terraform, and GitHub Actions when needed.

DD-04A also proposes `contracts/`, `infra/`, `docker/`, `scripts/`, and more backend
modules. These are not implemented yet; add them when concrete requirements exist.
Keep actual documentation in `docs/design-docs` unless a move is explicitly requested.
Do not introduce distributed messaging, Redis, Cassandra, Kubernetes, microservices,
or monorepo orchestration tools without a demonstrated requirement.

## Core Invariants

- Keep measurement, strategy, ML, risk, and execution separate. Infrastructure depends on application/domain code, not the reverse.
- Keep core trading calculations framework-light and in process. Broker-specific semantics belong in adapters, not strategy or feature code.
- Java owns production trading authority. The browser displays authoritative state and requests actions; it cannot enforce risk or declare orders filled.
- ML cannot turn an invalid setup into a trade or override risk limits. Initial ML risk-sizing authority is disabled.
- Reuse canonical production calculations across live, replay, and backtesting. Inject a clock; do not scatter direct wall-clock calls through trading logic.
- Use UTC/`Instant` for machine timestamps and `Asia/Kolkata` with an explicit exchange calendar for session semantics.
- Use decimal or scaled-integer representations for authoritative money and integer quantities. Statistical features may use `double` with defined tolerances.
- Preserve point-in-time availability, versions, source revisions, and decision lineage. Keep future outcome labels separate from feature inputs.
- Missing data is not zero. Incomplete candles are not confirmed closes. Untrustworthy required data blocks new trades.
- Durably record authoritative intent before broker submission. Handle idempotency, ambiguous outcomes, partial fills, and reconciliation explicitly.
- Bound asynchronous queues and define overflow behavior. Critical financial state must not be dropped.
- Risk owns approved quantity; execution cannot increase it. Round sizing toward lower risk and never move structural invalidation merely to obtain a larger position.

## Initial Scope

The active trading watchlist is limited to 20 stocks; the learning universe may be
broader, but collecting data does not grant execution eligibility.
`ER_RS_CONTINUATION_V1` targets intraday NSE cash equities, long and short, with
daily context and M5 setups. Options, futures, overnight positions, averaging down,
and initial pyramiding are outside its trading authority. Strategy profitability
and research thresholds require evidence, not assumptions.

## Verification

Available verification commands:

- Backend: `./mvnw verify` from the Maven project directory.
- Research: `uv sync --locked`, `uv run pytest`, `uv run ruff check .`, and `uv run ruff format --check .` from `research/`.
- Frontend: `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build` from `frontend/`.

Use JDK 25, Node 24 LTS (at least 24.15.0), pnpm 10.34.5, uv 0.12.10, and Python
3.13. The backend rejects other major JDKs. Backend integration tests require a
running Docker daemon and use an isolated PostgreSQL container. Tests currently
cover database connectivity, UTC sessions, scaffold smoke checks, and the domain
dependency boundary, not trading correctness. Favor
deterministic unit/replay tests, architecture-boundary tests, shared cross-language
fixtures, and integration tests for persistence and broker failure behavior.
Report what was verified and what could not run. For documentation-only changes,
check referenced paths and `git diff --check`. Never commit credentials or broker tokens.
