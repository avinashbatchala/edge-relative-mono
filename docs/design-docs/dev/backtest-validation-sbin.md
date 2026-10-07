# Focused Backtest Validation — SBIN, Multi-Timeframe (ER_RS_CONTINUATION_V1)

Forensic validation of the deterministic backtest against the design documents (DD-02 strategy
principles, DD-03 risk, DD-05 market data). Two questions are kept strictly separate:

1. **Does the implementation behave as designed?** (behavioral correctness)
2. **Is there an exploitable edge after costs, risk, regime and out-of-sample?** (research performance)

---

## A. Test scope

| Field | Value |
|---|---|
| Ticker | SBIN (secondary: HDFCBANK) |
| Instrument id | 1 (SBIN), 5 (HDFCBANK) |
| Exchange | NSE cash equity |
| Sector | PSU_BANK (mapped; no sector benchmark candles available) |
| Market benchmark | NIFTY (instrument 3) |
| Persisted data | M1 only; higher timeframes derived on read |
| Requested window | 2023-10-01 → 2026-09-18 |
| Context-valid window | 2023-09-21 → 2026-09-18 (~3.0y; NIFTY history starts 2023-09-21) |
| Strategy | `ER_RS_CONTINUATION_V1` v1 (RESEARCH), family `M5_3_8_CONFIRMATION` |
| Risk presets | `RESEARCH_PERMISSIVE` (2% risk, 10% session budget), `RESEARCH_CONSERVATIVE` (0.5%/3%) |
| Timeframes run | M1, M3, M5, M15, M30, H1, D1 |
| Context source | `DERIVED_RESEARCH` (deterministic derivation; STRICT fails closed on event risk) |
| Cost models | zero-cost diagnostic; realistic NSE intraday (`NSE_INTRADAY_RESEARCH_V1`) |
| Seed / determinism | engine ignores seed; re-runs verified identical |

---

## B. Data-quality validation

From the canonical store (`market.candle`, all `is_current`):

| Instrument | M1 bars | Sessions | Coverage |
|---|---:|---:|---|
| SBIN | 460,538 | 1,230 | 2021-09-21 → 2026-09-18 |
| HDFCBANK | 459,253 | 1,227 | 2021-10-08 → 2026-10-07 |
| NIFTY | 275,291 | 735 | 2023-09-21 → 2026-09-18 |

- **Session completeness**: 1,182 / 1,230 SBIN sessions have the full 375 M1 bars; ~48 sessions
  (mostly recent) are short (362/367 bars). The aggregator marks affected derived bars `INCOMPLETE`
  and confirmed-close consumers correctly skip them.
- **Corporate actions**: none recorded (`reference.corporate_action` empty); SBIN's last split
  (Jun-2021) predates the window, so raw = adjusted here.
- **Sector context**: SBIN→PSU_BANK is mapped, but no sector-index instrument has candles, so
  `SECTOR_RRS_RAW` is unavailable. Sector is a non-gating quality factor (DD-02 §41/§51), so trades
  still qualify; **sector-alignment analysis is NOT TESTABLE** with current data.
- **Benchmark**: NIFTY starts 2023-09-21, so RRS/market context is unavailable before then and the
  strategy fails closed (correct). Effective decision window ≈ 3 years.
- **Historical warm-up**: features warm from the canonical M1 series; warm-up is session-based.

---

## C. Strategy expectation card (`ER_RS_CONTINUATION_V1`, DD-02)

- **Intent**: intraday continuation of developing relative strength/weakness (M5 setups, daily context).
- **Direction**: long and short symmetric.
- **Required environment**: market bias not opposing; daily aligned; positive/negative persistent RRS;
  adequate RVOL; liquidity; technical void; a confirmed 3/8 EMA trigger; structural invalidation.
- **Lifecycle**: `NONE → WATCH → FORMING → NEAR_TRIGGER → VALID → (INVALIDATED|EXPIRED|MISSED|BLOCKED)`.
- **Entry**: only after the documented trigger; no-chase (missed if extended).
- **Stop**: 3/8 family invalidation = EMA3 recross of EMA8 (a *tight* stop by design, DD-02 §69).
- **Exit**: intraday only; stop/target/thesis; flatten before session close (DD-02 §95–§102).
- **Expected qualitative behavior**: more trades in directional regimes; WATCH ≫ VALID; no prohibited
  session entries; no overnight carry.

