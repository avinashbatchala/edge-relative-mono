# RRS math, persistence and benchmark alignment — audit

Status: executed against the running application and the persisted canonical/feature store.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Headline

RRS is **IMPLEMENTED** end to end: one canonical `RrsFeature` computes the DD-02 §33 formula, the
`/api/v1/features` API exposes raw/fast/slow/persistence/slope/acceleration/percentile/trend plus
stock-vs-market and stock-vs-sector, benchmark alignment is exact close-time with **no fallback or
percent-return substitution**, and snapshots persist append-only with parameter-hash lineage. The
independent live oracle reproduces `RRS_RAW` exactly.

This pass fixed the previously recorded gaps: the zero-ATR validity defect (RRS-1), the percentile
minimum-sample guard (RRS-2), the non-contiguous persistence window (RRS-3), and the `/series`
future-mapping leak (RRS-4). Corporate-action-adjusted inputs (RRS-5) and the incremental stream
producer (RRS-6) are now implemented as opt-in capabilities. Sector benchmark instruments (RRS-7)
remain **BLOCKED** on sector-index history.

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | working tree on top of `d199e62` |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Config | `feature.rrs.price-change=CLOSE_TO_CLOSE`, `fast=3`, `slow=8`, `persistence=8`, `slope=3`, `percentile-window=500`, `percentile-min-samples=20`; `feature.atr.length-by-timeframe.M5=600`, `smoothing=WILDER`; `feature.corporate-actions.adjusted-inputs=false`; `feature.stream.producer.enabled=false` |
| Benchmark | `NIFTY50` → canonical index `NIFTY` (`instrument_id=3`) |
| Real journey | SBIN (`id=1`) vs NIFTY, M5, anchor `2026-09-18T10:00:00Z` |
| Fixtures | `RrsFeatureTest`, `FeatureEngineTest`, `FeatureFixtureTest` (`contracts/fixtures/features/rrs-v1.json`) |
| Rerunnable script | `scripts/rrs-audit.sh` (`BASE=… scripts/rrs-audit.sh`) |

Sources: DD-02 §§31–42, §160; DD-05 §§137–151.

## Endpoint / boundary map

| Surface | Path | Status |
| --- | --- | --- |
| Snapshot / series / dashboard / diagnostics / watchlist | `GET /api/v1/features/*` | IMPLEMENTED |
| Canonical inputs | `GET /api/v1/history/candles` (raw + `adjustment=SPLIT_BONUS`) | IMPLEMENTED |
| Core math | `RrsFeature`, `Atr`, `Ema`, `RollingStatistics`, `PriceChange` | IMPLEMENTED |
| Persistence | `market.feature_snapshot(_value)`; `source_data_revision` reflects adjusted inputs | IMPLEMENTED |
| Adjusted inputs (opt-in) | `feature.corporate-actions.adjusted-inputs` | IMPLEMENTED (default off) |
| Incremental producer (opt-in) | `FeatureStreamProducer` + `/ws/features` | IMPLEMENTED (default off) |
| Sector benchmark instruments | `reference.benchmark.instrument_id` | BLOCKED (null; no sector-index history) |

## Scenario table

Live/HTTP evidence: `scripts/rrs-audit.sh` (10 PASS / 0 FAIL / 1 NOT_IMPLEMENTED).
Deterministic fixture evidence: `RrsFeatureTest` (9/9), `FeatureEngineTest` (8/8),
`FeatureFixtureTest` (4/4), `FeatureLeakageTest` (1/1), `FeatureVersioningTest` (3/3),
`FeatureStreamProducerTest` (1/1), `FeatureCorporateActionInputIntegrationTest` (1/1).

### S1 — directional fixtures

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RRS-01 | Equal normalized moves (DD02 §33) | same Δ/ATR | 0 | 0.0 | PASS | `identicalNormalisedMovementYieldsZero` |
| RRS-02/03 | Stock faster / weaker | sign tests | ± | +1.0 / −1.0 | PASS | `strongerStock…` |
| RRS-04 | Market down / stock weaker | sign tests + fixture | negative | covered | PASS | `rrs-v1.json` (tol 1e-9) |
| RRS-05 | Independent live oracle | SBIN vs NIFTY M5 | recompute | expected 1.9855911640 = actual | PASS | `independent-rrs-oracle` |
| RRS-06 | Fast/slow/persistence/accel/trend | fixture | frozen arrays | match | PASS | `rrsFixtureMatches` |

