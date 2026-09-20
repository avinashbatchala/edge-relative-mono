# RVOL interval, cumulative and daily baselines — audit

Status: executed against the running application and the persisted canonical store.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Headline

RVOL is **IMPLEMENTED** with all three variants in one `RvolFeature`: daily, same-slot interval and
cumulative-to-time, each normalised against prior **valid** sessions only, with MEAN/MEDIAN/
TRIMMED_MEAN/EW estimators and a parameter-hashed version. A live independent oracle reproduces
daily, interval and cumulative RVOL exactly. Two baseline-validity defects were found and fixed:
floor-based `expectedBars` disabled RVOL for timeframes with a partial final bar, and truncated or
holiday-shortened prior sessions were admitted into the daily baseline. The persisted baseline store
(DD-05 §155) and the dashboard's `rvolD1` display remain NOT_IMPLEMENTED.

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | working tree on top of `a53d928` |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` |
| Persistence | PostgreSQL 18.4, `edge_relative` |
| Config | `feature.rvol`: estimator `MEAN`, daily/interval/cumulative lookback 50, `min-samples 20`, `trimmed-fraction 0.1`, `ew-span 20`; `history-days 90` |
| Semantics | slot = `minutesSinceOpen(barOpen)/timeframeMinutes`; τ = `minutesSinceOpen(closeTime)`; baselines from strictly prior sessions |
| Real journey | SBIN (`id=1`), M5, anchor `2026-09-18T10:00:00Z`, 29 valid prior sessions |
| Fixtures | `RvolFeatureTest`, `FeatureFixtureTest` (`contracts/fixtures/features/rvol-v1.json`), `FeatureLeakageTest` |
| Rerunnable script | `scripts/rvol-audit.sh` (`BASE=… scripts/rvol-audit.sh`) |

Sources: DD-02 §§43–48 (source principle, daily/interval/cumulative definitions, robustness,
directional volume), §161 (fixture set); DD-05 §§152–156 (baseline dataset, no future slots,
session-aligned curves, baseline store, day-of-week context).

## Endpoint / boundary map

| Surface | Path | Status |
| --- | --- | --- |
| Snapshot | `GET /api/v1/features/snapshot` → `features.RVOL_D1/INTERVAL/CUMULATIVE`, `RVE`, `DIRECTIONAL_VOLUME_LONG/SHORT` | IMPLEMENTED |
| Series | `GET /api/v1/features/series` | IMPLEMENTED |
| Dashboard | `GET /api/v1/features/dashboard` → `rvolInterval`, `rvolCumulative` (no `rvolD1`) | PARTIAL |
| Diagnostics | `GET /api/v1/features/diagnostics` → `metricGaps.RVOL_INTERVAL/CUMULATIVE` | PARTIAL |
| Core | `RvolFeature`, `SessionModel`, `VolumeBaselineEstimators`, `BaselineEstimatorType` | IMPLEMENTED |
| Persisted baseline store | — | NOT_IMPLEMENTED (DD-05 §155, optional) |

## Scenario table

Live evidence: `scripts/rvol-audit.sh` (5 PASS / 0 FAIL / 2 NOT_IMPLEMENTED).
Deterministic fixtures: `RvolFeatureTest` (9/9), `FeatureFixtureTest` (4/4), `FeatureLeakageTest` (1/1).

### S1 — hand-built prior sessions, all three variants

| ID | Requirement / source | Input | Expected (math) | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RVOL-01 | Daily = today / prior-session mean (DD02 §44) | prior totals 100,100; today 200 | 2.0 | 2.0 | PASS | `dailyRvolUsesOnlyPriorValidSessions` |
| RVOL-02 | Interval = slot / prior same-slot mean (DD02 §45) | slot0 20 vs mean(100,100) | 0.2 | 0.2 | PASS | `intervalRvolComparesTheSameSessionSlot` |
| RVOL-03 | Cumulative = cumulative(τ)/prior cumulative(τ) (DD02 §46) | 20×11 / (10×11) | 2.0 | 2.0 | PASS | `cumulativeRvolComparesEquivalentSessionRelativeTime` |
| RVOL-04 | Do not sum interval ratios for cumulative | live SBIN | cumulative computed independently | cumulative 0.5386905193 ≠ Σ interval ratios | PASS | `live-oracle` |
| RVOL-05 | Live independent oracle | SBIN M5, anchor | recompute from M5 | daily 0.5386905193 / interval 4.3621616814 / cumulative 0.5386905193 (priorN 29) | PASS | `live-oracle` |
| RVOL-06 | Directional volume (DD02 §48) | up/down windows | ratios, zero denominator invalid | `0/200 → 0.0`, `x/0 → INVALID` | PASS | `rvol-v1.json` |

### S2 — edge cases

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RVOL-07 | Opening volume normal for the slot | opening 100, midday 10 | midday RVOL 1.0 | 1.0 | PASS | `openingVolumeDoesNotContaminateMiddayBaseline` |
| RVOL-08 | Abnormal opening volume | opening 20 vs 100 | 0.2 | 0.2 | PASS | `intervalRvolComparesTheSameSessionSlot` |
| RVOL-09 | Missing prior sessions | 1 session, min 2 | INSUFFICIENT_HISTORY | unavailable | PASS | `insufficientSessionsLeaveRvolUnavailableRatherThanOne` |
| RVOL-10 | Zero denominator | prior/invalid zero baseline | INVALID, no value | `no baseline volume` | PASS | `RvolFeature.relative` L142–144 |
| RVOL-11 | Outlier sessions | MEDIAN/TRIMMED_MEAN/EW | robust estimators available | implemented, MEAN default | PASS (available) / NOT_IMPLEMENTED (not default) | `VolumeBaselineEstimators` |
| RVOL-12 | Holiday-shortened session | 74/75 or special session | excluded from baseline | excluded | PASS (after RVOL-2) | `truncatedPriorSessionIsExcludedFromTheDailyBaseline` |
| RVOL-13 | Partial final bar (M30/H1/H2/H4) | 7 H1 bars | session valid, baselines available | available | PASS (after RVOL-1) | `partialFinalBucketDoesNotInvalidateASessionBaseline` |
| RVOL-14 | Session resets | new date | slots/τ reset per session | per-date aggregation | PASS | engine/test structure |

### S3 — exclusion, method/version, lineage

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RVOL-15 | Current session excluded from baseline (DD05 §153) | baseline loop | prior only | starts at `currentSessionIndex-1` | PASS | `dailyRvolUsesOnlyPriorValidSessions` |
| RVOL-16 | No future slots (DD05 §153) | future slot 74 spike | earlier anchor unchanged | unchanged (RRS/RVOL/RVE) | PASS | `noFutureSlotLeaksIntoAnEarlierAnchor`; `FeatureLeakageTest` |
| RVOL-17 | Baseline method/version explicit (DD02 §47, DD05 §152) | snapshot | `RVOL_V1@hash` | `RVOL_V1@10a3e0514e4b`, stable | PASS | `baseline-version-explicit` |
| RVOL-18 | Invalid prior session excluded (DD05 §152) | prior INCOMPLETE | excluded | excluded | PASS | `invalidPriorSessionIsExcludedFromTheBaseline` |
| RVOL-19 | Persisted baseline store (DD05 §155) | baseline cache | versioned records | not implemented | NOT_IMPLEMENTED | `persisted-baseline-store` |
| RVOL-20 | Dashboard `rvolD1` (DD01 display) | dashboard row | rvolD1 exposed | only interval/cumulative | NOT_IMPLEMENTED | `dashboard-rvol-d1` |
| RVOL-21 | Day-of-week context (DD05 §156) | conditional baseline | optional, sample-size exposed | not implemented | NOT_IMPLEMENTED (optional) | DD-05 §156 |

Counts: PASS 17, FAIL 0, NOT_IMPLEMENTED 4.

## Defect register

| # | Severity | Trading/user impact | Minimal reproduction | Before → after | Root cause | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RVOL-1 | High | M30/H1/H2/H4 sessions always carry a truncated final bar; `expectedBars` used floor division, so a complete session had *more* bars than expected and was marked invalid. Every RVOL baseline for those timeframes was permanently unavailable | compute RVOL on 3 full H1 sessions (7 bars each) | `INSUFFICIENT_HISTORY` (baseline null) → `10/70` VALID | `SessionModel.expectedBars = sessionMinutes()/timeframeMinutes` ignored the partial final bar | use the ceiling `(sessionMinutes + tf - 1)/tf` | `partialFinalBucketDoesNotInvalidateASessionBaseline` |
| RVOL-2 | Medium | A prior session with **fewer** bars than expected (truncated feed, holiday-shortened day) was treated as complete, so its understated total entered the daily baseline and biased RVOL | prior session with 74/75 bars and different volume | baseline polluted by truncated total → truncated session excluded; RVOL 1.0 vs ~0.01 | `SessionAgg.complete` only failed on *overflow*, never on missing bars | `valid()` requires exactly `expectedBars` fully-observed bars | `truncatedPriorSessionIsExcludedFromTheDailyBaseline` |

Failed-before/passed-after retained: RVOL-1's regression returned `INSUFFICIENT_HISTORY` before the
fix; RVOL-2's returned a polluted baseline before the fix. Both pass after.

## Independent math reconciliation

Live SBIN M5, anchor `2026-09-18T10:00:00Z` (last bar of the session), 29 valid prior sessions,
estimator MEAN, min-samples 20:

| Term | Value |
| --- | --- |
| Session volume at anchor | numerator for daily and cumulative |
| Anchor slot volume | interval numerator |
| Daily / interval / cumulative baseline | means over 29 valid prior sessions (full totals / anchor slot / cumulative-to-τ) |
| Expected daily / interval / cumulative | **0.5386905193 / 4.3621616814 / 0.5386905193** |
| API RVOL_D1 / RVOL_INTERVAL / RVOL_CUMULATIVE | **0.5386905193 / 4.3621616814 / 0.5386905193** |

Daily equals cumulative here because the anchor is the session's final bar (τ = session close), so
cumulative-to-τ equals the full session volume. The oracle is a separate prior-session grouping/mean
implementation, not the production calculator.

## Continuity and lineage analysis

- **Numerator/denominator**: distinct per variant (session-to-date, single-slot, cumulative-to-τ) and
  not derived from each other; cumulative is never the sum of interval ratios.
- **Validity gate**: a prior session contributes only when it is fully observed (exact `expectedBars`,
  every bar finalized and trustworthy, no overflow). Missing/corporate-event sessions and shortened
  days are excluded (DD-05 §152/§153, DD-02 §161).
- **No leakage**: baselines scan strictly prior sessions; `cumulativeUpTo` refuses when a prior
  session does not extend to the anchor τ, so a future slot cannot leak; `FeatureLeakageTest` proves
  appending future bars leaves an earlier anchor unchanged (RRS/RVOL/RVE/context).
- **Version lineage**: `RVOL_V1@<12-hex parameter hash>` over estimator, lookbacks, min-samples,
  trimmed fraction, EW span and timeframe; persisting a snapshot records this version.
- **Cross-layer**: the strategy volume gate uses all three (`StrategyEngine` `RVOL_FAILED` when none
  crosses its threshold); the dashboard surfaces interval/cumulative.

## Verification commands

- `scripts/rvol-audit.sh` → 5 PASS / 0 FAIL / 2 NOT_IMPLEMENTED.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=RvolFeatureTest,FeatureFixtureTest,FeatureLeakageTest,FeatureEngineTest,FeatureSnapshotIntegrationTest` → pass.
- `./mvnw -Denforcer.skip=true verify` → application 221, broker-groww 79.
- Browser: not exercised; this feature is API/DB-only.