---

## D. Strategy × timeframe matrix

| Timeframe | In V1 design scope? | Notes |
|---|---|---|
| M5 | **Yes** (primary) | 3/8 is an M5 family |
| M1, M3 | No (finer than M5) | runs but outside documented V1 setup timeframes |
| M15, M30, H1 | No | runs but V1 is intraday M5; results out-of-design |
| D1 | No | no intraday trigger; 0 trades |

---

## E. Baseline results (SBIN, `DERIVED_RESEARCH`, `RESEARCH_PERMISSIVE`, seed 7)

Zero-cost diagnostic:

| TF | anchors | WATCH | FORMING | NEAR | VALID | fills | completed | net P&L | win % | candidates | rejections |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M1 | 272,323 | 48,791 | 34,267 | 25,640 | 2,230 | 276 | 276 | −91,042 | 5.43 | 2,230 | 1,954 |
| M3 | 90,859 | 17,521 | 11,293 | 8,137 | 168 | 168 | 168 | −60,553 | 7.14 | 168 | 0 |
| M5 | 54,522 | 10,321 | 6,743 | 4,762 | 121 | 121 | 121 | −42,325 | 10.74 | 121 | 0 |
| M15 | 18,180 | 3,215 | 2,269 | 1,196 | 41 | 41 | 41 | −17,174 | 9.76 | 41 | 0 |
| M30 | 9,454 | 1,559 | 831 | 485 | 11 | 11 | 11 | −8,339 | 0.00 | 11 | 0 |
| H1 | 5,090 | 1,137 | 730 | 377 | 2 | 2 | 2 | −1,657 | 0.00 | 2 | 0 |
| D1 | 728 | 120 | 78 | 119 | 0 | 0 | 0 | 0 | — | 0 | 0 |

Realistic NSE intraday cost (`NSE_INTRADAY_RESEARCH_V1`):

| TF | completed | gross | costs | net | win % | expectancy | PF | avg R |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| M3 | 152 | −52,790 | 38,511 | −91,301 | 6.58 | −600.66 | 0.092 | −4.29 |
| M5 | 121 | −41,547 | 31,154 | −72,701 | 7.44 | −600.84 | 0.121 | −4.02 |
| M15 | 41 | −17,089 | 10,777 | −27,866 | 7.32 | −679.66 | 0.136 | −2.90 |
| M30 | 11 | −8,328 | 2,913 | −11,241 | 0.00 | −1,021.89 | 0.000 | −2.57 |
| H1 | 2 | −1,657 | 532 | −2,189 | 0.00 | −1,094.46 | 0.000 | −2.56 |

Second symbol: HDFCBANK M5 zero-cost — 126 trades, net −56,131, win 3.17%, avg R −3.24 (same pattern).
Risk preset: SBIN M5 `RESEARCH_CONSERVATIVE` — 93 trades, net −29,476, win 12.9%, 150 risk-rejected.
(Cost runs differ in trade count from zero-cost because costs change the equity path, which changes
equity-scaled risk budgets and therefore later approvals — not a determinism defect.)

---

## F. Documented vs actual behavior

| Expected (design) | Actual | Match | Root cause / classification |
|---|---|---|---|
| Setups progress NONE→WATCH→…→VALID | **Before fix: state trapped at NONE; zero trades over 5y.** After fix: full funnel (M5 WATCH 10,321→VALID 121) | **MISMATCH (fixed)** | `IMPLEMENTATION BUG` — age-expiry treated `NONE` as an ageing state; `NONE→EXPIRED` is illegal so reconcile collapsed it back to `NONE` forever |
| No look-ahead; features causal | Confirmed (feature engine is causal; timeline values at anchor) | MATCH | — |
| VALID rarer than WATCH | M5 121 vs 10,321; M1 2,230 vs 48,791 | MATCH | — |
| Persistent negative RRS supports shorts | Long/short counts near-symmetric (M5 63/58); both qualify | MATCH | — |
| Opening blackout / session cutoff respected | `OPENING_BLACKOUT`/`SESSION_ENTRY_CUTOFF` reason codes present; no entries in prohibited windows | MATCH | — |
| Intraday only; no overnight | `allowOvernight=false`; 15 SESSION_FLATTEN exits (M5) | MATCH | — |
| Stop-out losses ≈ −1R | **avg realized R −2 to −4** | **MISMATCH** | `STRATEGY/EXECUTION MODEL` — see §L: EMA8 stop is ~0.1% while next-bar-open fills gap 0.2–0.3% |
| Risk owns approved quantity | Quantity capped by notional/concentration; risk rejects M1 candidates 87.6% | MATCH | — |
| Realistic costs materially reduce edge | Costs ≈ 40–60% of gross loss at short TFs | MATCH (hypothesis) | — |