### S2 — edge cases

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RRS-08 | Zero ATR (DD05 §143/§144) | range-less bars | undefined, not a value | INVALID, no value | PASS (RRS-1) | `zeroAtrYieldsAnUnavailableRrs…`; `zeroAtrYieldsNoValidRrsValueInTheSnapshot` |
| RRS-09 | Missing ATR / warmup | < ATR length | unavailable | WARMING_UP/INCOMPLETE | PASS | warmup guards |
| RRS-10 | Mismatched intervals | benchmark close absent | STALE, no value | STALE, raw NaN | PASS | `benchmarkTimestampMismatch…` |
| RRS-11 | Stale benchmark bar | STALE quality | value + STALE | propagates STALE | PASS | `staleBenchmarkBarStillComputes…` |
| RRS-12 | One-bar burst | spike then flat | smoothing limits | EMA fast/slow | PASS | fixture |
| RRS-13 | EMA initialization | first valid raw | mean of first span | deterministic | PASS | `Ema.seriesSparse` |
| RRS-14 | D1/M5 agreement | both timeframes | both computed | exposed; strategy consumes both | PASS | integration + `StrategyEngineTest` |
| RRS-15 | Sector variants | stock vs sector | same formula | emitted when sector resolves | PASS / BLOCKED (RRS-7) | engine test; `sector-benchmark-instruments` |
| RRS-17 | Percentile minimum samples | < min samples | unavailable | guard withholds the percentile (no 1.0) | PASS (RRS-2) | `percentileRequiresTheConfiguredMinimumSamples` |
| RRS-22 | Persistence over a gap | NaN inside window | unavailable, no reach-back | unavailable | PASS (RRS-3) | `persistenceIsUnavailableWhenTheRecentWindowHasAGap` |

### S3 — percentile causality, lineage, adoption

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RRS-16 | Percentile uses no future data | append future bars | earlier anchor unchanged | unchanged | PASS | `rrsRvolRveAndContextAreUnchangedByFutureBars` |
| RRS-18 | Version lineage | version hash | stable, parameter-hashed | `RRS_V1@<hash>` stable; hash folds params incl. `percentileMinSamples` | PASS | `rrs-version-stable`; `FeatureVersioningTest` |
| RRS-19 | Benchmark identity, no substitution | live snapshot | NIFTY50 resolved | marketCode NIFTY50, instrument 3 | PASS | `benchmark-identity` |
| RRS-20 | Persistence append-only, never read | snapshot write | immutable/idempotent | enforced | PASS | `FeatureSnapshotIntegrationTest`; ADR-002 |
| RRS-21 | Series determinism | repeated `/series` | identical | identical | PASS | `series-deterministic` |
| RRS-4 | Series point-in-time benchmark | mapping valid after range start | no future mapping | resolved as of the range start | PASS | code; `series-point-in-time-benchmark` |
| RRS-5 | Adjusted RRS inputs | 1:2 split | ATR halves, RRS invariant | ATR ×0.5, RRS unchanged | PASS (opt-in) | `FeatureCorporateActionInputIntegrationTest` |
| RRS-6 | Incremental producer | changed rows | broadcast only on change | broadcast-on-change | PASS (opt-in) | `FeatureStreamProducerTest` |
| RRS-7 | Sector benchmark instruments | seed sector index | resolvable | benchmark rows have null `instrument_id` | BLOCKED | `sector-benchmark-instruments` |

Counts: PASS 21, FAIL 0, BLOCKED 1.

## Defect register

| # | Severity | Impact | Fix | Regression |
| --- | --- | --- | --- | --- |
| RRS-1 | High | zero ATR produced a `VALID` non-finite RRS | `RrsFeature` marks non-finite normalized values INVALID; `FeatureEngine.rrsRaw` refuses non-finite VALID | `zeroAtrYields…` (2 tests) |
| RRS-2 | Medium | percentile emitted 1.0 from a one-point trailing window (DD-02 §42 vs DD-05 §151 conflict) | window stays inclusive of `t` (DD-05 §151) with a versioned `percentileMinSamples` (production 20) folded into the version hash | `percentileRequiresTheConfiguredMinimumSamples` |
| RRS-3 | Medium | persistence counted the last N finite samples, reaching back past gaps and mixing regimes (DD-02 §35) | persistence now uses a contiguous `window` of recent bars; a gap makes it unavailable | `persistenceIsUnavailableWhenTheRecentWindowHasAGap` |
| RRS-4 | Medium | `/series` resolved sector/benchmark identity at the range end, so a later mapping could leak backwards (DD-05 §141/§260) | `FeatureSnapshotService.series` resolves as of the range start | code; `series-point-in-time-benchmark` |
| RRS-5 | Medium | RRS inputs were always raw, so a split/bonus split the ATR/RRS regime (DD-05 §§112/113) | opt-in adjusted inputs; raw untouched; unsupported action fails closed; `source_data_revision` records the choice | `FeatureCorporateActionInputIntegrationTest` |
| RRS-6 | Low | `/ws/features` never advanced beyond the connect snapshot | opt-in `FeatureStreamProducer` broadcasts `feature.update` only when authoritative content changes | `FeatureStreamProducerTest` |

