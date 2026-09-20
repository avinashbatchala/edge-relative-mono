# Broker connections, capabilities and health — audit

Status: executed against the running application, the live read-only Groww path, and the
WireMock-simulated broker suite. Scope classification: Core / implementation inspection.

## Environment and provenance

| Item | Value |
| --- | --- |
| Commit | `07b073b` + the audit script/report in this change |
| Backend | Spring Boot 4.0.8, started from `backend/application` with `secrets.properties` |
| Base URL | `http://localhost:8080` |
| Brokers installed | Groww only (`broker-groww`); one adapter |
| Live data | read-only Groww calls only; no orders placed at any point |
| Account scope | existing operator account; identifiers masked in evidence |
| Governance | `docs/design-docs/dev/groww-endpoint-matrix.md`, `GrowwBrokerAdapter`, `GrowwDisabledExecutionAdapter` |
| Rerunnable script | `scripts/broker-audit.sh` (`BASE=http://localhost:8080 scripts/broker-audit.sh`) |

Sources: DD01 §§18–23 (broker abstraction and boundaries), DD04 (adapter conventions),
DD03 (broker-health risk).

## Endpoint / boundary map

| Layer | Surface |
| --- | --- |
| Edge Relative HTTP (Groww) | `/api/v1/brokers/groww`: `capabilities`, `instruments`, `market-data/{quote,ltp,ohlc,option-chain,greeks}`, `historical/{candles,expiries,contracts}`, `portfolio/{holdings,positions,positions/trading-symbol,user}`, `margin/{user,required}`, `orders*`, `smart-orders*`; mutations: `POST/PUT /orders`, `POST /orders/cancel`, `POST /smart-orders`, `PUT /smart-orders/{id}`, `POST /smart-orders/cancel/...` |
| Ports | `BrokerAdapter`, `MarketDataBroker`, `HistoricalDataBroker`, `InstrumentBroker`, `PortfolioBroker`, `MarginBroker`, `OrderQueryBroker`, `TradeQueryBroker`, `SmartOrderQueryBroker`, `ExecutionBroker`, `SmartOrderBroker` |
| Resilience | `GrowwCallExecutor`, `SlidingWindowGrowwRateLimiter`, `GrowwCircuitBreaker`, `GrowwCooldownManager`, `GrowwErrorDecoder` |
| Health | `GrowwHealthMonitor` → `GrowwHealthStateProvider` → `GrowwHealthIndicator` (Actuator `groww`) |

## Scenario table

Live API evidence: `scripts/broker-audit.sh` (15/15 PASS). Simulated evidence:
`backend/broker-groww` tests (79/79 PASS).

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| BR-01 | Capability advertisement (DD01 §20) | GET `/capabilities` | execution capabilities absent | 17 capabilities; no `ORDER_EXECUTION`/`SMART_ORDER_EXECUTION` | PASS | `capabilities-no-execution` |
| BR-02 | Mutations refused with zero downstream HTTP | POST `/orders` | 501 `BROKER_OPERATION_NOT_ENABLED` | 501 | PASS | `mutation-refused-place` |
| BR-03 | Modify refused | PUT `/orders` | 501 | 501 | PASS | `mutation-refused-modify` |
| BR-04 | Cancel refused | POST `/orders/cancel` | 501 | 501 | PASS | `mutation-refused-cancel` |
| BR-05 | Smart-order create refused | POST `/smart-orders` (valid body) | 501 | 501 | PASS | `mutation-refused-smart-create` |
| BR-06 | Mutation has no HTTP client path | unit | no request emitted | asserted | PASS | `GrowwMutationSafetyTest` |
| BR-07 | Passive health (DD03 broker health) | GET `/actuator/health` | 200 UP | 200 UP | PASS | `actuator-health` |
| BR-08 | Canonical LTP mapping (DD05 field/unit) | GET `/market-data/ltp?segment=CASH&exchangeSymbols=NSE_SBIN` | canonical `{exchangeSymbol,lastPrice}` | `NSE_SBIN, 996.2`, 560–640 ms | PASS | `ltp-canonical-mapping` |
| BR-09 | Malformed symbol remains explicit | `NSE-SBIN` | 400 validation | 400 `BROKER_VALIDATION_FAILED` | PASS | `ltp-malformed-symbol` |
| BR-10 | Unknown enum boundary | `segment=NOPE` | 400 | 400 | PASS | `unknown-segment` |
| BR-11 | Missing required param | no `tradingSymbol` | 400 | 400 | PASS | `missing-param` |
| BR-12 | Order/trade query read | GET `/orders?pageSize=1` | 200 | 200 | PASS | `order-query` |
| BR-13 | Positions read | GET `/portfolio/positions` | 200 | 200 | PASS | `positions` |
| BR-14 | Margin read | GET `/margin/user` | 200 canonical | 200 | PASS | `margin` |
| BR-15 | User profile read | GET `/portfolio/user` | 200 (masked) | 200 | PASS | `user-profile` |
| BR-16 | Instrument master | GET `/instruments` | 200 rows | 200, 138,788 rows | PASS | `instrument-master` |
| BR-17 | Auth expiry handling | simulated 401 | refresh/`BROKER_AUTHENTICATION_FAILED` | asserted | PASS | `GrowwAuthenticationClientExpiryTest`, `GrowwAuthorizedExecutorTest` |
| BR-18 | Rate limiting | simulated 429 | `BROKER_RATE_LIMITED`, retry-after | asserted | PASS | `GrowwErrorDecoderTest`, `SlidingWindowGrowwRateLimiterTest` |
| BR-19 | Retry budget | transient 5xx | bounded retries then failure | asserted | PASS | `GrowwRetryPolicyTest`, `GrowwCallExecutorTest` |
| BR-20 | Circuit breaker / cooldown | repeated failures | open → degraded/unavailable, then half-open | asserted | PASS | `GrowwCircuitBreakerTest`, `GrowwCooldownManagerTest` |
| BR-21 | Malformed broker response | bad JSON | `BROKER_PROTOCOL_ERROR`, no silent default | asserted | PASS | `GrowwErrorDecoderTest` |
| BR-22 | Duplicate auth callbacks suppressed | concurrent refresh | single-flight token | asserted | PASS | `GrowwAccessTokenProviderSingleFlightTest` |
| BR-23 | Second broker failing while another healthy | two adapters | independent health | only one adapter installed | NOT_IMPLEMENTED | Limitations |
| BR-24 | Broker streaming re-subscription | WS subscribe/reconnect | no duplicate subs | no broker WS in adapter | NOT_IMPLEMENTED | Limitations |
| BR-25 | Degraded health reaches risk gate | Groww down | `BROKER_HEALTH_BLOCKED` | health not wired to risk context | NOT_IMPLEMENTED | Continuity |
| BR-26 | Degraded health visible in UI | Groww down | component state | details hidden (`show-details: never`) | NOT_IMPLEMENTED | Continuity |