Behavioral mismatches are concentrated in **two implementation bugs** (§M) and one **strategy/execution
interaction** (§L). No look-ahead, no session violation, no long/short asymmetry was found.

---

## G. Lifecycle analysis

Funnel is monotone and legal: WATCH ≥ FORMING ≥ NEAR_TRIGGER ≥ VALID for every timeframe, and VALID
is far rarer than WATCH. Instance continuity and terminal re-arm are verified by unit tests
(`StrategyWatchDiagnosticTest`, `StrategyEngineTest`). M1: 2,230 VALID → only 276 fills because risk
rejects 87.6% of candidates (equity-scaled budgets and concentration), which is the risk layer doing
its job, not a strategy failure.

---

## H. Long vs short (SBIN, zero-cost)

| TF | LONG n / net / win / avgR | SHORT n / net / win / avgR |
|---|---|---|
| M1 | 133 / −51,049 / 2 / −3.39 | 143 / −39,992 / 13 / −2.51 |
| M3 | 78 / −26,539 / 6 / −2.51 | 90 / −34,015 / 6 / −2.69 |
| M5 | 63 / −20,283 / 8 / −2.21 | 58 / −22,043 / 5 / −2.55 |
| M15 | 23 / −9,644 / 2 / −1.71 | 18 / −7,530 / 2 / −1.87 |

Near-symmetric counts and outcomes. This confirms the RRS direction/persistence short-side semantics
are correct.

---

## I. Timeframe analysis

- **Breadth of signals** rises sharply as timeframe falls (M1 candidates 2,230 vs M5 121 vs H1 2).
- **Entry lead time / extension**: not separately instrumented in this pass (gap: needs MFE/MAE
  before/after VALID). Averaged across TFs, M1 enters with the smallest average holding time
  (~0.14h long) and worst avg R; H1/D1 effectively don't trade (V1 is intraday M5).
- **Cost drag** is largest at M1/M3/M5 (costs ≈ 40–60% of gross), smallest at M30/H1.
- **Most trades / worst expectancy** at M1/M3; **fewest** at M30/H1. The design's M5 sits in the
  middle and is the only timeframe actually in scope.

---

## J. Parameter sensitivity

- **Risk preset**: conservative reduced trades 121→93 and net loss −42,325→−29,476 at M5 (smaller
  size, same negative edge) — risk scales exposure, it does not fix a negative signal.
- **Cost model**: zero→realistic roughly doubles the net loss at short TFs.
- Strategy-threshold permutations (persistence, RVOL minima, extension) are **NOT TESTABLE via the
  API**: the backtest request accepts only a preset or a catalog version, not arbitrary
  `StrategyParameters`. This is a documented capability gap (needs an API/catalog extension).

---

## K. Regime analysis

Not completed in this pass. The run-scoped timeline exposes per-anchor `marketStructure`/`marketRegime`,
so regime segmentation (trend/range/dislocated) is possible with one more analysis query. Noted as
follow-up, not a defect.

---

## L. Trade forensics — the dominant performance finding

Sampled M5 trades (DB `initial_risk_per_unit` vs actual exit):

| Dir | entry | risk/unit (planned) | exit | exit reason | quantity | net | R |
|---|---:|---:|---:|---|---:|---:|---:|
| SHORT | 614.50 | 0.30 | 615.60 | STOP | 406 | −447 | −3.67 |
| LONG | 625.60 | 0.65 | 624.30 | STOP | 399 | −519 | −2.00 |
| SHORT | 620.05 | 0.40 | 621.25 | STOP | 402 | −482 | −3.00 |
| SHORT | 609.95 | 0.30 | 611.20 | STOP | 408 | −510 | −4.17 |

