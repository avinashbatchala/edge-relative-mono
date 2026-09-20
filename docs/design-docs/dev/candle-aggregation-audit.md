# Canonical candles and timeframe aggregation — audit

Status: executed against the running application and the persisted canonical M1 base.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | working tree on top of `3d4d0dd` |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Canonical base | M1 only, `candle_definition_version='er-m1-base-v1'`; aggregates `er-aggregate-v1`, `nse-session-v1` |
| Instruments | SBIN (`id=1`), NIFTY index (`id=3`, structurally zero volume) |
| First-party endpoints | `GET /api/v1/history/candles`, `GET /api/v1/history/coverage` |
| Rerunnable script | `scripts/candle-aggregation-audit.sh` (`BASE=… scripts/candle-aggregation-audit.sh`) |
| Fixtures | broker payloads in `HistoryBackfillIntegrationTest`; deterministic `CandleAggregatorTest` |
| Clock | UTC instants throughout; exchange semantics `Asia/Kolkata` |

Sources: DD-05 §§91–119; DD-02 §17 (no look-ahead: incomplete bars may be used only as explicitly
incomplete).

## Endpoint / boundary map

| Layer | Surface | Status |
| --- | --- | --- |
| HTTP read | `GET /api/v1/history/candles?instrumentId&timeframe&from&to&limit`; M1,M3,M5,M15,M30,H1,H2,H4,D1,W1 | IMPLEMENTED |
| HTTP coverage | `GET /api/v1/history/coverage` | IMPLEMENTED |
| Query service | derives on read; half-open `[from,to)`; limit clamp `[1,10000]`; most-recent-N; in-progress bars marked incomplete | IMPLEMENTED |
| Aggregator | M1 passthrough + M3/M5/M15/M30/H1/H2/H4/D1/W1; session-anchored; `partial`; `GOOD`/`INCOMPLETE`/`NO_TRADES` | IMPLEMENTED |
| Registry | `TimeframeCatalog` (10 codes) + `reference.timeframe` (V011) | IMPLEMENTED |
| Persistence | `market.candle` append-only revisions (`is_current`, `revision_no`) | IMPLEMENTED |
| Validation / incidents | §117 checks at the write boundary + `market_data_incident` `CORRUPT_DATA` | IMPLEMENTED |
| Special / shortened sessions | `market.calendar.special-sessions` overrides (§116) | IMPLEMENTED |
| VWAP / trade_count | derived and M1 carry `null`; source supplies neither | BLOCKED (CA-5) |
| Lateness cutoff + finalization latency | in-progress marking implemented; no lateness window/latency metric | PARTIAL |
| Live candle builder / live-as-seen revisions API | no live producer; revisions not exposed | NOT_IMPLEMENTED |

## Scenario table

Live/HTTP+persistence evidence: `scripts/candle-aggregation-audit.sh` (8/8 PASS).
Deterministic/fixture evidence: `CandleAggregatorTest` (10/10), `NseTradingCalendarTest` (6/6),
`HistoryBackfillIntegrationTest` (18/18).

### S1 — known history to M1/M5/D1 and other aggregates

| ID | Requirement / source | Input | Expected (math) | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA-S1-1 | OHLCV from M1 (DD05 §§93/107/108/118) | SBIN M1 2026-09-18 | independent Python bucketing | M5=74, M15=25, H1=7, H4=2 exact | PASS | `independent-aggregation-oracle` |
| CA-S1-2 | D1 canonical session (DD05 §99) | same day | open 03:45Z, close 10:00Z, OHLCV=ΣM1 | matched; volume 5,626,812 | PASS | oracle |
| CA-S1-3 | D1/W1 session anchoring (DD05 §§93/100) | week 2026-09-14 | week OHLCV=Σweek M1; canonical boundaries | matched; W1 close 10:00Z | PASS | oracle; `w1AnchorsToCanonicalSessionBoundaries…` |
| CA-S1-4 | Missing minutes ≠ zero (DD05 §§103/104) | 362/375 minutes | bar INCOMPLETE, no fabricated bars | D1 INCOMPLETE; empty bucket emits no bar | PASS | oracle |
| CA-S1-5 | Finalized-vs-in-progress (DD05 §§101/102) | bar closing after `to` | `is_complete=false` | last bar `complete=false` when `closeTime > to` | PASS | `barsClosingAfterTheRequestedEndAreMarkedIncomplete` |

