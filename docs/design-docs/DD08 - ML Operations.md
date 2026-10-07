# DD08 - ML Operations

Status: implemented (first slice). Companion to DD02 §10/§143–144 (ML boundary) and DD03 (risk).

## 1. Scope and authority boundary

ML is **advisory**. It may rank or reject opportunities that Strategy has already qualified as VALID,
and it may inform later sizing research. It can never:

- turn an INVALID setup into a trade;
- override risk;
- size a live position (initial ML risk-sizing authority remains disabled).

The first slice delivers a **learning-to-rank model** over VALID setups. It is trained in the Python
research layer and served in-process by the Java backend through a dependency-free frozen artifact.

## 2. Locked decisions

| Concern | Decision |
|---|---|
| Objective | Rank already-VALID setups (learning-to-rank / score) |
| Features | Canonical engine features + cheap derived context (no new producers) |
| Label | Graded realized R of the simulated managed trade |
| Model | Gradient-boosted trees (LightGBM), lazily imported in research |
| Split | Python trains; Java stores the registry and serves inference |
| Training data | Per-anchor feature vector + outcome exported from the backtest engine |
| Java artifact | Pure-Java evaluator over a frozen `er-gbm-v1` JSON tree dump |
| Survivor gate | OOS rank quality threshold **and** non-degrading verification backtest, then manual promote |
| Binding | Append-only, effective-dated per-instrument model binding |
| Ranking group | Global model, groups by session, bound/evaluated per symbol |
| Timeframes | M5/M15/M30 setup + D1 context, default M5 |
| Artifact storage | Metadata in Postgres; JSON artifact on disk referenced by URI + SHA-256 |
| Ranking effect | Score orders valid setups under a configurable concurrent-position cap |
| Trigger | DB-backed queue polled by a Python research runner |

## 3. Feature vector

Built at the VALID anchor from the exact production `FeatureSnapshot` plus derived context. All
categorical inputs are one-hot encoded so every model split is numeric; a missing feature is omitted
and follows the model's stored default direction — never coerced to zero.

- Stock/RS: `ATR`, `DERIVED_ATR_PERCENT`, `RRS_RAW/FAST/SLOW/PERSISTENCE/SLOPE/ACCELERATION/PERCENTILE`,
  `RRS_VS_SECTOR_RAW`, `RVOL_D1/INTERVAL/CUMULATIVE`, `RVE`, `DIRECTIONAL_VOLUME_LONG/SHORT`.
- Context: `MARKET_ATR`, `MARKET_DIRECTIONAL_EFFICIENCY`, `SECTOR_RRS_RAW`,
  `SECTOR_DIRECTIONAL_EFFICIENCY`.
- Derived: `DERIVED_distanceEntryToStopAtr`, `DERIVED_distanceEntryToTargetAtr`,
  `DERIVED_plannedRewardRisk`, `DERIVED_entryExtensionAtr`, `DERIVED_priceVsSessionHighPct`,
  `DERIVED_priceVsSessionLowPct`, `DERIVED_rangePositionInSession`, `DERIVED_minutesSinceOpen`,
  `DERIVED_minutesToClose`, `DERIVED_dayOfWeek`, `DERIVED_alignmentCount`.
- Categorical (one-hot): `direction`, `setupFamily`, `rrsTrendState`, `marketStructure`,
  `sectorStructure`, `sectorCode`.

Session extremes and time-of-day are computed strictly from data at or before the anchor.

## 4. Artifact format (`er-gbm-v1`)

A small, dependency-free JSON document evaluated identically by Python and Java:

```json
{
  "format": "er-gbm-v1",
  "objective": "regression",
  "baseScore": 0.0,
  "features": ["RRS_RAW", "..."],
  "trees": [
    {"nodes": [
      {"feature": "RRS_RAW", "threshold": 0.5, "left": 1, "right": 2, "missing": 2},
      {"leaf": -0.12},
      {"leaf": 0.34}
    ]}
  ]
}
```

`baseScore` plus the sum of reached leaf values is the raw score. A missing feature follows the node's
`missing` child. The Python exporter converts a LightGBM booster and **verifies** the artifact
reproduces `booster.predict` before registration; a shared fixture
(`contracts/fixtures/ml/gbm_parity.json`) pins Java/Python evaluator parity.

## 5. Storage

- `control.ml_analysis_run` — queue: status, config, progress, metrics, registered `model_version_id`.
- `control.ml_model_binding` — append-only, effective-dated, non-overlapping per instrument; references
  `control.model_version`.
- `control.model` / `control.model_version` — registry (reused): algorithm, `artifact_uri`,
  `artifact_checksum`, periods, `metrics` JSONB, `lifecycle_state`.

## 6. API

- `POST /api/v1/ml/analysis-runs`, `GET /api/v1/ml/analysis-runs[/{key}]`, `POST …/{key}/cancel`
- Runner: `POST …/claim`, `…/{key}/progress`, `…/{key}/complete`, `…/{key}/fail`
- `POST /api/v1/ml/models`, `GET /api/v1/ml/models[/{id}]`, `POST …/{id}/lifecycle`
- `POST /api/v1/ml/bindings`, `GET /api/v1/ml/bindings/{instrumentId}[/effective]`
- `POST /api/v1/ml/export/anchors` — per-anchor training export
- `POST /api/v1/ml/verify` — baseline vs ML-ranked verification backtest

## 7. Research layer

`edge_relative_research.ml`: `artifact` (format + evaluator + LightGBM export with self-check),
`evaluate` (rank-IC, NDCG@k, grading), `dataset` (matrix assembly), `train` (grouped walk-forward),
`client`, `worker` (queue runner), `cli`. LightGBM/numpy are lazy imports so the pure-Python pieces
remain testable; `pyproject` is otherwise unchanged.

## 8. Screens (RESEARCH → ML Lab)

- **ML Lab** — configure universe, M5/M15/M30 setup timeframe + presets, window, model/thresholds, launch.
- **Analysis detail** — out-of-sample rank quality, gate verdict, promote-to-symbol, verification backtest.
- **Models & bindings** — registry with lifecycle and effective-dated per-instrument bindings.

## 9. Verification

- Backend: `GbmModelEvaluator`, `MlAnalysisRunService`, `MlArtifactParityTest` (shared fixture), full
  `mvnw verify`.
- Research: artifact round-trip, evaluator parity fixture, ranking metrics, dataset assembly (pytest).
- Frontend: view tests + lint/typecheck/build.

## 10. Open / next

- Wire an optional `mlModelVersionId` through the standard backtest API so a promoted binding drives
  live backtests directly (today verification is explicit via `/ml/verify`).
- Expand the training set to VALID anchors that were not taken (needs a forward-outcome label).
- Conditional promotion thresholds per strategy/symbol and automatic gate surfacing in the UI.
- LightGBM runtime dependencies for the research runner (`uv add lightgbm scikit-learn`) are required
  for training; the pure-Python artifact path is dependency-free.