The planned protective stop is 0.30–0.70 points from entry (~0.05–0.12%), far tighter than the M5
ATR (~2.5). The fill is entered at the **next bar open**; the bar frequently opens/passes the stop by
0.2–0.3%, so realized losses are **2–4× the planned risk**. This is the documented 3/8 invalidation
(EMA3 recross of EMA8) combined with next-bar-open entry: the stop sits inside bar noise. Behavioral
consequence: the strategy as configured is a systematic loser on this ticker, dominated by
stop-outs with slippage — not by targets.

Trade-level arithmetic is internally consistent (`net = gross − costs`; quantity×risk/unit = planned
risk; equity reconciles), so this is a **strategy/execution-model interaction**, not a bookkeeping bug.

---

## M. Bug findings (all found during this validation)

| # | Severity | File / method | Observed | Expected | Root cause | Fix |
|---|---|---|---|---|---|---|
| M1 | **High** | `StrategyEngine.selectState` / `expiredByAge` | State trapped at `NONE` after 12 bars; **0 trades over 5y** | Active setups expire; `NONE` may re-enter `WATCH` | Age/trigger expiry applied to `NONE`; `NONE→EXPIRED` illegal → reconcile collapsed to `NONE` | Expiry now requires an active state; `barsInState=0` for `NONE`; regression tests added |
| M2 | **High** | `HistoricalDataQueryService.candles` (`MAX_LIMIT=10_000`) | 5-year intraday replay silently truncated to last ~1–6 months | Full requested window | Interactive cap applied to batch reads | Added `replayCandles` + `history.replay` caps; backtest/checksum use it |
| M3 | **High** | `FeatureVersions` / `FeatureEngine` | M1 OOM (heap >3 GB) during pre-loop feature build | Bounded memory | A fresh `FeatureVersion` (with parameter map) built per feature × per anchor (~6M objects) | Memoise versions per (key,timeframe,benchmark) |
| M4 | Medium | `BacktestEngine.historyUpTo` / `candleAt` | O(n²) CPU on long series | O(n) | Linear rescan of the whole series per anchor | Pre-indexed `CandleIndex` (O(1) bar, O(window) history) |
| M5 | Medium | `BacktestConfiguration.backtestExecutor` | Unbounded virtual-thread-per-task; heavy runs could stack | Bounded concurrency | No pool bound | Fixed single-worker pool; submissions queue |
| M6 | Low | `BacktestService.datasetChecksum` | Built one giant manifest string for the whole range | Streaming hash | `StringBuilder` materialised every candle line | Incremental `MessageDigest.update` |

Determinism: repeated baselines produce identical trade counts and P&L (`M3 seed7=168/seed77=168`;
`M5 zc=rc=121`), so M2–M6 did not alter decisions.

---

## N. Documentation ambiguities / untested

- **Sector context** cannot be exercised (no sector-index candles) — the MARKET→SECTOR→STOCK
  hierarchy is untestable.
- **Strategy-threshold permutations** are not exposed by the API.
- **Regime segmentation** and **MFE/MAE-by-lifecycle-state** were not completed in this pass.
- V1 is documented as an M5 intraday strategy; the engine permits other timeframes, so M1/M3/M15/M30/H1/D1
  results are out-of-design and must not be used to judge the documented strategy.
- The 3/8 EMA8 stop is documented but its tightness vs bar noise is a design hypothesis, not a defect.

---

## O. Research findings (separate from software defects)

- After the M1 state-machine fix, the strategy generates a coherent lifecycle on real data.
- On SBIN and HDFCBANK, over the ~3-year context-valid window, the strategy is **consistently
  negative** under zero and realistic costs, at every intraday timeframe, long and short, permissive
  and conservative risk.
- The loss is dominated by **stop-outs at 2–4× planned risk** because the EMA8 stop is far tighter
  than M5 bar noise and entries fill at the next bar open.
- Costs are a first-order drag at M1–M5.

---

## P. Final verdicts

