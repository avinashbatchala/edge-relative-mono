# Edge Relative — Functionality Testing Prompts

49 independently copyable prompts based on the seven supplied design documents. Copy an entire text block into an agent with repository and test-environment access. Every block includes its own execution and reporting instructions.

**This is a testing guide, not a completed application audit.** Discover actual endpoints and implementation status before testing. Conditional or missing features must be reported accurately.

## Contents

- [01. Authentication, tenants, accounts, and secret isolation](#prompt-01)
- [02. Broker connections, capabilities, and health](#prompt-02)
- [03. Instrument master and temporal reference mappings](#prompt-03)
- [04. Trading calendar and session lifecycle](#prompt-04)
- [05. Watchlist management and eligibility](#prompt-05)
- [06. Historical ingestion, backfill, and coverage](#prompt-06)
- [07. Live market ingestion, ordering, and deduplication](#prompt-07)
- [08. Canonical candles and timeframe aggregation](#prompt-08)
- [09. Corporate actions and adjusted history](#prompt-09)
- [10. Feature registry, dependency graph, warm-up, and parity](#prompt-10)
- [11. RRS math, persistence, and benchmark alignment](#prompt-11)
- [12. RVOL interval, cumulative, and daily baselines](#prompt-12)
- [13. RVE and directional-volume math](#prompt-13)
- [14. ATR, moving averages, VWAP, and general indicators](#prompt-14)
- [15. Pivots, compression, technical void, and triggers](#prompt-15)
- [16. Market regime, sector context, and event-risk gates](#prompt-16)
- [17. Strategy registry, versions, configuration, and flags](#prompt-17)
- [18. Setup state machine and observation history](#prompt-18)
- [19. Opportunity ranking and recommendation explanations](#prompt-19)
- [20. Per-trade risk and position-sizing math](#prompt-20)
- [21. Portfolio exposure, concentration, and concurrent reservations](#prompt-21)
- [22. Drawdown states, circuit breakers, and kill switches](#prompt-22)
- [23. TradePlan creation, validity, and immutability](#prompt-23)
- [24. OMS, idempotent submission, and order events](#prompt-24)
- [25. Execution policies, partial fills, and price protection](#prompt-25)
- [26. Positions, realized/unrealized P&L, and ledger reconstruction](#prompt-26)
- [27. Automatic exits and manual exit workflow](#prompt-27)
- [28. Broker reconciliation and restart recovery](#prompt-28)
- [29. Observe, shadow, paper, assisted, and autopilot permissions](#prompt-29)
- [30. Transaction fees, taxes, spread, and slippage](#prompt-30)
- [31. Backtesting engine and zero-trade diagnosis](#prompt-31)
- [32. Backtest metrics, equity, drawdown, and attribution](#prompt-32)
- [33. Backtest UI and run configuration](#prompt-33)
- [34. Walk-forward, robustness, and experiment registry](#prompt-34)
- [35. Feature dashboard, data trust, and stock detail](#prompt-35)
- [36. Realtime REST/WebSocket continuity and resynchronization](#prompt-36)
- [37. Trade journal, decisions, and performance analytics](#prompt-37)
- [38. Alerts, diagnostics, and operational observability](#prompt-38)
- [39. Dataset catalog, revisions, storage, and compaction](#prompt-39)
- [40. Pattern windows and market-memory representations](#prompt-40)
- [41. Historical similarity and index retrieval](#prompt-41)
- [42. Outcome labels, as-of joins, and ML dataset integrity](#prompt-42)
- [43. ML inference, authority, calibration, and drift](#prompt-43)
- [44. Derivatives analytics and instrument selection](#prompt-44)
- [45. Database migrations, constraints, and transaction integrity](#prompt-45)
- [46. Architecture, API contracts, build, and performance](#prompt-46)
- [47. Operational readiness, deployment, backups, and recovery](#prompt-47)
- [48. Compliance configuration and data-use restrictions](#prompt-48)
- [49. Full-system continuity and regression journey](#prompt-49)

<a id="prompt-01"></a>

## 01. Authentication, tenants, accounts, and secret isolation

```text
PROMPT 01 — Authentication, tenants, accounts, and secret isolation

PURPOSE AND ARCHITECTURE
An operator accesses only their accounts and trading records. Identity belongs to operational state; canonical reference data may be shared. Test application authorization, not merely whether the login page is visible.

DESIGN SOURCES
DD01 §§10–11, 126, 149–150; DD04 security and frontend authority; DD04B §10.1
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Authenticate, enumerate permitted accounts, fetch a permitted resource, expire/revoke the session, and repeat reads and mutations.
2. Use two isolated test tenants/accounts and swap IDs in paths, bodies, query parameters, pagination cursors, exports, and stream subscriptions. Verify denied requests reveal no private resource data and cause no side effects.
3. Exercise missing/malformed/expired credentials, insufficient roles, account deactivation, reconnect, and concurrent session refresh; inspect logs and errors for secret leakage.

MATH / VALIDATION ORACLE
Use a predeclared ownership/permission matrix as the oracle. Assert exact allowed/denied outcomes and unchanged unauthorized rows; never treat a hidden UI button as enforcement.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Compare authenticated identity, response ownership, stored ownership, audit actor, and stream audience across the entire request chain.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-02"></a>

## 02. Broker connections, capabilities, and health

```text
PROMPT 02 — Broker connections, capabilities, and health

PURPOSE AND ARCHITECTURE
Normalize supported brokers behind adapters while keeping strategies broker-independent. A connected socket is not proof that market data or order state is trustworthy.

DESIGN SOURCES
DD01 §§18–23; DD04 broker adapters; DD03 broker-health risk
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Connect through an available sandbox/simulator, read capabilities, quotes, history, account state, and connection health; test each installed adapter separately.
2. Simulate authentication expiry, rate limiting, reconnect, delayed heartbeat, malformed responses, unavailable capabilities, and one broker failing while another remains healthy.
3. Verify retry budgets, request throttling, duplicate callbacks, unsupported segments, and recovery without duplicate subscriptions. Do not place real orders.

MATH / VALIDATION ORACLE
Check canonical field/unit mappings against recorded broker fixtures; independently compute ages and latencies from retained timestamps. Unsupported capability must remain explicit.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Trace broker account and temporal instrument mapping into canonical responses; confirm degraded health reaches risk gates and UI without silently switching sources.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-03"></a>

## 03. Instrument master and temporal reference mappings

```text
PROMPT 03 — Instrument master and temporal reference mappings

PURPOSE AND ARCHITECTURE
Resolve stable economic instruments despite symbol changes, broker-token changes, sector changes, and derivative expiry. Reference mappings use half-open validity ranges.

DESIGN SOURCES
DD01 §§24–25; DD05 §§38–43; DD04B §§3, 6–7
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Import instruments twice, query by canonical ID/symbol/token, and resolve historical aliases before, at, and after a mapping change.
2. Test overlapping intervals, adjacent intervals, duplicate mappings, unknown identifiers, delisting, token reuse, and invalid lot/tick sizes.
3. Where supported, test derivative underlying/expiry/strike/type consistency and reject incomplete contracts.

MATH / VALIDATION ORACLE
For [valid_from, valid_to), verify inclusion at start and exclusion at end; confirm database overlap protection. Symbol changes must not create a second economic instrument.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Follow the same instrument through watchlist, candles, features, plans, fills, and historical reads; no join may fall back to today’s alias or sector.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-04"></a>

## 04. Trading calendar and session lifecycle

```text
PROMPT 04 — Trading calendar and session lifecycle

PURPOSE AND ARCHITECTURE
Use an exchange calendar and injected clock to control pre-market preparation, entry eligibility, bar boundaries, and intraday flattening. Host timezone must not change behavior.

DESIGN SOURCES
DD01 §§131, 133–136; DD05 §§44–45, 93–104, 115–116; DD02 entry and flatten cutoffs
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Query normal, holiday, special, shortened, and week-boundary sessions; replay before/at/after open, blackout end, entry cutoff, flatten cutoff, and close.
2. Run the same fixture under different host timezones; exercise missing calendar data, session overrides, restart mid-session, and an overdue lifecycle job.
3. Verify pre-market readiness requires data warm-up and reconciliation; repeat lifecycle jobs without duplicate effects.

MATH / VALIDATION ORACLE
Calculate UTC/Asia-Kolkata instants independently. Build expected session-anchored bar boundaries and include shortened final bars rather than assuming uniform full intervals.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Compare calendar APIs, candle slots, RVOL slots, strategy entry permission, order expiry, session risk reset, and UI timestamps.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-05"></a>

## 05. Watchlist management and eligibility

```text
PROMPT 05 — Watchlist management and eligibility

PURPOSE AND ARCHITECTURE
Manage at most 20 active stocks while preserving a broader historical learning universe. Watchlist eligibility controls trading authority, not historical data identity.

DESIGN SOURCES
DD01 §§14–17; DD02 §§141–142; DD05 active versus learning universe
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Add, list, reorder, pin, suspend, resume, annotate, and remove stocks through supported APIs; verify persistence after reload.
2. Test 0/1/19/20/21 active entries, duplicate adds, concurrent additions at the cap, suspended-entry semantics, invalid instruments, and cross-tenant requests.
3. Change strategy/derivative eligibility; remove a symbol with an open position and verify protection continues while new-entry eligibility changes.

MATH / VALIDATION ORACLE
Recount active eligible stocks from authoritative rows after each mutation. Verify cap enforcement under concurrent requests rather than only client-side validation.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Reconcile stored list, API order, stream subscriptions, Opportunities rows, and strategy eligibility. Removing a watchlist row must not erase history or abandon positions.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-06"></a>

## 06. Historical ingestion, backfill, and coverage

```text
PROMPT 06 — Historical ingestion, backfill, and coverage

PURPOSE AND ARCHITECTURE
Persist reproducible canonical history with source provenance and enough benchmark/warm-up coverage for downstream calculations.

DESIGN SOURCES
DD01 §29; DD05 historical store, §§274–282; DD04B dataset catalog
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Request history/backfill for a small known range, follow the job to completion, retrieve candles and coverage, and repeat the same request.
2. Test paginated source responses, overlapping ranges, empty market holidays, partial source failure, rate limits, interrupted jobs, retries, and concurrent overlapping backfills.
3. Test missing benchmark history, invalid/reversed dates, unsupported intervals, corrected revisions, and corrupted/partial files.

MATH / VALIDATION ORACLE
Compare unique expected session slots and exact OHLCV fixtures with persisted rows; distinguish source-returned count, accepted count, duplicate count, and missing count.

CONTINUITY AND CROSS-LAYER ASSERTIONS
A completed job must point to readable committed data; coverage, manifest counts/checksums, candle queries, and feature warm-up must agree. Preserve original revisions.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-07"></a>

## 07. Live market ingestion, ordering, and deduplication

```text
PROMPT 07 — Live market ingestion, ordering, and deduplication

PURPOSE AND ARCHITECTURE
Normalize actual source semantics into deterministic canonical events without double-counting trades or pretending quote snapshots are trades.

DESIGN SOURCES
DD05 §§34–37, 46–74; DD01 §§26–27
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Inject valid trade, quote, index, heartbeat, status, and supported depth/OI events through the real ingestion boundary; read resulting state and persisted evidence.
2. Replay duplicates, equal timestamps, late events, sequence gaps, disconnect/reconnect, cumulative-volume resets, corrections, and conflicting sources.
3. Inject impossible prices, negative quantities, crossed quotes under the documented market-state policy, future timestamps, and queue pressure.

MATH / VALIDATION ORACLE
Use a hand-ordered event ledger to calculate unique trade volume and resulting prices. Check event-time versus received/processed-time semantics and configured tie-breaking.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Reconcile source event IDs through normalization, bars, quality incidents, and replay. Recovery must not silently average disagreeing feeds or rewrite live-as-seen decisions.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-08"></a>

## 08. Canonical candles and timeframe aggregation

```text
PROMPT 08 — Canonical candles and timeframe aggregation

PURPOSE AND ARCHITECTURE
Build identical session-aware candles for live processing and replay, preserving incomplete bars and explicit revisions.

DESIGN SOURCES
DD05 §§91–119; DD02 completed-candle and no-look-ahead rules
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Ingest a known trade sequence; fetch M1/M5/D1 and other supported aggregates; independently verify open/high/low/close/volume and finalization.
2. Test duplicate/out-of-order ticks, no-trade intervals versus missing feed intervals, exact close boundaries, lateness cutoff, special sessions, and shortened final bars.
3. Apply a correction and fetch both live-as-seen and corrected revisions; test overlapping bars and malformed OHLC inputs.

MATH / VALIDATION ORACLE
OHLC follows first/max/min/last ordered valid trade; volume sums unique valid quantities. VWAP=sum(price*quantity)/sum(quantity) where inputs support it. Zero total volume is explicitly undefined.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Higher-timeframe aggregates must reconcile to their eligible components. In-progress bars cannot satisfy confirmed-close strategy rules; corrections retain logical candle identity.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-09"></a>

## 09. Corporate actions and adjusted history

```text
PROMPT 09 — Corporate actions and adjusted history

PURPOSE AND ARCHITECTURE
Preserve raw tradable prices while producing explicitly versioned adjusted analytical series without future corporate-action knowledge.

DESIGN SOURCES
DD01 §132; DD05 §§43, 112–114, 260; DD04B corporate actions
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Apply isolated split, bonus, dividend, symbol-change, and supported merger/delisting fixtures; query raw and adjusted history on both sides of the event.
2. Test adjustment announcement/availability versus effective dates, revisions, repeated application, missing factors, and multiple actions in succession.
3. Backtest across a supported action and compare feature continuity with actual execution-price accounting; flag unsupported handling.

MATH / VALIDATION ORACLE
Derive expected factors and price/quantity transformations from the fixture’s defined action; test that factors apply exactly once. Do not assume dividend and split adjustments share identical semantics.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Instrument identity, raw prices, adjusted features, position quantities, dataset revisions, and historical as-of reads must remain consistent.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-10"></a>

## 10. Feature registry, dependency graph, warm-up, and parity

```text
PROMPT 10 — Feature registry, dependency graph, warm-up, and parity

PURPOSE AND ARCHITECTURE
Expose versioned deterministic measurements independently of strategy decisions. Use one canonical production implementation and explicit missingness.

DESIGN SOURCES
DD01 §§33, 102–104; DD05 §§120–140; DD04 shared fixtures
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Enumerate feature schemas and dependencies; calculate snapshots via ordinary application paths and compare historical, replay, and on-demand results.
2. Test each warm-up length at N-1/N/N+1, missing required versus optional inputs, dependency cycles if configuration permits them, version mismatch, and parameter-hash stability.
3. Repeat reads and same-bar evaluation; restart/replay rolling state; test correction rebuilds without altering earlier experiment references.

MATH / VALIDATION ORACLE
Build independent small fixtures per feature, with units and numerical tolerances chosen before comparison. Semantic changes need a feature version; implementation-only changes still require parity checks.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Compare observation key, schema, parameters, data revision, quality, and values across API/stream/storage/UI. Missing, invalid, unavailable, and not-yet-warmed values must not become zero.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-11"></a>

## 11. RRS math, persistence, and benchmark alignment

```text
PROMPT 11 — RRS math, persistence, and benchmark alignment

PURPOSE AND ARCHITECTURE
Measure volatility-adjusted outperformance separately from price direction, using temporally aligned stock and benchmark observations.

DESIGN SOURCES
DD02 §§31–42, 160; DD05 §§137–151
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Call feature APIs on fixtures for market up/stock faster, market up/stock flat, market down/stock flat, market down/stock weaker, and equal normalized moves.
2. Test zero/missing ATR, mismatched intervals, stale benchmark, a one-bar spike followed by neutrality, EMA initialization, D1/M5 agreement, and sector variants.
3. Validate historical percentiles use only prior eligible observations; change benchmark/version explicitly and preserve lineage.

MATH / VALIDATION ORACLE
RRS=(stock_delta/stock_ATR)-(market_delta/market_ATR). Example with deltas 4 and 2, ATRs 2 and 4 gives 1.5. Use the documented ATR timing convention; independently compute smoothing and persistence.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Raw RRS, fast/slow values, persistence, direction, quality and strategy reasons must agree. No silent benchmark fallback or substitution of percent-return difference for RRS.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-12"></a>

## 12. RVOL interval, cumulative, and daily baselines

```text
PROMPT 12 — RVOL interval, cumulative, and daily baselines

PURPOSE AND ARCHITECTURE
Compare volume to the correct historical time-of-day baseline rather than full-day volume or a generic rolling-bar average.

DESIGN SOURCES
DD02 §§43–48, 161; DD05 §§152–156
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Test daily RVOL, same-slot interval RVOL, and cumulative-to-time RVOL with hand-built prior sessions; query all three through the API.
2. Test opening volume that is normal for the slot, abnormal midday volume, missing prior sessions, zero denominator, outlier sessions, shortened sessions, and session resets.
3. Verify current session and future sessions are excluded from the baseline, and baseline method/version is explicit.

MATH / VALIDATION ORACLE
Daily=current daily volume/prior-session mean; interval=current slot volume/prior same-slot mean; cumulative=current cumulative/prior cumulative-to-same-time mean. Example 150/100=1.5; do not sum interval ratios to obtain cumulative RVOL.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Reconcile candle volume, slot membership, baseline samples, numerator/denominator, quality, displayed value, and downstream gate reason.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-13"></a>

## 13. RVE and directional-volume math

```text
PROMPT 13 — RVE and directional-volume math

PURPOSE AND ARCHITECTURE
Measure expansion of abnormal participation. RVE is an Edge Relative feature and must not silently become an unvalidated hard strategy gate.

DESIGN SOURCES
DD02 §§48–53; DD05 §§157–159
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Query a constant positive RVOL series, increasing/decreasing series, an isolated spike, and a restart/replay sequence.
2. Exercise RVOL zero, negative, missing, extreme values, insufficient warm-up, fast/slow parameter changes, and invalid lengths.
3. Where directional volume exists, test all-up, all-down, flat-price, and empty windows with explicit zero-denominator behavior.

MATH / VALIDATION ORACLE
RVE=EWMA(log(interval_RVOL),fast)-EWMA(log(interval_RVOL),slow), using documented initialization and zero handling. Constant RVOL after equivalent initialization gives zero; independently calculate a multi-step fixture.

CONTINUITY AND CROSS-LAYER ASSERTIONS
RVE must reference the correct RVOL version and window. UI labels, explanations, and qualification behavior must distinguish observational features from enabled validated gates.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-14"></a>

## 14. ATR, moving averages, VWAP, and general indicators

```text
PROMPT 14 — ATR, moving averages, VWAP, and general indicators

PURPOSE AND ARCHITECTURE
Provide mathematically defined, versioned indicators from canonical data; unavailable implementations remain explicit rather than returning placeholders.

DESIGN SOURCES
DD01 §39; DD05 §§160–163; DD02 indicator definitions
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Test each implemented ATR/MA/VWAP/HA variant through its public read path with an independent fixture.
2. Exercise price gaps, flat prices, zero volume, incomplete bars, session resets, missing input, window boundaries, and recursive initialization.
3. Test ATR-normalized distance, price/tick units, and incompatible feature versions; distinguish exact trade VWAP from bar-based approximation.

MATH / VALIDATION ORACLE
Check true range against max(high-low, abs(high-prevClose), abs(low-prevClose)); use the selected ATR smoothing rule. Verify VWAP weighted arithmetic and (price-VWAP)/ATR where that is the declared feature definition.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Compare indicator series with snapshots, chart overlays, strategy inputs, and restarted rolling state. A display refresh must not change the underlying observation.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-15"></a>

## 15. Pivots, compression, technical void, and triggers

```text
PROMPT 15 — Pivots, compression, technical void, and triggers

PURPOSE AND ARCHITECTURE
Convert technical structure into deterministic evidence without recognizing pivots or breakouts before confirmation.

DESIGN SOURCES
DD02 price structure and §§79–93; DD05 §§164–169
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Replay pivot-high/low fixtures including equal highs/lows, compression formation/release, retest failure, and nearest support/resistance selection.
2. Test bars immediately before/at/after pivot confirmation and breakout close; test intrabar breach without close confirmation.
3. Exercise zero ATR, missing lookback, nearby opposing barriers, maximum-entry-extension boundaries, and a gap past the trigger.

MATH / VALIDATION ORACLE
Compute levels, buffer rounding, ATR-normalized distances, and technical room independently from the documented strategy version. A pivot requiring future confirmation bars becomes usable only after those bars arrive.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Trace structure evidence into setup entry/invalidation/target references. A chase violation becomes MISSED or the documented equivalent; do not move targets/stops to manufacture reward/risk.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-16"></a>

## 16. Market regime, sector context, and event-risk gates

```text
PROMPT 16 — Market regime, sector context, and event-risk gates

PURPOSE AND ARCHITECTURE
Qualify stocks in broad-market and sector context while keeping unavailable event/context data explicit.

DESIGN SOURCES
DD01 §§30–32, 41–42; DD02 market state, §§82–85, 153–156; DD05 context alignment
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Replay bullish, bearish, range, transition, and dislocated cases; fetch regime and sector views and evaluate long/short candidates.
2. Test conflicting D1/M5 evidence, stale benchmark with fresh stock, absent sector mapping, sector optional versus required policy, and exact regime thresholds.
3. Exercise CLEAR/BLOCKED/UNKNOWN event risk and pre/post-event windows under the selected policy.

MATH / VALIDATION ORACLE
Use a truth table from the strategy version: opposing market forbids the corresponding V1 direction; dislocated state forbids new entries. Research-only confidence thresholds cannot become undocumented defaults.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Regime timestamps, quality, sector mapping revision, allowed direction, and rejection explanations must agree across APIs and setup history.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-17"></a>

## 17. Strategy registry, versions, configuration, and flags

```text
PROMPT 17 — Strategy registry, versions, configuration, and flags

PURPOSE AND ARCHITECTURE
Keep immutable strategy definitions/parameters separate from deployment authority and feature flags.

DESIGN SOURCES
DD01 §§44–45, 138–140; DD02 §§119–122; DD04B control versions and deployments
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Create or select test strategy versions, validate parameters, activate only in sandbox modes, query resolved configuration, and replay known inputs.
2. Test invalid ranges, unknown fields according to contract, incompatible feature schema/instrument/mode, retired versions, and overlapping deployment windows.
3. Change a flag mid-session, retry updates, and test optimistic/concurrent edits without silently rewriting old decisions.

MATH / VALIDATION ORACLE
Hash canonical resolved configuration; identical inputs/version/parameters must produce identical decisions. Parameters that the docs leave empirical require explicit recorded configuration.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Persisted strategy version, runtime deployment, API metadata, UI labels, and every decision reference must match. Disable new entries without disabling necessary position protection.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-18"></a>

## 18. Setup state machine and observation history

```text
PROMPT 18 — Setup state machine and observation history

PURPOSE AND ARCHITECTURE
Track developing setups as append-only observations with stable instance continuity; VALID is qualification, not approval or execution.

DESIGN SOURCES
DD01 §§43–47, 104, 121; DD02 §§78–85, 115–118, 157–162; DD04B §10.3
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Replay NONE/WATCH/FORMING/NEAR_TRIGGER/VALID and invalidation, expiration, MISSED, and entered flows where implemented; inspect latest and history endpoints after every event.
2. Repeat the same bar, reconnect, restart mid-setup, cold-start at an advanced eligible state, and evaluate two strategies/directions on one instrument.
3. Test duplicate observations, stable NONE behavior, new-instance reset after terminal state, out-of-order bars, and threshold boundaries.

MATH / VALIDATION ORACLE
Build an expected transition table from the actual documented contract. Preserve instance ID during one episode and create new identity only per lifecycle rules; replay must reproduce states and reasons.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Latest state must equal the latest applicable historical observation without overwriting history. Snapshot, strategy version, setup instance, reason codes, and eventual risk input must remain linked.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-19"></a>

## 19. Opportunity ranking and recommendation explanations

```text
PROMPT 19 — Opportunity ranking and recommendation explanations

PURPOSE AND ARCHITECTURE
Rank qualified opportunities and explain strategy, ML, and risk separately. A higher rank cannot override an invalid setup or risk prohibition.

DESIGN SOURCES
DD01 §§17, 48, 59–60, 118; DD02 §§105–108, 141–145
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Submit/replay multiple qualified and unqualified candidates, ties, missing optional metrics, and competing sector exposures; inspect ranked results.
2. Change ranking-only inputs and verify hard validity remains unchanged; exercise ML absent/observer/ranker modes and backend risk rejection.
3. Test deterministic tie-breaking, stale candidate removal, shared-capital prioritization, filtered views, and no-op refreshes.

MATH / VALIDATION ORACLE
Recompute configured lexicographic/factor ranking independently; verify factors rather than unexplained scores. Do not invent optimized weights or probabilities from feature magnitudes.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Each recommendation must reference its setup and current authority; UI should distinguish watch/forming/qualified/approved/blocked. Ranking changes must not mutate historical reasons.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-20"></a>

## 20. Per-trade risk and position-sizing math

```text
PROMPT 20 — Per-trade risk and position-sizing math

PURPOSE AND ARCHITECTURE
Calculate conservative permitted quantity from structural stop risk plus execution allowance, then apply every binding ceiling.

DESIGN SOURCES
DD03 §§13–29, 37–58, 149–160; DD04B risk decision constraints
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Evaluate long and short candidates with controlled equity/entry/stop/allowance; force each capital, liquidity, exposure, and broker limit to bind separately.
2. Test zero/negative stop distance, missing inputs, tiny stops, less-than-one-share capacity, tick/lot rounding, threshold equality, and APPROVE/REDUCE/REJECT.
3. Test session profit versus loss: profits must not automatically compound session reference equity; losses may reduce permission.

MATH / VALIDATION ORACLE
EffectiveLossPerUnit=positive directional stop distance+execution allowance; risk quantity=floor(budget/effective loss), final quantity=min(all applicable caps), rounded down. A synthetic budget 1000 and loss 12 permits at most 83 units before other caps. This is a fixture, not a default policy.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Reconcile immutable input snapshot, decision limits/reasons, plan quantity/risk, reservation, and UI values. Independent arithmetic must not call the implementation under test.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-21"></a>

## 21. Portfolio exposure, concentration, and concurrent reservations

```text
PROMPT 21 — Portfolio exposure, concentration, and concurrent reservations

PURPOSE AND ARCHITECTURE
Allocate shared account capacity safely across simultaneous candidates and pending orders; opposite positions do not erase gross risk.

DESIGN SOURCES
DD03 §§30–36, 59–78; DD04B §§2.6, 10.4, 17.1–17.3
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Create test long/short holdings and pending entries across symbols/sectors; evaluate additional candidates at symbol/sector/gross/net/position-count limits.
2. Issue concurrent approval requests that individually fit but collectively exceed capacity; repeat idempotent requests and inject rollback around reservation creation.
3. Partially fill, cancel the remainder, expire an intent, and close a position; inspect risk release exactly once.

MATH / VALIDATION ORACLE
Gross exposure=sum absolute notionals; net exposure=sum signed notionals. Compare risk reservations and consumed capacity against an independent account ledger after every transition. Net zero is not gross zero.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Risk decision, plan, reservation and account capacity must commit atomically under the existing lock policy. Reservations must neither leak nor be released twice across retries/restarts.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-22"></a>

## 22. Drawdown states, circuit breakers, and kill switches

```text
PROMPT 22 — Drawdown states, circuit breakers, and kill switches

PURPOSE AND ARCHITECTURE
Reduce trading permission as risk deteriorates while keeping controlled positions protected. Test controls only in an isolated simulator.

DESIGN SOURCES
DD03 §§43–50, 98–148, 199–207; DD01 §§86–87
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Drive controlled P&L through configured daily/weekly/monthly/account thresholds and risk states, including exact boundaries and recovery conditions.
2. Trigger stale data, repeated rejection, broker degradation, reconciliation mismatch, and risk-service failure.
3. Exercise stop-new-risk, cancel pending entries, reduce, flatten, and halt; race new-entry requests with a control activation and restart while halted.

MATH / VALIDATION ORACLE
Recompute each documented drawdown denominator/high-water mark independently; distinguish realized loss, current equity drawdown, and open risk. No arbitrary threshold is a production default.

CONTINUITY AND CROSS-LAYER ASSERTIONS
A control request is not proof of flatness. Follow cancellations, partial exit fills, remaining exposure, protection, reconciliation and audit. Never cancel protective exits as if they were pending entries.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-23"></a>

## 23. TradePlan creation, validity, and immutability

```text
PROMPT 23 — TradePlan creation, validity, and immutability

PURPOSE AND ARCHITECTURE
Represent approved immutable trade intent with complete setup, feature, strategy, risk, entry/stop/target, quantity and validity lineage.

DESIGN SOURCES
DD01 §§67–69; DD02 §§79–102; DD03 §§149–160; DD04B §§2.4, 13
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Create plans from APPROVE and REDUCE decisions; reject other outcomes; repeat the same decision concurrently to verify idempotency.
2. Test cross-account/instrument references, excess approved quantity/risk/notional, invalid directional prices, missing target methodology, and exact expiration boundaries.
3. Change current market price, invalidate the setup, revise intent, and start simulated execution; verify original plan remains unchanged.

MATH / VALIDATION ORACLE
Independently reconcile entry, structural invalidation, protective stop, effective planned loss and ceilings. Targets follow structure; no forced fixed reward/risk. A validity clock never renews approval on read.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Follow exact setup observation→risk decision→plan→order. Distinguish frozen intent, current eligibility, and execution state; revisions require explicit new lineage.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-24"></a>

## 24. OMS, idempotent submission, and order events

```text
PROMPT 24 — OMS, idempotent submission, and order events

PURPOSE AND ARCHITECTURE
Persist order intent before external side effects and track an append-only order event history independently of broker terminology.

DESIGN SOURCES
DD01 §§74–78; DD04B §§2.5, 10.5, 17.2; DD04 durability
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Submit approved intents to a broker simulator, follow CREATED through supported terminal states, and inspect read APIs/events.
2. Simulate broker accepted but response lost; retry the same client reference; reconcile instead of blindly placing another order.
3. Test duplicate callbacks, out-of-order acknowledgement/fill, cancel-versus-fill races, modification rejection, and restart at each commit/network boundary.

MATH / VALIDATION ORACLE
Count unique broker submissions and fills by durable identity; filled quantity must never exceed valid ordered quantity absent a documented broker correction. Verify legal transitions and average fill arithmetic.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Trace committed plan/order intent before broker invocation, then event history and current projection. Unknown acknowledgement must remain explicit and block unsafe retry behavior.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-25"></a>

## 25. Execution policies, partial fills, and price protection

```text
PROMPT 25 — Execution policies, partial fills, and price protection

PURPOSE AND ARCHITECTURE
Implement approved intent under price/liquidity constraints without exceeding quantity or risk; execution algorithms cannot promote an invalid trade.

DESIGN SOURCES
DD01 §§76, 78–79, 89–91; DD03 execution allowances; DD04B §17.3
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Simulate market/limit/stop types actually supported, slicing, delayed acknowledgement, partial fills, remainder cancellation and timeout.
2. Test gaps, stale quotes, widened spread, exhausted liquidity, lot constraints, and repricing outside approved entry bounds.
3. Deliver duplicate fill IDs, split one economic fill into multiple events, and crash during fill handling.

MATH / VALIDATION ORACLE
Example fills 20@100 and 30@102 give quantity 50 and weighted average 101.2 before fees. Verify per-order versus per-fill cost semantics and no fill outside limit constraints.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Fill insert, order/trade/position projections, reservation changes and audit must be atomic. Execution quality metrics must use the documented reference price/time and direction sign.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-26"></a>

## 26. Positions, realized/unrealized P&L, and ledger reconstruction

```text
PROMPT 26 — Positions, realized/unrealized P&L, and ledger reconstruction

PURPOSE AND ARCHITECTURE
Derive position state from immutable fills and reconcile it with broker truth; position rows alone are not execution evidence.

DESIGN SOURCES
DD01 §§81–82, 122; DD04A position projection correction; DD04B §§2.3, 17.3
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Open a long and short, partially reduce, fully close, and re-enter through simulated orders; retrieve position and account views after each fill.
2. Test duplicate fills, out-of-order delivery, fee adjustments, stale/missing marks, multiple trades in one symbol, and process restart.
3. Rebuild projections from the ledger in an isolated database and compare with normal operation.

MATH / VALIDATION ORACLE
For a simple long 10@100 closed 4@110, gross realized=40 and remaining quantity=6; at mark105 unrealized=30 under average-cost semantics. Subtract correctly attributed fees once. Test mirrored short arithmetic.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Reconcile orders/fills→trade→position→portfolio equity→UI and journal. Stale marks must be labeled; neither a missing quote nor an empty API page means zero exposure.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-27"></a>

## 27. Automatic exits and manual exit workflow

```text
PROMPT 27 — Automatic exits and manual exit workflow

PURPOSE AND ARCHITECTURE
Close or reduce positions under the defined exit hierarchy, including thesis failure and intraday flattening. An exit click is only a request.

DESIGN SOURCES
DD01 §§83–85; DD02 §§95–104, 173; DD03 protective exits
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Trigger emergency/risk exit, stop, thesis invalidation, market reversal, target, time stop and session flatten individually; test simultaneous conditions for priority.
2. Exercise RRS hysteresis around zero, gap through stop, partial exit fill, rejected exit, repeated exit click, and cancel/entry race.
3. Verify V1 no averaging down, no initial pyramiding, and no accidental overnight carry; test failure to flatten as an explicit unresolved condition.

MATH / VALIDATION ORACLE
Use the strategy’s documented condition persistence and priority; compute remaining quantity after every fill and ensure exits cannot reverse a position by over-closing.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Follow conflicting entry cancellation, exit intent, fills, flat confirmation and reconciliation. Preserve original plan and structured exit reasons; keep residual positions protected.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-28"></a>

## 28. Broker reconciliation and restart recovery

```text
PROMPT 28 — Broker reconciliation and restart recovery

PURPOSE AND ARCHITECTURE
Detect disagreement between internal evidence and broker snapshots and restore safe authority after interruption.

DESIGN SOURCES
DD01 §§80, 143–144; DD03 broker/reconciliation risk; DD04 §72; DD04B §§10.6, 17.4
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Simulate matching state, broker-only position/order/fill, internal-only pending order, quantity mismatch and stale broker snapshot.
2. Restart before submission, after broker acceptance, after partial fill and during reconciliation; run reconciliation twice.
3. Inject missing credentials, failed protection restoration, competing execution instances and delayed broker evidence.

MATH / VALIDATION ORACLE
Compare timestamped independently assembled internal and broker ledgers; classify differences rather than overwriting one with the other. Exactly one execution authority may be enabled.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Trading remains disabled until required recovery gates pass. Reconciliation items, risk state, restored protection, orders and audit must explain every recovery decision.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-29"></a>

## 29. Observe, shadow, paper, assisted, and autopilot permissions

```text
PROMPT 29 — Observe, shadow, paper, assisted, and autopilot permissions

PURPOSE AND ARCHITECTURE
Keep mode-specific side effects and authority explicit while reusing domain decisions. Future modes must not become enabled simply because a UI switch exists.

DESIGN SOURCES
DD01 §§12–13, 88, 139–147; DD03 trading modes; DD04 single execution authority
Scope classification: Conditional / later deployment stages. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Replay identical inputs/configuration through supported research/backtest/observe/paper/shadow modes and compare pre-execution decisions.
2. Use sandbox adapters for assisted approval, denied/expired approval, permission-limited autopilot and mode-switch races.
3. Test strategy/symbol/instrument/time/risk restrictions, restart defaults, no duplicate live authority and manual risk-reducing controls.

MATH / VALIDATION ORACLE
Compare feature/setup/risk intent before execution differences. Observe mode cannot place orders; simulation state and reservations must not consume live capacity.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Mode and deployment identity must propagate through every decision/order/result. Unimplemented modes are reported as unavailable, not approximated with live execution.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-30"></a>

## 30. Transaction fees, taxes, spread, and slippage

```text
PROMPT 30 — Transaction fees, taxes, spread, and slippage

PURPOSE AND ARCHITECTURE
Account for historical, instrument- and side-specific transaction friction without confusing attribution with cash charges.

DESIGN SOURCES
DD01 §§79, 90, 94; DD02 §139; DD03 execution-adjusted risk
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Use versioned synthetic fee schedules to calculate entry/exit round trips, long/short cases, multiple fills, brokerage caps, and effective-date boundaries.
2. Test zero-rate components, minimum charges where supported, rounding, cancelled/unfilled orders, and partial closes.
3. Compare reference-price and executed-price P&L with separately itemized fees; verify schedules unavailable for the period produce explicit limitations.

MATH / VALIDATION ORACLE
Independently evaluate each configured fee base, tax dependency, cap and rounding step using decimals. Slippage already embedded in fill prices is attribution, not a second deduction.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Reconcile cost schedule version and itemization across risk allowance, fills, ledger, journal, backtest totals and UI. Do not hardcode alleged current legal rates from design-doc examples.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-31"></a>

## 31. Backtesting engine and zero-trade diagnosis

```text
PROMPT 31 — Backtesting engine and zero-trade diagnosis

PURPOSE AND ARCHITECTURE
Run persisted history through production logic and explain exactly where trades are eliminated; demonstrate a complete simulated lifecycle.

DESIGN SOURCES
DD01 §§92–99; DD04 §40; DD03 §§182–194; DD05 revision freeze
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Discover real APIs, preflight coverage, start a historical run, poll terminal state and fetch diagnostics, trades, positions and results.
2. Trace counts through events→features→setups→candidates→risk→plans→orders→fills→exits; inspect the first unexplained drop and top rejection reasons.
3. Run an isolated persisted positive-control dataset through the same APIs, satisfying actual rules and producing at least one completed trade; also run legitimate zero-trade and missing-data cases.
4. Test simulated-clock freshness, stable setup state, warm-up, shared capital, equal-time ordering, next-eligible fill, same-bar stop/target ambiguity, cancellation/restart, and end-of-run positions.

MATH / VALIDATION ORACLE
Calculate expected fills/costs/P&L independently for the positive control; never inject a completed trade or weaken production rules. Historical data need not produce trades, but no-trade explanations must be evidenced.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Freeze inputs/build/policies and rerun for identical economic results. Reconcile stage counts and ledger totals with results APIs; partial or failed runs cannot masquerade as successful empty runs.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-32"></a>

## 32. Backtest metrics, equity, drawdown, and attribution

```text
PROMPT 32 — Backtest metrics, equity, drawdown, and attribution

PURPOSE AND ARCHITECTURE
Measure portfolio performance from net marked-to-market equity and clearly defined completed-trade aggregation.

DESIGN SOURCES
DD01 §95; DD03 §§192–194; DD02 setup-level metrics
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Fetch results for all-win, all-loss, breakeven, no-trade, partial-exit and still-open-position fixtures.
2. Test unequal symbol trade counts, multi-day positions if supported, costs, missing marks, one observation, zero variance and zero loss denominator.
3. Verify CAGR applicability, Sharpe/Sortino sampling/annualization/reference rate, average R denominator, turnover and exposure definitions.

MATH / VALIDATION ORACLE
Win rate=wins/defined completed-trade denominator; profit factor=sum positive net outcomes/abs(sum negative net outcomes), undefined with no losses. High-water mark includes starting equity; equity 100,120,90,110 has maximum drawdown 25%. Independently compute every reported metric.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Trade sums, symbol attribution, realized/unrealized components, final equity, chart extrema and risk reports must reconcile. Do not average symbol win rates without weighting; chart downsampling cannot alter metrics.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-33"></a>

## 33. Backtest UI and run configuration

```text
PROMPT 33 — Backtest UI and run configuration

PURPOSE AND ARCHITECTURE
Let an operator configure, launch and inspect a reproducible run without hiding unsupported inputs or failed jobs.

DESIGN SOURCES
DD01 backtest and dashboard requirements; DD04 Vue, REST and frontend authority; UI organization is a proposed design
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Use the browser to select strategy/version, symbols, dates, capital, risk, data revision and cost/execution settings; compare submitted payload with the form.
2. Inspect progress, Overview/Trades/Symbols or equivalent implemented views, chart tooltips, trade detail, filters, pagination, refresh and rerun.
3. Exercise invalid/reversed dates, missing coverage, duplicate start clicks, cancellation, failed run, no trades, open positions, long symbol names and narrow screens.

MATH / VALIDATION ORACLE
Compare displayed numbers and chart points directly with authoritative API responses and independent metric fixtures; verify timezone, currency, sign and percentage scaling.

CONTINUITY AND CROSS-LAYER ASSERTIONS
A cloned run preserves resolved configuration but receives new identity. Filtered trade lists must not silently relabel full-portfolio metrics; refreshed UI cannot revert completed status or hide errors.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-34"></a>

## 34. Walk-forward, robustness, and experiment registry

```text
PROMPT 34 — Walk-forward, robustness, and experiment registry

PURPOSE AND ARCHITECTURE
Evaluate hypotheses without tuning and reporting on the same data; treat uncertain thresholds as research questions.

DESIGN SOURCES
DD01 §§96–101; DD02 research hypotheses/ablations; DD03 §183; DD04B §11.4
Scope classification: Conditional / research capabilities. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. If implemented, create chronological train/validation/test windows and a fixed holdout; execute baseline and one ablation on identical frozen inputs.
2. Test overlapping label horizons, purging/embargo policies, insufficient sample sizes, failed trials, duplicate experiment submission and parameter-neighborhood sensitivity.
3. For Monte Carlo, test reproducible seeds and configured resampling dependence assumptions; do not imply independent trades when exposure clusters exist.

MATH / VALIDATION ORACLE
Recompute split membership and aggregate out-of-sample metrics; labels available after a boundary cannot leak into fitting. Report uncertainty and multiple-search limitations rather than declaring a single best trial validated.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Link experiment→dataset/code/parameters→trial→results→promotion evidence. A successful research job must not directly grant live authority.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-35"></a>

## 35. Feature dashboard, data trust, and stock detail

```text
PROMPT 35 — Feature dashboard, data trust, and stock detail

PURPOSE AND ARCHITECTURE
Show trustworthy measurement context and explain degraded states without confusing connection health, snapshot refresh and market freshness.

DESIGN SOURCES
DD01 §§15, 116–118, 125; DD05 §§67–70, 132–138; DD04 frontend authority
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Compare dashboard, per-symbol detail, series and diagnostic responses for the same observation/timeframe.
2. Test fresh snapshot over stale candles, connected transport with absent market producer, warm-up, missing optional/required features, invalid numbers and all-unavailable rows.
3. Open issue reasons, filter affected rows, switch Features/Setups/instrument/timeframe, and refresh or reconnect without losing selection.

MATH / VALIDATION ORACLE
Recompute state counts and unique affected rows; overlapping missing-metric counts cannot be summed as unique stocks. Only an authoritative risk/data gate may claim trading permission.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Values, timestamps, units, versions and reason codes must match across views. Display unavailable distinctly from zero; preserve essential reasons outside hover-only controls.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-36"></a>

## 36. Realtime REST/WebSocket continuity and resynchronization

```text
PROMPT 36 — Realtime REST/WebSocket continuity and resynchronization

PURPOSE AND ARCHITECTURE
Keep browser state aligned with authoritative snapshots despite dropped, duplicated or reordered realtime messages.

DESIGN SOURCES
DD04 §§43–44, 48–52; DD05 observation and event identity
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Fetch an initial snapshot, subscribe, mutate test state and compare stream updates with subsequent REST reads.
2. Drop sequence numbers, duplicate/reorder messages, reconnect after disconnection, restart sequence scope and change accounts/subscriptions.
3. Test snapshot/update race, deleted watchlist rows, multiple browser tabs, backpressure and unknown message schema versions.

MATH / VALIDATION ORACLE
Define sequence scope from the actual contract. After resync, compare a canonicalized browser state with a fresh authoritative snapshot; no stale update may overwrite newer state.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Maintain entity ID, observation/revision, account, sequence and timestamp throughout. A stream connected indicator cannot assert market-data freshness or order finality.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-37"></a>

## 37. Trade journal, decisions, and performance analytics

```text
PROMPT 37 — Trade journal, decisions, and performance analytics

PURPOSE AND ARCHITECTURE
Explain executed and skipped decisions and calculate performance from execution evidence, with separate editable operator notes.

DESIGN SOURCES
DD01 §§119–122, 137; DD04B immutable audit and trade lineage
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Complete a simulated trade, fetch journal/detail, add a note, filter by symbol/strategy/regime/time and retrieve daily aggregates.
2. Include rejected and skipped setups, multiple partial fills, revised fees, no-trade days and trades crossing date boundaries where supported.
3. Test duplicate event delivery, journal generation rerun, pagination totals, missing lineage and unauthorized note edits.

MATH / VALIDATION ORACLE
Recompute net P&L, R, MFE/MAE conventions and weighted group statistics from fixtures. Human notes must not alter immutable fills or historical decision evidence.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Trace market observation through strategy/risk/plan/order/fill/exit/journal; daily reports, portfolio and backtest accounting must use compatible scopes and definitions.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-38"></a>

## 38. Alerts, diagnostics, and operational observability

```text
PROMPT 38 — Alerts, diagnostics, and operational observability

PURPOSE AND ARCHITECTURE
Surface actionable incidents and explain system behavior without treating counters as current-state authority.

DESIGN SOURCES
DD01 §§123–125, 137; DD05 §§283–287; DD04 observability
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Trigger test setup/risk/feed/order incidents and observe in-app/test-sink alerts, metrics, logs and resolution.
2. Test duplicate storms, suppression windows, severity transitions, acknowledgement, delayed recovery and failure of the alert sink.
3. Compare cumulative counters since process start with current affected entities; restart and validate counter-reset semantics.

MATH / VALIDATION ORACLE
Reconcile alert counts against unique triggering events under the documented dedup policy. Independently calculate latency timestamps; specify percentile window and units.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Carry correlation/causation IDs and instrument/account references across APIs, events and alerts. Use an isolated sink; do not send real messages to recipients as part of this audit.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-39"></a>

## 39. Dataset catalog, revisions, storage, and compaction

```text
PROMPT 39 — Dataset catalog, revisions, storage, and compaction

PURPOSE AND ARCHITECTURE
Keep large analytical history in versioned files and operational authority/catalogs in PostgreSQL; experiments reference immutable data.

DESIGN SOURCES
DD05 §§75–90, 254–263, 306–338; DD04B §§9, 11, 15–16
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Create a small dataset via its supported job, query manifest/coverage, validate files/checksums and consume it in a feature/backtest read.
2. Test partial upload, corrupted file, manifest/file count mismatch, schema evolution, revision lineage, concurrent publication and missing partitions.
3. Compact/rebuild in isolation and verify observation identity and economic contents; test tombstone/deletion policy only on disposable data.

MATH / VALIDATION ORACLE
Independently count rows, unique keys, timestamp bounds and checksums. Compare pre/post-compaction ordered contents rather than filenames. Do not fabricate successful manifests for missing files.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Dataset identity, version, source revision, observation keys and experiment references must remain traversable. Corrected history creates a new revision rather than mutating prior results.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-40"></a>

## 40. Pattern windows and market-memory representations

```text
PROMPT 40 — Pattern windows and market-memory representations

PURPOSE AND ARCHITECTURE
Represent ordinary and setup-related historical state/trajectory without encoding future outcomes in pattern identity.

DESIGN SOURCES
DD05 §§177–209; DD04B §11.2
Scope classification: Conditional / market-memory implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Materialize windows for an ordinary observation and a setup anchor; query same-stock, sector and multi-resolution context.
2. Test insufficient lookback, missing optional dimensions, adjusted-price scale changes, different schema versions and repeated materialization.
3. Revise source history and verify a new pattern revision while preserving the old experiment; test anchor/window temporal boundaries.

MATH / VALIDATION ORACLE
Independently reconstruct the sequence ending at the anchor and its declared return/ATR/volume normalization. Missingness masks and feature order must agree with schema metadata.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Anchor observation, window bounds, dataset/feature/pattern revisions and stored vector must agree. Future success/failure labels remain separate.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-41"></a>

## 41. Historical similarity and index retrieval

```text
PROMPT 41 — Historical similarity and index retrieval

PURPOSE AND ARCHITECTURE
Retrieve comparable prior patterns with transparent cohorts and sample size; similarity has no initial trading authority.

DESIGN SOURCES
DD01 §61; DD05 §§210–240, 267–270
Scope classification: Conditional / research and advisory. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Call real similarity APIs for same-stock, same-regime, sector and supported global cohorts; inspect returned matches and metadata.
2. Test mandatory historical as_of_limit, k=0/negative/oversized, empty corpus, self-match, adjacent-window duplicates, incompatible schema and stale index.
3. Compare approximate retrieval with exact search on a small corpus; disable the index and exercise documented fallback.

MATH / VALIDATION ORACLE
Compute distances/ranking independently under the selected metric; assert temporal eligibility and diverse-episode policy. A similarity score is not a calibrated win probability.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Query schema/cohort/as-of/index version must match returned pattern IDs and outcome availability. Future matches or labels unavailable at decision time must never influence historical decisions.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-42"></a>

## 42. Outcome labels, as-of joins, and ML dataset integrity

```text
PROMPT 42 — Outcome labels, as-of joins, and ML dataset integrity

PURPOSE AND ARCHITECTURE
Create future outcome labels separately from contemporaneous features and include ordinary, skipped and rejected observations.

DESIGN SOURCES
DD01 §§104–107; DD02 §136; DD05 §§241–263
Scope classification: Conditional / research pipeline. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Materialize forward return/MFE/MAE/target-stop labels on a hand-checkable price path, then build a small training dataset.
2. Test insufficient future horizon, session close, missing data, same-bar target/stop ambiguity, label revision and delayed label availability.
3. Test as-of joins for sector mappings, corporate actions, higher-timeframe features, rolling normalization and chronological split boundaries.

MATH / VALIDATION ORACLE
Define price basis, direction, horizon, R denominator and censoring before calculating labels. Recompute extrema and returns independently; never call unknown target ordering a win.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Feature rows cannot include future outcome fields; training records retain anchor and label-availability times, schema/revision and selection provenance. Market-path labels are not realized execution P&L.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-43"></a>

## 43. ML inference, authority, calibration, and drift

```text
PROMPT 43 — ML inference, authority, calibration, and drift

PURPOSE AND ARCHITECTURE
Evaluate only permitted model influence after deterministic qualification, preserving model/schema lineage and hard risk ceilings.

DESIGN SOURCES
DD01 §§8–9, 49–58, 107–115; DD03 §§85–95; DD04B §10.2
Scope classification: Conditional / staged ML capability. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. If implemented, score identical valid/invalid setups under OBSERVER, RANKER, FILTER and RISK_REDUCER authority.
2. Test absent model, schema mismatch, timeout, NaN/out-of-range probability, stale model, low sample size and drift-triggered downgrade.
3. Compare deterministic baseline with model-assisted decisions; test deployment windows and research-role attempts to promote a model.

MATH / VALIDATION ORACLE
Probabilities must be in [0,1]; independently compute Brier/log-loss or selected calibration bins on a known label set. A model may not create an invalid trade or exceed deterministic risk.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Prediction inputs, model/feature versions, authority, fallback and final decisions must remain linked. Historical runs cannot use a model trained on their future. Resolve broad product examples against stricter current authority policy.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-44"></a>

## 44. Derivatives analytics and instrument selection

```text
PROMPT 44 — Derivatives analytics and instrument selection

PURPOSE AND ARCHITECTURE
Compare supported instruments only after an underlying opportunity qualifies; options cannot rescue an invalid underlying setup.

DESIGN SOURCES
DD01 §§62–66; DD02 §149; DD03 derivatives risk; DD04B derivative contracts
Scope classification: Conditional / later instrument scope. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. If supported, query chains/contracts, select expiry/strike and compare equity/future/option candidates with actual account capability.
2. Test stale/crossed quotes, missing Greeks/IV/OI, wide spreads, expiry cutoff, invalid lot size, unsupported products and missing historical option data.
3. Exercise defined-risk versus unsupported unlimited-risk structures and explicit rejection rather than assumed liquidity.

MATH / VALIDATION ORACLE
Check contract multiplier, lots versus units, premium/notional/margin distinctions and model/version/unit conventions. Validate Greeks with an independent implementation only when the pricing assumptions are specified.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Underlying setup→instrument selection→risk approval→plan must retain consistent identity and bounded risk. Do not impose equity stop-distance sizing on derivatives.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-45"></a>

## 45. Database migrations, constraints, and transaction integrity

```text
PROMPT 45 — Database migrations, constraints, and transaction integrity

PURPOSE AND ARCHITECTURE
Enforce financial invariants in real PostgreSQL, preserving immutable evidence and making projections rebuildable.

DESIGN SOURCES
DD04 jOOQ/Flyway; DD04A corrections; DD04B §§2–6, 13, 17–21
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Apply migrations to fresh and supported upgrade databases; generate/validate jOOQ schema compatibility and exercise API writes.
2. Attempt test-only invalid cross-tenant/account/instrument references, overlapping temporal mappings, duplicate orders/fills, rejected-decision plans and over-approved quantities.
3. Inject failure at each risk-approval and fill-processing transaction step; verify all-or-nothing behavior and retry idempotency.

MATH / VALIDATION ORACLE
Use actual PostgreSQL constraints and isolation behavior rather than H2 approximations. Inspect numeric scale, UUID/BIGINT roles, timestamps, uniqueness scope and immutable-update protection.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Decision/plan/reservation/account state and fill/order/position/risk/audit transitions must reconcile atomically. Audit envelopes complement rather than replace order-event evidence.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-46"></a>

## 46. Architecture, API contracts, build, and performance

```text
PROMPT 46 — Architecture, API contracts, build, and performance

PURPOSE AND ARCHITECTURE
Verify module boundaries, generated contracts and measured operational behavior without introducing unnecessary distributed infrastructure.

DESIGN SOURCES
DD04 entire stack and engineering standards; DD05 storage/query benchmarking
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Run existing build, architecture, integration and API contract checks; inspect dependency direction and Java/Python ownership.
2. Call representative read/write endpoints for schema validation, pagination, unknown enum/version, malformed body, large identifiers and decimal precision.
3. Measure a bounded representative event/replay/API workload; inspect queue bounds, latency, hot-path database calls and memory growth.

MATH / VALIDATION ORACLE
Use declared budgets where present; otherwise report measured workload/hardware/percentiles without inventing pass thresholds. Check JSON large-ID precision in TypeScript and decimal rounding end to end.

CONTINUITY AND CROSS-LAYER ASSERTIONS
OpenAPI/DTO/client/runtime responses must agree. Features/strategy/risk stay framework-light and in-process; research Python cannot mutate production authority. Build and replay evidence must identify the same commit.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-47"></a>

## 47. Operational readiness, deployment, backups, and recovery

```text
PROMPT 47 — Operational readiness, deployment, backups, and recovery

PURPOSE AND ARCHITECTURE
Prove an isolated deployment can start safely, recover durable evidence and refuse new exposure when critical dependencies fail.

DESIGN SOURCES
DD01 §§133–148; DD04 §§68–72 and recovery/operations; DD05 backups and bounded queues
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Start/restart a test stack, verify readiness gates, inject database/object-store/feed unavailability and recover.
2. Restore a disposable backup and compare ledger/catalog identity; test bounded queue overflow according to recoverable-versus-critical data policy.
3. Exercise competing execution instances, configuration mismatch, failed startup migration and interrupted worker recovery without deploying production.

MATH / VALIDATION ORACLE
Measure lost/duplicated events and compare restored critical-state checksums/counts to a known snapshot. Critical financial intent cannot be silently dropped to satisfy latency.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Readiness, trading permission, restored protection, reconciliation and audit must agree. An HTTP health endpoint being UP is insufficient evidence that trading may resume.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-48"></a>

## 48. Compliance configuration and data-use restrictions

```text
PROMPT 48 — Compliance configuration and data-use restrictions

PURPOSE AND ARCHITECTURE
Enforce configured broker/segment/order/data-use restrictions without treating dated design-document regulatory descriptions as current legal advice.

DESIGN SOURCES
DD01 §§127–130; DD05 §§88–89, 336–337; DD03 compliance gate
Scope classification: Conditional / configured controls; legal validation separate. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Inspect configured capabilities and their provenance/effective dates; use test rules for allowed/blocked segments, order tags, rate limits and trading windows.
2. Test missing or expired policy, conflicting higher-priority prohibitions, unlicensed export flags and cross-tenant dataset access.
3. Verify a manual UI action or model cannot bypass a configured hard restriction; perform no live registration, trading or external disclosure.

MATH / VALIDATION ORACLE
Use approved versioned policy as the software oracle. If current real-world rules are needed, verify official sources separately and identify unresolved interpretation; do not invent numeric limits.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Policy revision, decision reason, enforcement layer and audit must be traceable. Unsupported compliance workflows are a gap, not a passed test.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```

<a id="prompt-49"></a>

## 49. Full-system continuity and regression journey

```text
PROMPT 49 — Full-system continuity and regression journey

PURPOSE AND ARCHITECTURE
Prove that the product operates as one consistent trading system rather than a set of individually successful endpoints.

DESIGN SOURCES
DD01 full lifecycle; DD02 deterministic replay; DD03 risk authority; DD04B canonical lineage; DD05 point-in-time integrity
Scope classification: Core / inspect implementation. Confirm actual implementation; this label does not assert it exists.

PRACTICAL SCENARIOS TO EXECUTE
1. Create an isolated account/watchlist, resolve instruments/calendar, load persisted history and warm dependencies.
2. Replay a known positive control through features→setup→risk→plan→simulated order→partial/full fill→position→exit→journal→metrics; capture each ID and response.
3. Repeat with a valid setup rejected by risk, stale required data, an expired plan, duplicate requests and mid-lifecycle restart.
4. Run the equivalent historical/backtest path and inspect UI; compare shared pre-execution decisions and documented execution differences.

MATH / VALIDATION ORACLE
Prepare a small independent ledger of expected states, quantities, fees, P&L and equity before execution. Reconcile every adjacent boundary and final totals, including residual reservations and positions.

CONTINUITY AND CROSS-LAYER ASSERTIONS
Produce a lineage table of entity IDs, timestamps, versions and ownership for each step; all links must resolve. No unit-test-only or fixture-injected shortcut qualifies as end-to-end evidence.

EXECUTION CONTRACT — follow this without needing any other prompt
You are auditing Edge Relative, not merely proposing tests. Discover the repository instructions, supplied design documents, running services, actual OpenAPI/controllers/clients, authentication and test commands. Use the named document titles as well as DD numbers because the Dev Stack file labels itself DD-04A while a separate DD04A DB Schema exists. Cite exact source sections and code when establishing expected behavior. Apply specific mathematical/risk/physical-schema requirements over broad examples; record unresolved conflicts instead of silently choosing business policy.

Map implemented endpoints and internal boundaries for this feature. Do not invent paths or create public test-only endpoints. Start necessary local/test services where available. Execute real HTTP requests and relevant streams; use real persistence for integration checks. If a function has no public endpoint, drive its existing application/job/event boundary and label that coverage accurately. Use local/paper/simulated execution and disposable accounts; no live orders, real notifications, production deployments, destructive production operations or secret disclosure.

Discover existing behavior before changing code. Reproduce defects, diagnose the first failing boundary, make scoped reversible fixes supported by established requirements, add meaningful regression checks, and rerun the affected journey. Do not weaken strategy/risk gates, fabricate successful responses, or implement missing major subsystems merely to obtain a pass. Mark absent functionality NOT_IMPLEMENTED, unavailable prerequisites BLOCKED, ambiguous requirements SPEC_GAP, failed assertions FAIL and demonstrated checks PASS. Implemented happy paths alone are insufficient, but do not claim literal exhaustive coverage of an infinite input space.

For each applicable endpoint test valid, missing/null/empty/malformed, unknown identifier, numeric/date/enum boundary, unauthorized ownership, duplicate/retry and concurrency cases. Include exact lower/equal/upper boundaries, and document non-applicable classes. Follow writes with detail/list/history/stream reads and persisted evidence. Test reload/restart, asynchronous completion, cancellation and pagination where relevant. Preserve UTC instants and exchange calendar semantics. Explicitly check stale responses, version drift, duplicate events, inconsistent totals and broken entity lineage.

Execute BOTH a realistic available-data journey and isolated deterministic fixtures for otherwise unreachable edges. Keep fixture outcomes separate from real historical evidence. Independently calculate expected math from source rules with declared units, rounding, denominators and tolerances; importing the production calculator as the expected-value oracle is not independent validation. Never interpret HTTP 200, a green unit suite, zero trades or a connected socket alone as feature correctness.

REQUIRED TEST REPORT AND REPRODUCIBLE EVIDENCE
Save a feature-specific report and rerunnable request/test script using repository conventions. Record commit/build, environment, dataset/revision, configuration, clock/seed, account scope, test time and fixture provenance. Capture sanitized method/path/query/body, status, relevant response fields or complete saved response, request/correlation/job IDs, elapsed time and related database/event evidence. Never print credentials.
Provide a scenario table: ID | requirement/source | preconditions/input | expected result/math | observed result | status | evidence reference. Report executed/failed/blocked/not-implemented/spec-gap counts and actual endpoint coverage; retain failed-before and passed-after evidence separately.
For each defect report severity, practical trading/user impact, minimal reproduction, expected-versus-actual response, root cause, affected layer, fix and regression result. Include a dedicated continuity/lineage analysis and expected-versus-actual math reconciliation. Verify browser behavior if tools exist and distinguish browser-tested from API-only. Finish with unresolved limitations and concrete next actions. Never claim an unexecuted scenario passed or hide a blocked stage behind a mock.
```