### S2 — duplicates, out-of-order, no-trade vs missing, boundaries, lateness, shortened/special bars

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA-S2-1 | Duplicate writes do not double-count (DD05 §108) | repeated M1 write | idempotent | no duplicate current rows | PASS | `one-current-revision` |
| CA-S2-2 | Out-of-order creates a revision (DD05 §105) | late bar | prior revision retained | append-only revision asserted | PASS (store) | `recordsAnAppendOnlyRevisionWhenABarChanges` |
| CA-S2-3 | No-trade vs missing feed (DD05 §§103/104) | zero-volume vs absent minute | distinct states | covered zero-volume → `NO_TRADES`; absent → `INCOMPLETE` | PASS | `no-trades-vs-missing` (NIFTY 75/75); `fullyCoveredZeroVolumeIntervalIsNoTradesNotMissingData` |
| CA-S2-4 | Exact close boundary (DD05 §§93/98) | 15:30 IST; post-close 15:35 | excluded | excluded; last M5 closes 10:00Z | PASS | oracle |
| CA-S2-5 | Configured lateness cutoff + latency (DD05 §102) | late tick | finalized after cutoff, latency measured | no cutoff window or metric | NOT_IMPLEMENTED | CA-7 residual |
| CA-S2-6 | Shortened final intraday bar (DD05 §98) | H4/H1/H2 | final bar `partial` | H4 last `partial=true`, close 10:00Z | PASS | oracle |
| CA-S2-7 | Special/shortened sessions (DD05 §116) | Muhurat / ad-hoc close | explicit session definition | configurable per-date override; D1/M30 boundaries and length honoured | PASS | `specialSessionOverridesMakeAWeekendTradingDay…`; `specialSessionShiftsBucketBoundariesAndD1Close` |
| CA-S2-8 | Persisted base matches its definition (DD05 §§99/115) | backfilled pre/post prints | only session minutes stored | pre-fix 11,738 SBIN non-session rows → 0 | PASS (after CA-1) | `base-session-purity` |

### S3 — corrections/revisions, overlaps, malformed input

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CA-S3-1 | Correction retains logical identity (DD05 §§92/105/106) | changed bar | new revision, prior non-current | revision lineage asserted | PASS (store) / NOT_IMPLEMENTED (live-as-seen API) | integration test |
| CA-S3-2 | No unexpected overlap (DD05 §117) | same instrument/timeframe | one bar per interval | one-current invariant (0 violations / 1,465,284) | PASS | `one-current-revision` |
| CA-S3-3 | Malformed OHLC validation (DD05 §117) | high<low, negative volume | not persisted, incident | rejected + `CORRUPT_DATA` incident | PASS | `malformedCandlesAreNotPersistedAndRaiseAnIncident` |
| CA-S3-4 | Unknown timeframe (DD05 §96) | `timeframe=XX` | 400 `HISTORY_INVALID` | 400 `HISTORY_INVALID` | PASS | script |
| CA-S3-5 | Reversed/equal range | `from >= to` | 400 | 400 `HISTORY_INVALID` | PASS | script |
| CA-S3-6 | Limit lower boundary | `limit=0` | clamped to 1 | 1 most-recent bar | PASS | script |
| CA-S3-7 | Malformed instant | `from=notadate` | 400 in the project envelope | 400 `REQUEST_INVALID` | PASS (after CA-2) | script; `malformedQueryParameterUsesTheStableErrorEnvelope` |
| CA-S3-8 | Case-insensitive timeframe | `timeframe=m5` | M5 bars | M5 bars | PASS | probe |

Counts: PASS 16, NOT_IMPLEMENTED 2 (CA-S2-5 lateness latency, CA-S3-1 live-as-seen API), BLOCKED 1
(CA-5 VWAP/trade_count), FAIL 0.

## Defect register

| # | Severity | Impact | Minimal reproduction | Before → after | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- |
| CA-1 | High | Canonical M1 base stored pre-open/post-close/non-trading prints as `er-m1-base-v1`/GOOD/complete while reads excluded them; inflated coverage and broke definition lineage | non-session row count in base | 59,510 rows → 0; SBIN count 472,276 → 460,538 | shared `NseTradingCalendar.isSessionMinute`; filter in `BackfillChunkWriter`; V020 purge | `persistsOnlyCanonicalSessionMinutes…`; `base-session-purity` |
| CA-2 | Low | Malformed query parameters returned Spring's default error document | `from=notadate` | default `{timestamp,status,error,path}` → `{code:"REQUEST_INVALID",message}` | global `RequestExceptionHandler` for bind/conversion/body errors | `malformedQueryParameterUsesTheStableErrorEnvelope`; `reject-malformed-instant` |
| CA-3 | Low | W1 `open_time`/`close_time` tracked the first/last contributing minute, not canonical session boundaries | weekly bar with a missing final minute | close `09:59Z` → `10:00Z`; open always the first session's open | session-anchor the W1 bucket start and close | `w1AnchorsToCanonicalSessionBoundaries…` |
| CA-4 | Medium | A real zero-volume interval and a missing feed interval were encoded identically | NIFTY M5 session | INCOMPLETE/GOOD → `NO_TRADES` for covered zero-volume buckets | `QUALITY_NO_TRADES` in the aggregator; `BarQuality` maps it to `UNAVAILABLE` | `fullyCoveredZeroVolumeIntervalIsNoTradesNotMissingData`; `no-trades-vs-missing` |
| CA-6 | Medium | Malformed source OHLC/volume was persisted as a valid canonical bar; no incident | backfill `high<low` / negative volume | persisted → rejected + `CORRUPT_DATA` incident | `wellFormed` filter + `HistoryRepository.recordIncident` | `malformedCandlesAreNotPersistedAndRaiseAnIncident` |
| CA-7 | Medium | Derived bars were always `complete=true`, so confirmed-close consumers could use an in-progress bar | read with `to` inside a bucket | `complete=true` → `false` when `closeTime > to` | `HistoricalDataQueryService.markInProgressIncomplete` | `barsClosingAfterTheRequestedEndAreMarkedIncomplete` |
| CA-8 | Medium | Special/shortened sessions could not be represented; weekend special prints were unreadable | Muhurat-style session | not modelled → configurable per-date overrides | `MarketCalendarProperties.special-sessions` + `NseTradingCalendar.Session` | `specialSessionOverrides…`; `specialSessionShiftsBucketBoundariesAndD1Close` |
| CA-5 | — | Candle VWAP/trade_count absent | derived bars | always `null` | not fixable: Groww M1 supplies neither trade-level price×quantity nor trade count; fabricating them would violate DD-05 §110 | BLOCKED (documented) |