| Strategy / TF | Behavioral correctness | Backtest validity | Performance evidence | Reason |
|---|---|---|---|---|
| ER_RS_CONTINUATION_V1 / M5 (in-scope) | **PASS** (after M1 fix + M2 reader fix) | **VALID** | **WEAK** | Lifecycle/funnel/risk/session correct; negative net at zero and realistic cost, dominated by tight-stop slippage |
| ER_RS_CONTINUATION_V1 / M3 | PASS WITH CONCERNS | VALID | WEAK | Out-of-design timeframe; stronger cost drag |
| ER_RS_CONTINUATION_V1 / M1 | PASS WITH CONCERNS | VALID (after fixes) | WEAK | Out-of-design; 87.6% risk-rejected; worst R |
| ER_RS_CONTINUATION_V1 / M15–H1 | PASS WITH CONCERNS | VALID | WEAK / INCONCLUSIVE | Out-of-design; tiny samples (2–41 trades) |
| ER_RS_CONTINUATION_V1 / D1 | N/A | VALID | NOT EVALUATED | No intraday trigger; 0 trades |
| Sector-alignment analysis | N/A | NOT VALID | NOT EVALUATED | No sector benchmark data |

### Final comparison

> **Does the implemented algorithm behave as the design documents say it should?**
> Yes, once the two blocking defects are fixed: the lifecycle, gates, session rules, long/short
> symmetry, risk authority and intraday flatten all match DD-02/DD-03. Before the fixes it did **not**
> (the `NONE` trap produced zero trades over five years).

> **After costs, risk, regime and out-of-sample, is there evidence of an exploitable edge for SBIN?**
> **No, not in this pass.** Every in-scope configuration is negative under both zero and realistic
> costs, and the dominant loss driver is a structural stop/entry interaction (EMA8 stop inside M5
> noise + next-bar-open fills), not a code defect. This is a **research hypothesis failure for this
> ticker/period**, not evidence that the software is broken.

### Recommended next actions (in order)

1. **FIX IMPLEMENTATION first**: the M1 state-machine trap and M2 reader truncation were blocking; both
   are fixed. Re-run broader validation before any performance claim.
2. **RESEARCH STRATEGY FURTHER**: the 3/8 stop/entry design produces 2–4R stop-outs. Test a
   minimum stop distance (ATR-based floor), stop/limit trigger entries, and lower-timeframe ambiguity
   resolution before judging edge.
3. **EXPOSE PARAMETER PERMUTATIONS** (API/catalog) to test persistence, RVOL and extension thresholds
   under train/validation/holdout.
4. **INGEST SECTOR BENCHMARKS** to exercise the documented MARKET→SECTOR→STOCK hierarchy.
5. Do **not** promote any configuration to broader multi-symbol testing until (2)–(3) are resolved.

---

## Addendum (Phase A) — Execution/exit sensitivity (M5+)

Opt-in execution features added (defaults unchanged): `targetMethod=R_MULTIPLE` with `targetR`,
`entryMethod=TRIGGER_LIMIT`, ambiguity policies `STOP_FIRST_CONSERVATIVE` / `TARGET_FIRST_OPTIMISTIC` /
`SKIP_AMBIGUOUS`, and per-trade MFE/MAE (max favourable/adverse excursion in R). M1 excluded as a
subject timeframe.

SBIN M5, zero-cost, permissive, 2023-10-01→2026-09-18:

| Variant | trades | exits | wins | avg R | avg MFE | avg MAE |
|---|---:|---|---:|---:|---:|---:|
| baseline (no target) | 121 | 106 STOP / 15 FLATTEN | 13 | −2.38 | 1.85 | 2.70 |
| target 1.5R | 123 | 77 STOP / 46 TARGET | 11 | −2.28 | 0.79 | 2.37 |
| target 2R | 123 | 83 STOP / 40 TARGET | 19 | −2.25 | 0.86 | 2.43 |
| target 3R | 121 | 86 STOP / 35 TARGET | 24 | −2.08 | 1.13 | 2.47 |
| target 2R + trigger entry | 123 | 83 STOP / 40 TARGET | 19 | −2.28 | 0.85 | 2.45 |

Cross-checks: HDFCBANK M5 2R → 128 trades, avg R −2.81, MFE 0.57 / MAE 2.89; SBIN M15 2R → 41,
avg R −1.73; SBIN M30 2R → 11, avg R −1.62; SBIN M5 2R realistic cost → 123, avg R −3.92.

### Interpretation

- Adding a target (previously absent entirely) converts roughly a third of trades to TARGET exits and
  lifts the number of winners (13 → 19–24), but **expectancy stays ≈ −2R**.
- The decisive statistic is **MAE ≫ MFE at entry**: the average trade runs ~0.6–1.8R in favour but
  ~2.4–2.9R against. Because the documented 3/8 invalidation (EMA3 recross of EMA8) places the
  protective stop only ~0.05–0.12% from entry, ordinary M5 noise and next-bar-open fills produce
  stop-outs of 2–4× planned risk.
