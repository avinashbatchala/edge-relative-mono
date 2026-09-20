# Backtest engine audit

Status: executed against the running application and persisted PostgreSQL history.
Scope classification: Core / implementation inspection (label asserts intent, not existence).

## Environment and provenance

| Item | Value |
| --- | --- |
| Commit at audit | `b20088b` + the fixes in this change (uncommitted at time of writing) |
| Backend | Spring Boot 4.0.8, Java 25 target, started from repo (`backend/application`) with `secrets.properties` |
| Base URL | `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (Docker `edge-relative-mono-postgres-1`), database `edge_relative` |
| Canonical data | M1 base candles, revisions frozen (`is_current`), 2021-09-21 … 2026-09-18 |
| Clock / seed | application `Clock` (UTC), run `seed` recorded in the immutable run spec |
| Account scope | single local operator; there is no authentication/authorization layer (see Limitations) |
| Rerunnable script | `scripts/backtest-audit.sh` (`BASE=http://localhost:8080 scripts/backtest-audit.sh`) |

Design sources: DD01 §§67–69 (trade plan/immutable intent/sizing), DD01 §§92–99
(shared logic, execution, costs, point-in-time, chronological evaluation), DD04 §40
(canonical Java backtester), DD03 §§149–160/182–194 (risk decisions, portfolio-aware risk),
DD05 §§67–70/132–133 (quality/freshness, revision freeze).

## Endpoint / boundary map (discovered, not invented)

| Method | Path | Boundary |
| --- | --- | --- |
| GET | `/api/v1/backtests?limit=` | list runs (`BacktestController`) |
| POST | `/api/v1/backtests` | start / idempotent re-request (`BacktestService.start`) |
| GET | `/api/v1/backtests/{runKey}` | run detail + metrics + parameters |
| POST | `/api/v1/backtests/{runKey}/cancel` | cancellation |
| GET | `/api/v1/backtests/{runKey}/trades?symbol=&limit=&offset=` | simulated ledger |
| GET | `/api/v1/backtests/{runKey}/equity` | marked-to-market equity series |
| GET | `/api/v1/strategies`, `/api/v1/risk-policies`, `…/templates/parameters` | catalog references |
| GET | `/api/v1/watchlist` | finite symbol universe |
| GET | `/api/v1/history/candles`, `/api/v1/features/series` | chart/replay sources |

There is no public backtest "positions"/"orders" endpoint; orders/fills are represented in
the run-scoped `stageCounts` diagnostics and the trade ledger (positions are opened/closed
within a run, not exposed as a live table). Coverage is labelled accordingly below.

## Scenario table

Evidence: `scripts/backtest-audit.sh` output (this run) plus cited tests.

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| BT-01 | List runs (DD01 §116) | GET /api/v1/backtests | 200, list | 200 | PASS | audit `list-runs` |
| BT-02 | Unknown run (DD04B lineage) | GET unknown UUID | 404 | 404 | PASS | audit `unknown-run-404` |
| BT-03 | Fail-closed validation | symbols=[] | 422 actionable | 422 "Select at least one symbol." | PASS | audit `empty-symbols-422` |
| BT-04 | Date boundary | end < start | 422 | 422 "Choose a valid start and end date." | PASS | audit `bad-dates-422` |
| BT-05 | Symbol resolution | unknown symbol | 422 | 422 "Unknown instruments: [NOPE_XYZ]" | PASS | audit `unknown-symbol-422` |
| BT-06 | Versioned policy | contextSource=NOPE | 422 | 422 (context validation) | PASS | audit `bad-context-422` |
| BT-07 | Full historical journey | 3 symbols, 2026-08-01…09-18, derived research | SUCCEEDED, ledger readable | SUCCEEDED; trades 200; 1 fill/exit | PASS | audit `research-run-terminal`, `research-trades-200` |
| BT-08 | Ledger reconciliation | same run | netPnl = Σ trade net; equity reconciles | netPnl −519.4; equity 1,000,000 → 999,480.6 | PASS | below |
| BT-09 | Symbol filter | ?symbol=SBIN / RELIANCE | 200, consistent | 200; RELIANCE 1, SBIN 0 | PASS | audit `trades-symbol-filter` |
| BT-10 | Idempotent start (DD04B durable intent) | repeat identical config | same runKey, no duplicate | same runKey | PASS | audit `idempotent-start` |
| BT-11 | Legitimate zero-trade | strict context | SUCCEEDED, explained | 0 trades; 5,078 missing-dependency reasons | PASS | audit `strict-zero-trade-explained` |
| BT-12 | Deterministic positive control | engine fixture (isolated) | exact fills/costs/equity/DD | asserted | PASS | `BacktestEngineEndToEndTest` |
| BT-13 | Persisted positive-control fixture | `ERFIXTURE` M1 dataset via API | ≥1 completed trade | 0 trades (45 M5 bars derived, all anchors failed) | FAIL | fixture note |
| BT-14 | Browser UI journey | Overview/Trades/Chart | metrics match API | not executed (no browser tooling) | BLOCKED | Limitations |
| BT-15 | Unauthorized ownership | tenant/account scoping | rejected | no auth layer exists | NOT_IMPLEMENTED | Limitations |
| BT-16 | Partial fills / cancellations | execution adapter | modelled | not modelled | NOT_IMPLEMENTED | Limitations |

Counts: PASS 12, FAIL 1, BLOCKED 1, NOT_IMPLEMENTED 2.