Failed-before/passed-after retained: RRS-1 failed with `expected not VALID but was VALID`; RRS-2/RRS-3
are new coverage; RRS-5's integration test asserts `adjustedAtr ≈ rawAtr/2` and `adjustedRrs ≈ rawRrs`.

## Independent math reconciliation

`RRS = ΔP_stock/ATR_stock − ΔP_market/ATR_market` (DD-02 §33). Live SBIN vs NIFTY, M5, anchor
`2026-09-18T10:00:00Z`:

| Term | Value |
| --- | --- |
| ΔP_stock / ΔP_market | 3.5000000000 / 4.5500000000 |
| Wilder ATR_stock(600) / ATR_market(600) | 1.5601204408 / 17.6476035213 |
| Expected / API RRS_RAW | 1.9855911640 / 1.9855911640 |

Scale invariance check with a 1:2 split (all bars pre-ex): independent ATR halves and RRS is unchanged,
which the adjusted-inputs integration test asserts (`ATR ×0.5`, `RRS` equal within 1e-6).

## Continuity and lineage analysis

- **Agreement**: `RRS_RAW` availability gates every derivative; a non-VALID raw (now including zero
  ATR) yields unavailable fast/slow/persistence/slope/acceleration/percentile/trend.
- **Alignment**: exact `closeTime`; mismatch is `STALE` with no value; a stale-quality bar computes
  but propagates `STALE`.
- **Lineage**: snapshots record benchmark/sector ids and store `feature_version=RRS_V1@<hash>`,
  `parameter_hash`, and a `source_data_revision` that is `canonical-m1-ca-adjusted-v1` when adjusted
  inputs are enabled. The calculation path never reads the snapshot tables (ADR-002).
- **Point-in-time**: a single snapshot resolves the benchmark at its anchor; a series resolves at the
  range start so a mapping that became valid later cannot leak backwards.
- **Adjusted vs raw**: r aw candles are never modified; the adjusted series is derived on read and
  gated by the corporate-action guard (split/bonus supported; other types fail closed).
- **Open**: sector benchmark instruments are unseeded, so `RRS_VS_SECTOR_RAW`/`SECTOR_RRS_RAW` are
  `BENCHMARK_UNRESOLVED`; a per-anchor (intra-range mapping change) re-resolution is still future work.

## Verification commands

- `scripts/rrs-audit.sh` → 10 PASS / 0 FAIL / 1 NOT_IMPLEMENTED.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=RrsFeatureTest,FeatureEngineTest,FeatureFixtureTest,FeatureLeakageTest,FeatureVersioningTest,FeatureSnapshotIntegrationTest,FeatureStreamProducerTest,FeatureCorporateActionInputIntegrationTest` → pass.
- `./mvnw -Denforcer.skip=true verify` → application 219, broker-groww 79.
- Browser: not exercised (no tooling); this feature is API/DB-only.

## Limitations and next actions

1. **RRS-7 (BLOCKED)**: `reference.benchmark` rows are seeded but their `instrument_id` is null and no
   sector-index instrument/history exists, so sector variants stay `BENCHMARK_UNRESOLVED`. Prerequisite:
   ingest the NSE sector indices (e.g. NIFTY BANK/IT/PSU BANK/PRIVATE BANK) as index instruments with
   canonical history and link `reference.benchmark.instrument_id` / `sector_benchmark_history`.
2. **Adjusted inputs (opt-in)**: enabling `feature.corporate-actions.adjusted-inputs` fails closed for
   any dividend/rights/merger in the window because those factor semantics are not modelled
   (`corporate-action-audit.md`). Implement those types before turning it on broadly.
3. **Intra-range benchmark changes**: `/series` resolves identity at the range start; if a sector
   mapping changes within the range, later bars still use the start mapping. Per-anchor resolution is
   the follow-up.
4. **Producer (opt-in)**: `FeatureStreamProducer` only advances the stream when the canonical store
   changes; there is still no live market-data producer updating that store.