- Conclusion: the negative result is **not** primarily the missing target. It is (a) a stop that is
  far too tight relative to intraday noise and (b) entry timing that sees adverse excursion before
  favourable. Trigger entries changed little (the trigger is already crossed at signal time).

### Next (Phase A follow-ups)

1. **ATR stop floor** (`minStopAtr`, applied in the risk/plan path with quantity re-sizing) so the
   planned risk distance is meaningful relative to M5 ATR.
2. Entry-timing study: why does adverse excursion precede favourable (entry-bar/gap behaviour,
   pullback vs breakout), and whether a confirmation/limit entry improves MFE−MAE asymmetry.
3. Only after those, decide whether the signal itself carries edge.

### Phase A follow-up — ATR minimum-stop floor

The EMA8 invalidation places the stop ~0.1% from entry, far inside M5 noise. A minimum protective-stop
floor (`minStopAtr`, applied in the risk path so quantity re-sizes; structural invalidation is never
moved) was added and tested (SBIN M5, 2R target, permissive, zero cost unless stated):

| Variant | trades | wins | avg R | avg MAE | net | net % |
|---|---:|---:|---:|---:|---:|---:|
| no floor | 123 | 19 | −2.25 | 2.43 | — | — |
| floor 0.5 ATR | 123 | 44 | −1.08 | 1.64 | — | — |
| floor 1.0 ATR | 116 | 40 | −0.51 | 1.23 | — | — |
| floor 1.5 ATR | 114 | 42 | −0.31 | 1.03 | — | — |
| floor 2.0 ATR | 112 | 46 | −0.17 | — | −15,951 | −1.60 |
| floor 1.5 ATR + realistic cost | 114 | 36 | −0.65 | — | −53,438 | −5.34 |
| floor 2.0 ATR + realistic cost | 112 | 38 | −0.43 | — | −44,911 | −4.49 |

This is the decisive result of Phase A: **the negative P&L was dominated by an unworkably tight stop**.
Widening the protective stop to 1.5–2.0 ATR moves expectancy from ≈ −2R to ≈ −0.2R (zero cost) and
lifts the win count roughly 2×. Realistic M5 costs remain a first-order drag, so the configuration is
still not profitable — but the strategy is no longer being destroyed by its stop.

Remaining levers before judging edge: cost drag (prefer M15+ where costs are smaller per unit of
move), entry timing (MFE−MAE asymmetry), and per-stock parameter optimisation (the research-layer
program).

---

## Phase B — Sweepable backtests

The backtest request now accepts inline, validated `strategyParameters` (precedence over
preset/version) and the full parameter set is folded into the run manifest so every configuration is
a distinct, reproducible run. A bounded `POST /api/v1/backtests/sweep` runs a batch of configurations
sharing one base request, serialized by the single-worker executor.

Initial SBIN M15 sweep (target 2R, 1.5 ATR floor, permissive, real data):

| Config | trades | win % | net |
|---|---:|---:|---:|
| baseline params | 38 | 36.8 | −4,170 |
| persistence threshold 0.40 | 28 | 35.7 | −4,996 |
| RVOL interval minimum 1.5 | 30 | 43.3 | **+3,164** |

The third configuration is positive — the first sign-flip observed — which is exactly the kind of
per-stock parameter interaction the research layer is meant to search systematically (walk-forward,
not a single-window optimiser).

---

## Phase C — Research-layer search orchestration

Added a standard-library-only research package (`research/src/edge_relative_research/backtest/`):
an HTTP client for the Java API, chronological train/validation/OOS and walk-forward splitters, a
parameter-grid generator, a sweep runner, and a one-shot CLI
(`python -m edge_relative_research.backtest.cli`). It reads/executes backtests over HTTP and writes
nothing — Python never owns production state.

Sample run (SBIN M15, 2024, target 2R, 1.5-ATR floor), 8-config grid of RRS persistence and RVOL
interval: all eight configurations finished and ranked positive (+₹3.5k…+₹5.1k, ~50% win, 6–10
trades each). The samples are small and this is a single in-sample window; the point is that the
per-stock search loop is now automated, reproducible and ready for walk-forward evaluation.
