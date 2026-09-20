# Corporate actions and adjusted history — audit

Status: executed against the running application and the persisted reference/candle store.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Headline finding

Corporate-action handling is **NOT_IMPLEMENTED** end to end. `reference.corporate_action` exists as a
physical table but **no code reads or writes it**, there is no adjustment factor, no adjusted
analytical series or dataset, no as-of/point-in-time read, and no API surface. The only preserved
invariant is the one the design protects most strongly: **canonical candles hold raw tradable prices
and nothing adjusts them** (DD-05 §112). Every scenario that requires an adjustment is therefore
reported NOT_IMPLEMENTED rather than fabricated.

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | working tree on top of `3500613` |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Fixtures | `reference.corporate_action` rows inserted in a rolled-back transaction; instrument SBIN `id=1` |
| Instrument store | `reference.instrument` (symbol-keyed identity), `reference.instrument_identifier` (0 rows) |
| Candle store | `market.candle` (raw OHLCV; no adjusted columns) |
| Rerunnable script | `scripts/corporate-action-audit.sh` (`BASE=… scripts/corporate-action-audit.sh`) |
| Clock / account scope | no clock dependence; read-only plus rolled-back fixtures; no orders |

Sources: DD01 §132 (splits, bonuses, dividends, rights, mergers, symbol changes), DD-05 §43
(corporate-action reference data), DD-05 §§112–114 (raw vs adjusted, adjustment factors, overnight
gap), DD-05 §260 (corporate-action leakage: raw and adjusted series remain separate and auditable),
DD04B §7.2/§7.4 (symbol history separate from economic identity; corporate actions are first-class
reference data).

## Boundary and endpoint map

| Surface | Path / object | Status | Notes |
| --- | --- | --- | --- |
| Corporate-action table | `reference.corporate_action` (V002:264–288) | SCHEMA_ONLY | 0 rows; no Java reference; `action_type` enum, ratio/cash checks |
| Symbol/alias history | `reference.instrument_identifier` (V002:74–91) | SCHEMA_ONLY | 0 rows; temporal validity + exclusion constraints |
| Instrument identity | `reference.instrument.canonical_symbol` | IMPLEMENTED | identity key = exchange+segment+type+symbol (mutable symbol) |
| Raw candles | `market.candle` / `GET /api/v1/history/candles` | IMPLEMENTED | raw prices; no adjusted columns; M1 base only |
| Adjusted series / factors | — | NOT_IMPLEMENTED | no table, service, dataset or API |
| Corporate-action ingestion | Groww clients | NOT_IMPLEMENTED | no corporate-action endpoint in the broker matrix |
| As-of / point-in-time reads | — | NOT_IMPLEMENTED | no `available_at` on the action table |
| Backtest execution prices | `BacktestEngine` fills | IMPLEMENTED (raw) | `adjustedFill` is slippage only; `BacktestTrade` documents raw tradable prices |

## Scenario table

Live/HTTP+persistence evidence: `scripts/corporate-action-audit.sh` (6 PASS / 0 FAIL / 8 NOT_IMPLEMENTED).

### S1 — apply isolated action fixtures; raw and adjusted on both sides

| ID | Requirement / source | Input | Expected (math) | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-1 | Split adjustment (DD01 §132, DD05 §113) | SPLIT 1:2, ex 2026-10-01 | pre-ex prices × 1/2; quantity × 2 | no adjustment; schema model only | NOT_IMPLEMENTED | `split-bonus-adjustment`; `corporate-action-schema-semantics` |
| CA09-2 | Bonus adjustment | BONUS 1:1 | pre-ex prices × 1/2; quantity × 2 (capitalisation, not face-value change) | no adjustment | NOT_IMPLEMENTED | `split-bonus-adjustment` |
| CA09-3 | Dividend adjustment (must not equal split semantics) | DIVIDEND ₹5.00 cash | additive: pre-ex price − 5 (or factor (P−5)/P); quantity unchanged | no adjustment; ratio vs cash modelled distinctly in schema | NOT_IMPLEMENTED | `dividend-adjustment-distinct` |
| CA09-4 | Symbol change (DD05 §43, DD04B §7.2) | symbol rename | economic instrument stable, symbol versioned alias | identity is symbol-keyed; `instrument_identifier` 0 rows | NOT_IMPLEMENTED | `instrument-symbol-history` |
| CA09-5 | Merger / demerger / delisting | MERGER/DELISTING | continuity mapping to successor/termination | types allowed by schema; no target columns, no handling | NOT_IMPLEMENTED | schema check; report |
| CA09-6 | Raw prices preserved (DD05 §112) | SBIN M1 bar | stored value unchanged, never overwritten | API close `989.7` == stored raw `989.70000000` | PASS | `raw-prices-preserved` |
| CA09-7 | No adjusted substitution (DD05 §107) | `adjust=true` query | raw bars returned; no hidden adjustment | identical output | PASS | `adjust-param-ignored` |
| CA09-8 | No adjusted API surface | OpenAPI | no adjust/corporate paths | 0 of 67 paths | PASS | `no-adjusted-api-surface` |