## Defect register (failed-before → passed-after)

| # | Severity | Impact | Repro | Expected → Actual (before) | Root cause | Layer | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D1 | Critical | Trades silently not persisted; run "succeeds" with empty ledger | Start two runs with identical window/scoring | ledger has trade → ledger empty while metrics count it | candidate/plan/trade keys not run-scoped; `ON CONFLICT (trade_key) DO NOTHING` dropped the second run's rows | backtest engine + persistence | include `runKey` in candidate/context keys | BT-07/BT-08; `BacktestEngineEndToEndTest` |
| D2 | High | Ledger unreadable; UI Trades tab broken | `GET /trades` without `symbol` | 200 list → 500 | `(? IS NULL OR t.symbol = ?)` binds untyped NULL; PostgreSQL rejects parameter type | persistence | build WHERE conditionally | BT-07, BT-09 |
| D3 | High | Wrong symbol labels/filtering | Run over RELIANCE | symbol "RELIANCE" → symbol "2" | engine stored instrument id as symbol | engine | map instrument → canonical symbol | BT-09 |
| D4 | High | Selecting a catalog placeholder version 500s the start call | `strategyVersionId=1` (empty parameters) | 422 → 500 | `{}` mapped into `StrategyParameters` primitive doubles | catalog + API | validation failure + UI hides invalid versions | BT-06 sibling; `CatalogIntegrationTest` |
| D5 | Medium | Valid configs omitting optional fields rejected | POST without `strictProducers`/`warmupBars`/`seed` | 422/start → 400 | Jackson `FAIL_ON_NULL_FOR_PRIMITIVES` on absent primitive record components | API contract | boxed optionals with service defaults | BT-03…BT-06 |

## Expected-vs-actual math (independent calculation)

Real historical trade (run `c1c9fd65-a4b6-3a42-93d6-c62f2232695e`):
`RELIANCE LONG, entry 1276.00, exit 1273.35, qty 196, costs 0 (USER_ZERO_ASSUMPTION), exit STOP`.

- Gross = (exit − entry) × qty = (1273.35 − 1276.00) × 196 = −2.65 × 196 = **−519.40** (matches).
- Net = gross − explicit costs = −519.40 − 0 = **−519.40** (matches).
- Final equity = starting + net = 1,000,000 − 519.40 = **999,480.60** (equity series last point matches).
- Max drawdown = **519.40** (−0.0519%) (matches).
- Realized R = net / (initialRiskPerUnit × qty) = −3.1176; implied initial risk ≈ 166.6 currency.

Deterministic fixture (`BacktestEngineEndToEndTest`) independently asserts: protective-stop
placement, next-bar fill, gap-through-stop at the open (not the stop), itemized cost
breakdown, `net = gross − costs`, randomized reconciliation `finalEquity = starting +
realized + open-marked`, and drawdown ≥ 0.

## Continuity, lineage and stream checks

- **Revision freeze**: canonical M1 is read `is_current` only; the immutable manifest checksum
  is persisted in the run spec (`datasetChecksum`) and the run key derives from the resolved
  spec, so a later data revision cannot silently change past results.
- **Idempotency**: identical config → identical `runKey` → returns the existing run; FAILED/
  CANCELLED retries create a new linked run (never overwrite).
- **Lineage**: backtest trades carry `instrument_id` + canonical `symbol`; stage counts are
  run-scoped. The `ERFIXTURE` fixture dataset was removed after the attempt (DELETE 1875 candles
  + 1 instrument) so it cannot contaminate historical evidence.
- **Async status**: polled to terminal; `CREATED`/`RUNNING` never masquerade as complete.

## Fixture note (BT-13)

A distinct `ERFIXTURE` instrument with 1,875 generated M1 candles (5 sessions,
2026-09-07…09-11) was seeded and run through the same API. It produced 0 trades: the reader
derived only 45 M5 bars (9/session) instead of 75, so nearly every anchor failed
`LIQUIDITY_FAILED`/`MISSING_REQUIRED_DEPENDENCY` and no setup qualified. This is a fixture
construction limitation (M1→M5 aggregation coverage), reported as **FAIL** rather than hidden.
The engine-level deterministic fixture (BT-12) remains the reproducible positive control.

## Coverage and non-applicable classes

Documented, exercised: list/get/404, empty/date/symbol/context validation, real run lifecycle,
ledger + equity, symbol filter, idempotency, strict zero-trade with reasons.
Declared not-applicable or not implemented: auth/ownership (no security layer), partial fills,
order cancellation/expiry persistence, browser UI automation, malformed-JSON body (covered by
framework 400).

## Automated tests run

- `BacktestEngineEndToEndTest` (3), `BacktestMetricsTest` (3), `BacktestRunRowTest`,
  `BacktestPresetsTest`, `CatalogIntegrationTest` (5).
- Backend `./mvnw -Denforcer.skip=true verify` → application 188, broker-groww 79.
- Frontend `typecheck`, `lint`, `test` (87), `build`.

## Limitations and next actions

- Persisted positive-control fixture needs a correct full-session M1 generator (or an
  aggregation-coverage fix); until then, fixture evidence is engine-level only.
- No authentication/tenant scoping exists, so unauthorized-ownership checks cannot be run.
- Costs default to the explicit `USER_ZERO_ASSUMPTION`; supply a cost schedule for realistic fees.
- Partial fills, order cancellation/expiry persistence, and browser UI verification remain open;
  the latter has no tooling in this environment.