Counts: PASS 22, FAIL 0, NOT_IMPLEMENTED 4.

## Defect register

No broker-layer defect was reproduced. One behavioural inconsistency was found and scoped:

| # | Severity | Impact | Repro | Expected → Actual | Root cause | Disposition |
| --- | --- | --- | --- | --- | --- | --- |
| B1 | Low | Smart-order mutations with an *invalid* body return `400 BROKER_VALIDATION_FAILED` before the capability refusal, so callers do not see the consistent `501 BROKER_OPERATION_NOT_ENABLED`. Safety holds: no HTTP reaches Groww in either case. | `POST /smart-orders` with `{}` | 501 → 400 | `GrowwMutationController.createSmartOrder` maps enums before delegating to `GrowwDisabledExecutionAdapter` | SPEC_GAP (test uses a valid body; recorded, not changed) |

## Independent math / unit reconciliation

- Canonical LTP: broker `NSE_SBIN` → canonical `{exchangeSymbol:"NSE_SBIN", lastPrice:996.2}`; no unit scaling.
- Quote: `dayChange 7.5`, previous close (`ohlc.close`) `988.7` → independent
  `7.5 / 988.7 × 100 = 0.75857%`; API `dayChangePercent = 0.758571862041064` — matches.
- Latency measured from retained client timestamps: LTP round-trip 560–642 ms across runs
  (`time_total`); health `observedAt` is the server clock.
- Closed-market observation: `averagePrice: null` and zero bid/ask depth at audit time
  (2026-09-20, weekend) — a legitimate "market closed / depth unavailable" state, not zero-filled
  prices (prices are real; depth is explicitly zero/absent).

## Continuity and lineage

- Canonical mapping is broker-neutral: adapters return `Broker*` models; the application maps to
  canonical instruments/timeframes (`CanonicalInstrumentService`, `TimeframeCatalog`). Live reads
  returned canonical shapes with no Groww identifiers leaking beyond `exchangeSymbol`.
- Health is passive (`GrowwHealthMonitor`) and derived from observed outcomes (auth > cooldown >
  circuit), so it never spends quota probing; exposed as Actuator status (`UP` currently).
- **Gap**: degraded broker health does not yet reach the risk gate or the UI. `RiskEvaluator`
  already has a `broker.health` precondition and `RiskReasonCode.BROKER_HEALTH_BLOCKED`, but the
  production `RiskContextProvider` is the fail-closed default returning empty, and
  `management.endpoint.health.show-details: never` hides the `groww` component. No silent source
  switching occurs (there is a single adapter), so this is an observability/wiring gap, not a
  correctness defect.

## Automated tests and coverage

- `scripts/broker-audit.sh`: 15/15 live scenarios (read-only).
- `backend/broker-groww` suite: 79/79, covering auth, rate limit, retry, circuit breaker, error
  decoding, mapper units, historical range split, read clients, and mutation safety.
- Backend `verify`: application 188, broker-groww 79 (last run).
- **Browser: not verified** (no browser tooling); all UI-related claims are API-only.

## Limitations and next actions

- Only one broker adapter exists, so "one broker failing while another stays healthy" is not
  testable; a second adapter or a multi-adapter health registry is required.
- No broker streaming subscriptions exist; duplicate-subscription/reconnect checks are
  not applicable to the request/response adapter.
- Auth expiry, rate limiting, malformed responses and breaker transitions were validated with the
  WireMock suite, not by degrading the live account.
- Next: wire `GrowwHealthStateProvider` into the risk context provider so a degraded broker state
  reaches `BROKER_HEALTH_BLOCKED`, and surface broker health in the workstation (either
  `show-components`/`show-details` policy or a dedicated read endpoint), without weakening the
  fail-closed default.