## Limitations and next actions

1. **Persisted baseline store** (DD-05 §155, NOT_IMPLEMENTED): baselines are recomputed per snapshot.
   Add a rebuildable, versioned baseline cache (instrument, slot, lookback, estimator, sample_count,
   value, as-of, feature_version) if recomputation cost demands it.
2. **Dashboard `rvolD1`** (NOT_IMPLEMENTED): the dashboard/diagnostics expose only `rvolInterval`/
   `rvolCumulative`; add `RVOL_D1` to `metricGaps`/row fields for observability (the strategy already
   consumes it).
3. **Robust estimator not default** (DD-02 §47): MEAN is configured; MEDIAN/TRIMMED_MEAN/EW are
   implemented. Corporate-event outliers and abnormal historical days argue for a robust default
   once evidence supports it.
4. **Corporate-action volume**: adjusted inputs (see `corporate-action-audit.md`) scale volume, which
   would affect both numerator and baseline consistently; enabling it for volume baselines is part of
   the broader adjusted-input work.
5. **Day-of-week context** (DD-05 §156, optional): not implemented; only pursue if sample size
   justifies the segmentation and the sample count is exposed.
6. **Shortened/special sessions**: `SessionModel.expectedBars` still uses the **normal** 375-minute
   session, so a configured special session is excluded from baselines (conservative). If shortened
   days should participate, `expectedBars` must become per-date via `NseTradingCalendar.sessionMinutes(date)`.
