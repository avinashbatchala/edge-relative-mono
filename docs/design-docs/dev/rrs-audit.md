# RRS math, persistence and benchmark alignment — audit

Status: executed against the running application and the persisted canonical/feature store.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Headline

RRS is **IMPLEMENTED** end to end: one canonical `RrsFeature` computes the DD-02 §33 formula, the
`/api/v1/features` API exposes raw/fast/slow/persistence/slope/acceleration/percentile/trend plus
stock-vs-market and stock-vs-sector, benchmark alignment is exact close-time with **no fallback or
percent-return substitution**, and snapshots persist append-only with parameter-hash lineage. The
independent live oracle reproduces `RRS_RAW` exactly.

One real defect was found and fixed: a **zero ATR** made the normalized move undefined
(`x/0`/`0/0`) but was marked `VALID` with a NaN value. Remaining items are spec gaps/limitations
(percentile window wording, non-contiguous persistence window, sector-mapping resolved at the range
end, raw-only RRS inputs).

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | working tree on top of `2fb5f85` |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Config | `feature.rrs.price-change=CLOSE_TO_CLOSE`, `fast-length=3`, `slow-length=8`, `persistence-window=8`, `slope-lookback=3`, `percentile-window=500`; `feature.atr.length-by-timeframe.M5=600`, `smoothing=WILDER` |
| Benchmark | `NIFTY50` → canonical index `NIFTY` (`instrument_id=3`) |
| Real journey | SBIN (`id=1`) vs NIFTY, M5, anchor `2026-09-18T10:00:00Z` |
| Fixtures | `RrsFeatureTest`, `FeatureEngineTest`, `FeatureFixtureTest` (`contracts/fixtures/features/rrs-v1.json`) |
| Rerunnable script | `scripts/rrs-audit.sh` (`BASE=… scripts/rrs-audit.sh`) |

Sources: DD-02 §§31–42 (concept, why percent difference is insufficient, raw formula, ATR baseline,
persistence, trend, acceleration, multi-timeframe, daily/intraday rules, sector reuse, percentile),
DD-02 §160; DD-05 §§137–151 (temporal alignment, staleness, NIFTY benchmark, source requirement, raw
feature, smoothing, persistence, trend/acceleration, multi-timeframe, stock-vs-sector,
sector-vs-market, historical percentile).

## Endpoint / boundary map

| Surface | Path | Status |
| --- | --- | --- |
| Snapshot | `GET /api/v1/features/snapshot?instrumentId&timeframe&anchor` | IMPLEMENTED |
| Series | `GET /api/v1/features/series?instrumentId&timeframe&from&to&limit` | IMPLEMENTED |
| Dashboard | `GET /api/v1/features/dashboard?refresh` | IMPLEMENTED |
| Diagnostics | `GET /api/v1/features/diagnostics?refresh` | IMPLEMENTED |
| Watchlist | `GET /api/v1/features/watchlist` | IMPLEMENTED |
| Canonical inputs | `GET /api/v1/history/candles` | IMPLEMENTED |
| Core math | `RrsFeature`, `Atr`, `Ema`, `RollingStatistics`, `PriceChange` | IMPLEMENTED |
| Persistence | `market.feature_snapshot(_value)`, `FeatureSnapshotWriter`, `JdbcFeatureSnapshotRepository` | IMPLEMENTED (append-only; never read by the calculation path) |
| Live incremental | `/ws/features`, `LiveFeatureStore` | PARTIAL (snapshot-only; no producer) |

## Scenario table

Live/HTTP evidence: `scripts/rrs-audit.sh` (5 PASS / 0 FAIL / 2 NOT_IMPLEMENTED).
Deterministic fixture evidence: `RrsFeatureTest` (7/7), `FeatureEngineTest` (8/8),
`FeatureFixtureTest` (4/4), `FeatureLeakageTest` (1/1), `FeatureVersioningTest` (3/3).

### S1 — directional fixtures via the feature API

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RRS-01 | Equal normalized moves (DD02 §33) | stock and market same Δ/ATR | raw = 0 | 0.0 | PASS | `identicalNormalisedMovementYieldsZero` |
| RRS-02 | Stock faster than market | stock Δ/ATR > market | raw > 0 | +1.0 | PASS | `strongerStockYieldsPositiveAndWeakerYieldsNegative` |
| RRS-03 | Market up / stock flat | stock Δ=0, market Δ>0 | raw < 0 | −1.0 | PASS | `strongerStock…` |
| RRS-04 | Market down / stock weaker | both negative, stock more | raw < 0 | covered by sign tests + fixture | PASS | `rrs-v1.json` tolerance 1e-9 |
| RRS-05 | Independent live oracle | SBIN vs NIFTY M5 | recomputed Wilder ATR + formula | expected 1.9855911640 = actual (ΔS/ATR 3.5/1.5601; ΔM/ATR 4.55/17.6476) | PASS | `independent-rrs-oracle` |
| RRS-06 | Fast/slow/persistence/acceleration/trend | fixture | frozen expected arrays | match to 1e-9 | PASS | `rrsFixtureMatches` |
| RRS-07 | No percent-return substitution | live snapshot | RRS = ATR-normalized value, not % diff | matches oracle | PASS | `independent-rrs-oracle` |