### S2 — announcement vs effective, revisions, repeated/missing/multiple factors

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-9 | Announcement vs effective date (DD05 §260) | announced then effective | factor known only at availability | no announcement/availability column | NOT_IMPLEMENTED | `announcement-vs-effective`; CA09-D3 |
| CA09-10 | Factor revisions | corrected action | versioned factor, prior retained | no factor dataset/revision model | NOT_IMPLEMENTED | `factor-revisions` |
| CA09-11 | Repeated application | apply factor twice | idempotent, applied exactly once | no application path | NOT_IMPLEMENTED | `factor-revisions` |
| CA09-12 | Missing factor | action without factor | fail closed, do not cross silently | features silently cross (no detection) | NOT_IMPLEMENTED | `adjusted-vs-raw-feature-continuity` |
| CA09-13 | Multiple actions in succession | split then bonus | chained factors compose deterministically | no composition | NOT_IMPLEMENTED | `split-bonus-adjustment` |
| CA09-14 | Malformed action rows rejected | unknown type; unpaired ratio | constraint violation | rejected by `ck_corporate_action_type` / `ck_corporate_action_ratio` | PASS | `corporate-action-schema-rejects` |
| CA09-15 | Fixture isolation | rollback | no residue | 0 rows after rollback | PASS | `fixture-rollback-clean` |

### S3 — backtest across an action; feature continuity vs execution accounting

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-16 | Adjusted analytical series for features (DD05 §113) | history across a split | continuous adjusted features | features compute on raw prices | NOT_IMPLEMENTED | `adjusted-vs-raw-feature-continuity` |
| CA09-17 | Execution uses actual tradable prices (DD05 §113) | fills across a split | raw fill prices | `BacktestTrade` documents raw; `adjustedFill` is slippage only | PASS (by design) | code citation |
| CA09-18 | Position quantity adjustment | bonus/split holding | quantity and cost basis adjusted | no positions/execution subsystem | NOT_IMPLEMENTED | `position-quantity-adjustment` |
| CA09-19 | Corporate-action leakage guard (DD05 §260) | metadata known later | not a contemporaneous feature | no guard; table unused | NOT_IMPLEMENTED | `corporate-action-unwired` |

Counts: PASS 6, FAIL 0, NOT_IMPLEMENTED 13.

## Independent factor oracle (declared units and semantics)

No production calculator exists to import, so the expected transforms are derived directly from the
fixtures. Prices are INR per share; quantities are integer shares; cash is INR.

| Action | Multiplicative price factor | Quantity factor | Cash | Notes |
| --- | --- | --- | --- | --- |
| SPLIT 1:2 (1 new : 2 old) | 1/2 = 0.50000000 | ×2 | — | face value halves; capital unchanged |
| BONUS 1:1 | 1/2 = 0.50000000 | ×2 | — | numerically equal factor, different accounting |
| DIVIDEND ₹5.00 | (P − 5)/P, price-dependent | ×1 | ₹5.00/share | **not** a multiplicative split factor |

The oracle is deliberately explicit that dividend and split/bonus adjustments do **not** share
semantics (additive vs multiplicative). None of these transforms is applied anywhere in the system.

## Continuity and lineage analysis

- **Identity**: `CanonicalInstrumentService.ensureInstrument` derives `instrument_key =
  UUID(instrument:<exchange>:<segment>:<type>:<symbol>)`, so a symbol change creates a **new
  `instrument_id`** and splits candle/feature history. `reference.instrument_identifier` (which would
  carry symbol/ISIN versioned aliases) has 0 rows. DD-05 §39 / DD04B §7.2 are unmet.
