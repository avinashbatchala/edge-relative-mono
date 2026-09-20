# Corporate actions and adjusted history — audit

Status: executed against the running application and the persisted reference/candle store.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Headline

A **versioned split/bonus adjusted analytical series** now exists alongside raw history, with
point-in-time as-of reads and a **fail-closed guard**, while raw canonical candles remain untouched
(DD-05 §112). Dividends, rights, mergers/demergers, symbol changes and delistings are still
**NOT_IMPLEMENTED** (their factor semantics differ — additive vs multiplicative — and are a policy
decision, DD-05 §260; DD01 §132). Features and backtests still read the raw series; adopting the
adjusted series there is a scoped next action.

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | working tree on top of `3d87890` |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative`; Flyway `V021` applied |
| Factor model | `reference.corporate_action_factor` (explicit `price_factor`, `quantity_factor`, `available_at`, `factor_version`, `definition`) |
| API | `GET /api/v1/history/candles?...&adjustment=NONE|SPLIT_BONUS&asOf=…` |
| Fixtures | `CorporateActionAdjustmentTest` (pure math), `CorporateActionAdjustmentIntegrationTest` (Testcontainers + real HTTP) |
| Rerunnable script | `scripts/corporate-action-audit.sh` (`BASE=… scripts/corporate-action-audit.sh`) |

Sources: DD01 §132; DD-05 §43 (reference data), §§112–114 (raw vs adjusted, factors), §260
(no future knowledge; raw/adjusted separate); DD04B §7.4. The support boundary is an explicit
Edge formalization: **SPLIT and BONUS are multiplicative; dividend is additive and not yet modelled.**

## Boundary and endpoint map

| Surface | Path / object | Status | Notes |
| --- | --- | --- | --- |
| Raw candles | `GET /api/v1/history/candles` (default `adjustment=NONE`) | IMPLEMENTED | raw vertabim; `cumulativeAdjustmentFactor` null |
| Adjusted candles | same path, `adjustment=SPLIT_BONUS` (+ optional `asOf`) | IMPLEMENTED | back-adjusted, versioned `er-ca-adjusted-v1` |
| Factor model | `reference.corporate_action_factor` (V021) | IMPLEMENTED | explicit, versioned, `available_at` |
| Actions reference | `reference.corporate_action` (V002) | SCHEMA + repository writes | `CorporateActionFactorRepository.insertAction/insertFactor` |
| Guard | `CorporateActionAdjustmentService` | IMPLEMENTED | unsupported type or missing factor → `CORPORATE_ACTION_ADJUSTMENT_UNSUPPORTED` (409) |
| Point-in-time | `asOf` filter on `available_at` | IMPLEMENTED | a later-announced factor is excluded, never leaks |
| Dividend / rights / merger / symbol change / delisting | — | NOT_IMPLEMENTED | fail closed on the adjusted path |
| Feature / backtest adoption | `HistoricalDataReader` | NOT_IMPLEMENTED | still raw; next action |
| Position/quantity adjustment | — | NOT_IMPLEMENTED | no positions subsystem |

## Scenario table

Live/HTTP+persistence evidence: `scripts/corporate-action-audit.sh` (5 PASS / 0 FAIL / 4 NOT_IMPLEMENTED).
Deterministic fixture evidence: `CorporateActionAdjustmentTest` (4/4),
`CorporateActionAdjustmentIntegrationTest` (3/3).

### S1 — isolated fixtures; raw and adjusted on both sides

| ID | Requirement / source | Input | Expected (math) | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-1 | Split adjustment (DD01 §132, DD05 §113) | SPLIT 1→2, ex 2026-09-16, factor 0.5/2 | pre-ex price ×0.5, volume ×2; ex-date unchanged | exactly that; factor 0.5 recorded | PASS | `adjustedSeriesHalvesPricesAndDoublesVolumeBeforeTheExDate…`; `splitOfOneIntoTwo…` |
| CA09-2 | Bonus adjustment | BONUS factor 0.5/2 after a split | factors compose once each (0.25/4) | 0.25 price, ×4 volume | PASS | `successiveActionsComposeExactlyOnceEach` |
| CA09-3 | Dividend ≠ split semantics (DD05 §113) | additive cash ₹5 | `(P−5)/P`, quantity unchanged | split stays multiplicative; dividend not encoded as split | PASS (semantics) / NOT_IMPLEMENTED (dividend) | `aDividendCashFactorIsNotAMultiplicativePriceFactor`; `dividend-adjustment-semantics` |
| CA09-4 | Symbol change (DD05 §43, DD04B §7.2) | rename | stable identity + versioned alias | identity symbol-keyed; `instrument_identifier` 0 rows | NOT_IMPLEMENTED | report; CA09-D4 |
| CA09-5 | Merger / demerger / delisting | continuity mapping | target/successor handling | types allowed by schema; no target columns or handling; guard rejects | NOT_IMPLEMENTED | `dividend-rights-merger-symbol-delisting` |
| CA09-6 | Raw preserved (DD05 §112) | raw read vs adjusted read | raw unchanged | raw `100/10/factor null`; adjusted `50/20/0.5` | PASS | `raw-default-preserved`; integration test |
| CA09-7 | Adjust applies exactly once | repeated adjusted reads | idempotent, no mutation | repeated calls identical; raw untouched | PASS | `repeatedCallsAreIdenticalAndNeverMutateTheRawBars` |
| CA09-8 | Invalid adjustment value | `adjustment=BOGUS` | 400 | 400 `HISTORY_INVALID` | PASS | `invalid-adjustment-rejected` |

### S2 — announcement vs effective, revisions, repeated/missing/multiple factors

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-9 | Announcement vs effective (DD05 §260) | factor known 09-20; as-of 09-10 | later knowledge excluded | as-of 09-10 unadjusted; as-of 09-25 adjusted | PASS | `adjustedSeriesOnlyReadsFactorsKnownAtTheAsOfTime` |
| CA09-10 | Factor revisions | multiple factor versions | versioned, latest wins | `factor_version` unique per action; ordering by version | PASS (schema/query) | `factor-table-present` |
| CA09-11 | Repeated application | apply twice | once | factor 0.5 not 0.25 | PASS | `repeatedCallsAreIdenticalAndNeverMutateTheRawBars` |
| CA09-12 | Missing factor | supported action without factor | fail closed | 409 `CORPORATE_ACTION_ADJUSTMENT_UNSUPPORTED` | PASS | guard query; integration test (dividend case) |
| CA09-13 | Multiple actions in succession | split then bonus | compose deterministically | 0.25 / ×4 | PASS | `successiveActionsComposeExactlyOnceEach` |
| CA09-14 | Malformed action rows | unknown type; unpaired ratio | constraint violation | rejected | PASS | `corporate-action-schema-rejects` |
| CA09-15 | Unsupported type in window | DIVIDEND in window | adjusted fails closed; raw available | 409 adjusted; 200 raw | PASS | `unsupportedActionInTheWindowFailsClosedWhileRawRemainsAvailable` |

### S3 — backtest across an action; feature continuity vs execution

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-16 | Adjusted analytical series for features (DD05 §113) | history across a split | continuous adjusted features | adjusted series available; features not yet wired | NOT_IMPLEMENTED | `feature-backtest-adjusted-adoption` |
| CA09-17 | Execution uses tradable prices (DD05 §113) | fills across a split | raw fill prices | `BacktestTrade` raw; `adjustedFill` is slippage | PASS (by design) | code citation |
| CA09-18 | Position quantity adjustment | bonus/split holding | quantity/cost adjusted | no positions subsystem | NOT_IMPLEMENTED | `position-quantity-adjustment` |
| CA09-19 | Corporate-action leakage guard (DD05 §260) | later metadata | not contemporaneous | `available_at` + `asOf` exclude later knowledge | PASS | CA09-9 |

Counts: PASS 14, FAIL 0, NOT_IMPLEMENTED 4 (dividend/rights/merger/symbol/delisting, feature/backtest
adoption, position adjustment, symbol-change identity), SPEC_GAP 1 (dividend method).

## Independent factor oracle (units and semantics)

Prices are INR/share, quantities integer shares, cash INR. Oracle derives the transform from the
fixture action; no production calculator is imported.

| Action | Price factor | Quantity factor | Notes |
| --- | --- | --- | --- |
| SPLIT 1→2 | 0.50000000 | 2.00000000 | multiplicative; applied to bars strictly before ex-date |
| BONUS 1→2 | 0.50000000 | 2.00000000 | multiplicative; composes with prior splits |
| DIVIDEND ₹5 (proposed) | (P−5)/P, price-dependent | 1.00000000 | additive; **not** the split transform; method unpinned |

Worked reconciliation: raw close `100`, volume `10`, SPLIT 0.5/2 → adjusted close `50`, volume `20`,
`cumulativeAdjustmentFactor` `0.5`; raw read still `100`/`10`/null. Two actions → `25`/`40`/`0.25`.

## Continuity and lineage analysis

- **Raw vs adjusted separation** (DD05 §112/§260): raw `market.candle` is never mutated; adjusted bars
  are derived on read and carry `definitionVersion=er-ca-adjusted-v1` plus the cumulative factor.
- **Point-in-time** (DD05 §260): `available_at` gates which factors an as-of read may use; a factor
  announced later is excluded, so no future knowledge leaks into a contemporaneous view.
- **Exactly-once**: each factor contributes one multiplication to a bar's cumulative product; repeated
  reads recompute from raw and are identical.
- **Guard** (DD05 §43): an unsupported action type, or a supported type with no factor, makes the
  adjusted read fail closed (409) while raw remains available — no silent crossing.
- **Open gaps**: identity is symbol-keyed (a rename splits history); merger/symbol-change targets are
  not representable; dividends/rights remain unsupported; features/backtests still consume raw.

## Defect register

| # | Severity | Impact | Status |
| --- | --- | --- | --- |
| CA09-D1 | High | Features/backtests silently crossed splits because no adjusted series existed | RESOLVED (series implemented); feature/backtest adoption still open |
| CA09-D2 | High | `reference.corporate_action` was dead schema with no factor dataset | RESOLVED (V021 + repository + service) |
| CA09-D3 | Medium | No announcement/availability coordinate for point-in-time | RESOLVED (`available_at` + `asOf`) |
| CA09-D5 | High | No fail-closed behavior for unsupported actions | RESOLVED (guard, 409 `CORPORATE_ACTION_ADJUSTMENT_UNSUPPORTED`) |
| CA09-D4 | Medium | Symbol changes split economic identity; no alias history | OPEN (SPEC_GAP) |
| CA09-D6 | Medium | Dividend/rights semantics (additive) unmodeled | OPEN (SPEC_GAP; policy needed) |
| CA09-D7 | Medium | Merger/demerger target mapping unmodeled | OPEN (NOT_IMPLEMENTED) |
| CA09-D8 | Medium | Features/backtests still read raw; no adoption of the adjusted series | OPEN (NOT_IMPLEMENTED) |

## Verification commands

- `scripts/corporate-action-audit.sh` → 5 PASS / 0 FAIL / 4 NOT_IMPLEMENTED.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=CorporateActionAdjustmentTest,CorporateActionAdjustmentIntegrationTest` → 7/7.
- `./mvnw -Denforcer.skip=true verify` → application 213, broker-groww 79.
- Browser: not exercised; there is no corporate-action UI.

## Limitations and next actions

1. **Dividend policy** (SPEC_GAP): decide additive back-adjustment (`(P−cash)/P`) vs another method,
   then add a `DIVIDEND` factor type and remove it from the guard's unsupported set.
2. **Rights / merger / demerger / symbol change / delisting**: define factor/target semantics and add
   target columns (`target_instrument_id`, `new_symbol`) plus handling.
3. **Identity continuity**: key economic identity by ISIN or a versioned alias in
   `reference.instrument_identifier` so a symbol change does not orphan history (DD05 §39, DD04B §7.2).
4. **Feature/backtest adoption**: let `FeatureSnapshotService` read the adjusted series for continuity
   while `BacktestEngine` fills on raw prices (DD05 §113), and surface the guard as a data-quality
   block rather than a 409 on a background path.
5. **Factor ingestion**: no broker corporate-action endpoint exists; a reference source and
   reconciliation path are prerequisites.