### S2 — edge cases

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RRS-08 | Zero ATR (DD05 §143/§144) | open=high=low=close | undefined, not a value | **was VALID+NaN → now INVALID, no value** | PASS (after fix) | `zeroAtrYieldsAnUnavailableRrsRatherThanAValidNonFiniteValue`; `zeroAtrYieldsNoValidRrsValueInTheSnapshot` |
| RRS-09 | Missing ATR / warmup | < ATR length bars | unavailable | `WARMING_UP`/`INCOMPLETE` | PASS | `Atr.series` NaN warmup; `derived()` guard |
| RRS-10 | Mismatched intervals | benchmark close absent | `STALE`, no value | STALE, raw NaN | PASS | `benchmarkTimestampMismatchIsAStaleQualityCondition` |
| RRS-11 | Stale benchmark bar | present but quality STALE | value with STALE quality | VALID + STALE quality propagates | PASS | `staleBenchmarkBarStillComputesButPropagatesQuality` |
| RRS-12 | One-bar spike followed by neutrality | spike then flat | fast smoothing limits effect | fast = EMA(3) of raw | PASS (formula) / SPEC_GAP (no burst metric) | `rrs-v1.json` fast/slow |
| RRS-13 | EMA initialization | first valid raw | seed = mean of first `span` valid | deterministic | PASS | `Ema.seriesSparse`; `expectedFast` |
| RRS-14 | D1/M5 agreement | D1 + M5 snapshots | both computed, same engine | both exposed; strategy consumes both | PASS | `FeatureSnapshotIntegrationTest`; `StrategyEngineTest` |
| RRS-15 | Sector variants | stock vs sector | same formula | `RRS_VS_SECTOR_RAW` emitted when sector resolved | PASS / NOT_IMPLEMENTED (no sector benchmark instrument seeded) | `snapshotCarriesMarketSectorAndStockRelativeFeatures` |

### S3 — percentile causality and lineage

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RRS-16 | Percentile uses no future data | append future bars | earlier anchor unchanged | unchanged | PASS | `rrsRvolRveAndContextAreUnchangedByFutureBars` |
| RRS-17 | Percentile prior-only (DD02 §42) | window at t | only observations before t | includes t (DD05 §151 allows "before or at t"); no min-sample guard | SPEC_GAP | `percentile` L166–187 |
| RRS-18 | Benchmark/version change lineage | version hash | stable, parameter-hashed | `RRS_V1@<hash>` stable across reads; hash folds benchmark/timeframe/params | PASS | `rrs-version-stable`; `FeatureVersioningTest` |
| RRS-19 | Benchmark identity, no substitution | live snapshot | NIFTY50 resolved, no proxy | marketCode NIFTY50, instrument 3 | PASS | `benchmark-identity` |
| RRS-20 | Persistence is append-only and never read | snapshot write | immutable, idempotent | unique key, triggers reject mutation, calculation path rebuilds from candles | PASS | `FeatureSnapshotIntegrationTest`; ADR-002 |
| RRS-21 | Series determinism | repeated `/series` | identical | identical | PASS | `series-deterministic` |

Counts: PASS 18, FAIL 0 (after fix), SPEC_GAP 1, NOT_IMPLEMENTED 2 (live incremental producer,
corporate-action-adjusted RRS inputs; sector benchmark instrument seeding).

## Defect register

| # | Severity | Trading/user impact | Minimal reproduction | Before → after | Root cause | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RRS-1 | High | A stock or benchmark whose bars have no range (zero ATR — e.g. halted/zero-volume minutes) produced `RRS_RAW` marked `VALID` with a `NaN`/`Infinity` value. A snapshot could be VALID with a non-finite value, violating "missing is not zero" and feeding strategy/risk a meaningless strength reading | `RrsFeature.compute` on zero-range subject and benchmark | `availability=VALID, value=NaN` → `availability=INVALID, value=null` | `raw[i] = change/atr` divided a finite-but-zero ATR; only ATR finiteness was checked, not the result | check `Double.isFinite(value)` in `RrsFeature` (INVALID/UNAVAILABLE) and harden `FeatureEngine.rrsRaw` against non-finite VALID | `zeroAtrYieldsAnUnavailableRrsRatherThanAValidNonFiniteValue`; `zeroAtrYieldsNoValidRrsValueInTheSnapshot` |