Failed-before/passed-after retained: CA-1 (7-row payload persisted 7 rows → 3), CA-3 (`09:59Z` →
`10:00Z`), CA-4 (NIFTY buckets `INCOMPLETE`/`GOOD` → `NO_TRADES`), CA-6 (malformed persisted →
incident), CA-7 (`complete=true` → `false`), CA-2 (default body → `REQUEST_INVALID`).

## Independent math reconciliation

The oracle is a standalone Python implementation of DD-05 §§93/98/99 — not the production aggregator.

| Timeframe | Bars (API = oracle) | Field mismatches | Notes |
| --- | --- | --- | --- |
| M5 | 74 | 0 | one empty bucket emits no bar |
| M15 | 25 | 0 | exact |
| H1 | 7 | 0 | 6 full + 1 truncated (partial) |
| H4 | 2 | 0 | 1 full + 1 truncated (partial) |
| D1 | 1 | 0 | open 03:45Z / close 10:00Z; volume 5,626,812 = ΣM1; INCOMPLETE 362/375 |
| W1 | 1 | 0 | week volume = Σ week M1; close 10:00Z |
| NIFTY M5 | 75 | 0 | all `NO_TRADES` (structural zero volume) |

No tolerance was needed.

## Continuity and lineage analysis

- **Cross-timeframe**: M5/M15/H1/H4/D1/W1 all reconcile to M1 exactly.
- **Revision lineage**: append-only with `revision_no`/`previous_revision_no` and a
  one-current invariant (0 violations over 1,465,284 rows). Correction journey proven by
  `recordsAnAppendOnlyRevisionWhenABarChanges`; no corrected bars exist in the live DB.
- **Definition lineage**: CA-1 was a definition break (rows stamped with a definition that excluded
  them) and is closed; one shared `isSessionMinute` rule now prevents write/read divergence.
- **In-progress**: bars closing after the decision time are `is_complete=false` (DD-05 §101), so
  confirmed-close consumers cannot treat them as final; `qualityState` still carries minute coverage
  and the `NO_TRADES`/missing distinction.
- **Source lineage limit**: candle VWAP/trade_count are absent because the source supplies neither;
  they remain `null` rather than a fabricated approximation.

## Verification commands

- `scripts/candle-aggregation-audit.sh` → 8/8 PASS.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=CandleAggregatorTest,NseTradingCalendarTest,HistoryBackfillIntegrationTest` → 34/34.
- `./mvnw -Denforcer.skip=true verify` → application 206, broker-groww 79.
- Regression re-runs: `scripts/history-audit.sh` 12/12; `scripts/live-ingestion-audit.sh` 3 PASS / 14 NI.
- Browser: not exercised (no tooling); this feature is API/DB-only.

## Limitations and next actions

- CA-5 (BLOCKED): candle `trade_count`/`vwap` need trade-level price×quantity and trade count from
  the source. Neither is in the Groww M1 payload; adding a trade-tick capture path is the prerequisite
  (DD-05 §§48/109/110/111). Until then `null` is the correct representation.
- CA-7 residual: a configurable lateness cutoff and a measured finalization-latency metric are still
  absent (DD-05 §102); only in-progress marking exists.
- CA-S3-1 residual: non-current revisions are retained in `market.candle` but no API exposes the
  live-as-seen view (DD-05 §§64/105).
- If a `NO_TRADES` candle is ever persisted, the `market.candle.quality_state` check constraint
  (V010) must be extended; today `NO_TRADES` exists only on the derived, unpersisted read model.
- Add cross-language candle fixtures under `contracts/fixtures/` (DD-05 §119) for live/replay parity.