- **Raw vs adjusted separation**: satisfied only in the trivial direction — the raw series exists and
  no adjustment exists. There is no adjusted dataset, so "separate and auditable" (DD-05 §260) is not
  met because the adjusted side is absent.
- **Feature continuity**: the feature engine consumes `HistoricalDataReader` raw candles directly; a
  split/bonus price jump is treated as a real price move, so ATR/RRS/RVOL and patterns cross actions
  silently. There is no factor lookup, no detection, and no quality downgrade.
- **Execution accounting**: backtest fills use raw historical prices (correct for fills, DD-05 §113);
  there is no position/cost-basis adjustment and no live positions, so bonus-credited quantity is
  unmodelled.
- **Revision lineage for actions**: none; the table has `created_at` only and no availability or
  revision columns, so point-in-time correctness (DD-05 §260) cannot be represented.

## Defect register

| # | Severity | Impact | Repro | Expected vs actual | Root cause | Status |
| --- | --- | --- | --- | --- | --- | --- |
| CA09-D1 | High | Features and candles silently cross splits/bonuses, invalidating backtests (DD01 §132, DD05 §§43/260) | fetch M5 around any action | continuous adjusted features vs raw discontinuity | no adjustment subsystem | NOT_IMPLEMENTED |
| CA09-D2 | High | `reference.corporate_action` is dead schema: no ingestion, no read, no lineage | `SELECT count(*)` = 0; no Java reference | first-class reference data vs unused table | subsystem not built | NOT_IMPLEMENTED |
| CA09-D3 | Medium | The action model cannot express announcement/availability or effective-vs-available timing, so point-in-time (§260) is unrepresentable | schema inspection | `announced_at`/`available_at` vs absent | schema gap | SPEC_GAP |
| CA09-D4 | Medium | Symbol changes split economic identity; no alias history | identity key includes symbol; `instrument_identifier` = 0 | stable identity + versioned alias vs symbol-keyed | identity model gap | SPEC_GAP |
| CA09-D5 | Medium | Merger/demerger/symbol-change targets have no columns (`target_instrument_id`/`new_symbol`); only `metadata` JSONB | schema inspection | representable action target vs absent | schema gap | SPEC_GAP |

No FAIL statuses; no defect was fixable within the audit contract, because every gap is the absence of
the corporate-action subsystem itself. Implementing it here would be building a missing major
subsystem to obtain a pass, which the contract prohibits.

## Verification commands

- `scripts/corporate-action-audit.sh` → 6 PASS / 0 FAIL / 8 NOT_IMPLEMENTED.
- Live schema fixtures ran in a rolled-back transaction; `reference.corporate_action` count stays 0.
- OpenAPI (`/v3/api-docs`) has 0 corporate/adjustment paths.
- No code changed, so the last full `./mvnw -Denforcer.skip=true verify` (application 206,
  broker-groww 79) remains the build baseline; re-run after any implementation.
- Browser: not exercised (no tooling); there is no corporate-action UI.

## Limitations and next actions

Ordered prerequisites to implement the feature (none of which is a green unit test):

1. **Factor model and dataset**: a versioned corporate-action factor dataset (DD-05 §113) with a
   documented transform per action type — multiplicative for split/bonus, additive for dividend,
   identity/mapping for symbol change/merger — and an adjusted analytical series that never
   overwrites raw `market.candle`.
2. **Point-in-time schema**: add announcement/availability timestamps and factor revisions so
   backtests only know actions that were known at the time (DD-05 §260), and extend the action model
   with `target_instrument_id`/`new_symbol` for symbol changes/mergers.
3. **Identity continuity**: key economic identity by a durable identifier (ISIN) or a versioned alias
   in `reference.instrument_identifier`, so a symbol change does not orphan history (DD05 §39,
   DD04B §7.2).
4. **Fail-closed guard**: when a known action's ex-date falls inside a feature/backtest window and no
   factor is available, mark affected data untrustworthy rather than silently crossing it (AGENTS.md
   fail-closed data principle; DD-05 §43).
5. **Execution accounting**: cost-basis/quantity adjustment for bonus/split-affected positions once a
   positions subsystem exists.
6. **Ingestion source**: no broker corporate-action endpoint exists in the Groww matrix; a reference
   data source and reconciliation path are prerequisites (DD-05 §43).