Failed-before evidence: the new `RrsFeatureTest` failed with `expected not VALID but was VALID`; it
passes after the fix.

## Independent math reconciliation

Formula oracle (DD-02 §33): `RRS = ΔP_stock/ATR_stock − ΔP_market/ATR_market`. The arithmetic
example (deltas 4 and 2, ATRs 2 and 4 → 1.5) holds as pure arithmetic; note that a Wilder true range
is at least the absolute close-to-close move, so a single-bar ratio > 1 requires a smoothed ATR over
bars with smaller ranges.

Live reconciliation (SBIN vs NIFTY, M5, anchor `2026-09-18T10:00:00Z`, 90-day window):

| Term | Value |
| --- | --- |
| ΔP_stock | 3.5000000000 |
| ΔP_market | 4.5500000000 |
| Wilder ATR_stock(600) | 1.5601204408 |
| Wilder ATR_market(600) | 17.6476035213 |
| Expected RRS | 3.5/1.5601204408 − 4.55/17.6476035213 = **1.9855911640** |
| API RRS_RAW | **1.9855911640** |

Tolerance: exact to 10 decimal places (well within 1e-6); the oracle is a separate implementation of
Wilder TR/ATR and the CLOSE_TO_CLOSE delta, not the production calculator.

## Continuity and lineage analysis

- **Raw/derived agreement**: `RRS_RAW` availability gates fast/slow/persistence/slope/acceleration/
  percentile/trend in `FeatureEngine`; a non-VALID raw yields unavailable derivatives (now including
  the zero-ATR case).
- **Benchmark alignment** (DD05 §137): exact `closeTime` equality; a mismatch is `STALE` with no
  value, never a nearest/latest substitute. A stale-quality benchmark bar still computes but
  propagates `STALE` quality (DD05 §138).
- **Identity/lineage** (DD05 §§144/149): snapshots record `market_instrument_id`, `sector_id`,
  `sector_instrument_id`; values persist with `feature_version=RRS_V1@<hash>` and `parameter_hash`.
  The calculation path never reads the snapshot tables (ADR-002).
- **Persistence**: append-only with immutable triggers; deterministic `snapshot_key` makes writes
  idempotent; the async writer is bounded and drops (observable) rather than blocking.
- **Open lineage issues**: `/series` resolves the benchmark/sector identity once at the range end, so
  a sector mapping change mid-range would apply the later mapping to earlier bars (currently no
  numeric effect because sector benchmark instruments are unseeded); RRS inputs are raw candles, so a
  split/bonus is crossed (corporate-action-adjusted adoption pending).

## Verification commands

- `scripts/rrs-audit.sh` → 5 PASS / 0 FAIL / 2 NOT_IMPLEMENTED.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=RrsFeatureTest,FeatureEngineTest,FeatureFixtureTest,FeatureLeakageTest,FeatureVersioningTest,FeatureSnapshotIntegrationTest` → pass.
- `./mvnw -Denforcer.skip=true verify` → application 215, broker-groww 79.
- Browser: not exercised (no tooling); this feature is API/DB-only.

## Limitations and next actions

1. **Percentile policy** (SPEC_GAP): DD-02 §42 says "only prior observations"; DD-05 §151 allows
   "before or at t". The implementation includes `t` and has no minimum-sample guard, so the first
   finite raw yields percentile 1.0. Decide the documented window and add a minimum-sample/warmup
   requirement; a semantic change requires a new RRS feature version.
2. **Persistence window** (SPEC_GAP): `persistence` counts the last `window` **finite** raw samples,
   skipping NaNs, so it is not a contiguous "recent bars" window. Decide gap semantics (contiguous vs
   finite-sample) and document it.
3. **Series point-in-time identity** (SPEC_GAP): resolve sector membership per anchor rather than once
   at the range end so historical `/series` cannot use a later mapping.
4. **Adjusted inputs** (NOT_IMPLEMENTED): feed RRS the split/bonus-adjusted series for continuity
   while keeping execution on raw prices (see `corporate-action-audit.md`).
5. **Live incremental RRS** (NOT_IMPLEMENTED): no market-data producer feeds `LiveFeatureStore`, so
   the stream remains snapshot-only.
6. **Sector variants** (NOT_IMPLEMENTED): seed the NSE sector benchmark instruments so
   `RRS_VS_SECTOR_RAW`/`SECTOR_RRS_RAW` are available rather than `BENCHMARK_UNRESOLVED`.
