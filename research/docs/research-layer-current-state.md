# Research Layer — Current State (Reconnaissance)

- **Scope:** Prompt 00 reconnaissance only. No research engine implemented.
- **Method:** Read all design docs; inspected source, migrations, tests and CI; ran the
  Python test suite; ran backend schema/feature/setup integration tests against
  Testcontainers PostgreSQL 18.4; inspected the *running* local PostgreSQL schema and
  row counts directly. Files were verified by path/line wherever practical.
- **Exit criteria:** PASS — there is a concrete, evidence-based map of how a
  point-in-time research dataset can be assembled from the existing system, together
  with the exact gaps that block it.

Evidence sources are cited inline as `path:line`. All row counts were read from the
running container `edge-relative-mono-postgres-1` (database `edge_relative`) unless a
test is named.

---

## 1. Executive summary

The backend is substantially further along than `AGENTS.md` suggests. A deterministic
feature engine, a deterministic setup/strategy engine, a risk engine, a trade-plan
lifecycle, a risk/corporate-action adjusted history read path, and a Java backtest
engine all exist and are covered by tests. The canonical M1 candle store is populated.

The Python research layer, by contrast, is a **bare placeholder**: one module with a
docstring and one import smoke test. Everything in `docs/edge-relative-python-research-layer-agent-prompts.md`
(Prompt 01 onward) is unstarted.

For research purposes the practical consequence is:

- **Candles are reconstructable and authoritative in PostgreSQL** (M1 only; higher
  timeframes derived on read).
- **Point-in-time feature vectors are reconstructable** either by re-running the
  deterministic Java engine or by reading persisted `market.feature_snapshot` rows.
- **Setup state snapshots are persisted**, but the live and backtest paths always
  evaluate from `PriorSetup.none()`, so true lifecycle transitions
  (`WATCH→FORMING→NEAR_TRIGGER→VALID`, `INVALIDATED`, `EXPIRED`) are **not** currently
  produced. Non-executed states exist only as independent snapshots, and in the
  backtest only as aggregated `stageCounts` in a metrics JSON blob.
- **There are no executed trades, orders, fills, or realized P&L anywhere.** The
  operational execution tables are empty, and the backtest writes only to
  `research.*` simulated ledger tables. There is no live/paper execution and no broker
  mutation (mutations fail closed with `BROKER_OPERATION_NOT_ENABLED`).
- **There are no outcome labels, pattern windows, similarity indexes, or training
  runs.** Their catalog tables exist but are empty and have no producer.
- **There is no Parquet/object storage in code**; it is documented-only (ADR-001,
  ADR-002, DD-05 §76, DD04B §16).

A point-in-time research row can therefore be assembled **now** for
feature + market/sector + setup-state inputs, but **not** for executed-trade or
future-outcome columns. That is the central finding of this reconnaissance.

---

## 2. Architecture diagram

```text
                              NSE / Groww (read-only)
                                      |
                                      v
                       +--------------------------------+
                       |  Groww adapter (broker-groww)  |   mutations refused:
                       |  historical + instrument + LTP |   BROKER_OPERATION_NOT_ENABLED
                       +--------------------------------+
                                      |  HistoricalCandle (M1)
                                      v
        +----------------------------------------------------------------+
        | Backend application (Spring Boot + jOOQ + Flyway)               |
        |                                                                |
        |  history/  ingestion -> market.candle (M1 canonical, rev)      |
        |            query     -> derived M3..D1/W1 on read              |
        |            corporateaction -> adjusted series (as-of factors)  |
        |                                                                |
        |  feature/  FeatureEngine -> market.feature_snapshot(_value)    |
        |            /api/v1/features + /ws/features                    |
        |                                                                |
        |  strategy/ StrategyEngine -> operational.setup_observation     |
        |            /api/v1/setups/{id}/evaluate | /api/v1/setups/{id}  |
        |                                                                |
        |  risk/     RiskEvaluator -> risk_context_snapshot,             |
        |            risk_decision(_reason), risk_reservation            |
        |                                                                |
        |  tradeplan/ TradePlanFactory -> trade_plan(_event)             |
        |                                                                |
        |  backtest/ reuses Feature+Strategy+Risk+TradePlan engines      |
        |            -> research.{experiment,run,backtest_*,ledger}      |
        +----------------------------------------------------------------+
                 |                      |                      |
                 v                      v                      v
        operational.trade/order/     research.dataset(_version)   audit.audit_event
        order_event/fill/position    research.* (all empty)       (append-only)
        (ALL EMPTY in live DB)
                 |
                 v
        (no execution runtime wired; no broker writes)

  Python research/  -- currently a stub; intended to READ production and WRITE research
        |
        +-- (intended) HTTP /api/v1/*  and/or read-only SQL on reference/control/
        |   market/operational observations
        +-- (intended) write research.* datasets/manifests/experiments
        +-- (eventual) Parquet/object storage for wide matrices (documented, not built)
```

Authority boundary (from `AGENTS.md` and the research prompt guide): Python may
discover/validate hypotheses, but must never place orders, change risk decisions,
write fills, mutate positions/trade plans, or auto-promote rules into Java.

---

## 3. Repository and module map

| Area | Path | State |
|---|---|---|
| Backend reactor | `backend/pom.xml` | 4 Maven modules; enforces JDK `[25,26)` (`backend/pom.xml:42-45`) |
| — domain | `backend/domain` | Framework-free; effectively empty (`package-info.java` only) |
| — broker-api | `backend/broker-api` | Broker-neutral ports/models |
| — broker-groww | `backend/broker-groww` | Groww read-only adapter + disabled mutation adapter |
| — application | `backend/application` | Spring Boot, jOOQ, Flyway, all trading logic |
| Frontend | `frontend/` | Vue 3 workstation placeholder |
| Research (Python) | `research/` | src-layout package; **stub** (see §4) |
| Contracts | `contracts/fixtures/features/` | Frozen cross-language feature fixtures (Java consumer only) |
| Migrations | `backend/application/src/main/resources/db/migration/` | V001–V021 + `R__grants_template.sql` |
| Scripts | `scripts/*.sh` | curl-based audit scripts (not a library) |
| Design docs | `docs/design-docs/` + `docs/design-docs/dev/` | DD01–DD05, ADR-001/002/003, per-area audits |

Backend application packages of interest:

`history/`, `feature/`, `strategy/`, `risk/`, `tradeplan/`, `backtest/`,
`corporateaction/`, `reference/`, `catalog/`, `watchlist/`, `opportunity/`, `broker/`.

Migrations: `V001` schemas, `V002` reference, `V003` control, `V004` market catalog,
`V005` operational, `V006` research, `V007` audit/immutability, `V008`–`V012` candles,
`V013` feature engine, `V014` setup engine, `V015` setup instance, `V016` risk engine,
`V017` trade-plan lifecycle, `V018` backtest engine, `V019` strategy/risk status,
`V020` session purge, `V021` corporate-action factors.

---

## 4. Python research project (current state)

| Item | Evidence |
|---|---|
| Package | `research/src/edge_relative_research/__init__.py` = one docstring line |
| Test | `research/tests/test_import.py` = one smoke test |
| `pyproject.toml` | name `edge-relative-research` 0.1.0; `requires-python >=3.13`; **runtime `dependencies = []`**; dev deps `pytest==9.0.2`, `ruff==0.15.6`; build `uv_build==0.12.10` (`research/pyproject.toml:1-16`) |
| `uv.lock` | 8 packages total (pytest + ruff + transitive); no numpy/polars/pandas/scipy/statsmodels/sklearn/pyarrow/DB driver (`research/uv.lock`) |
| Missing structure | No `config/`, `db/`, `datasets/`, `features/`, `labels/`, `patterns/`, `similarity/`, `experiments/`, `validation/`, `cli/`, `notebooks/`, `docs/` |
| CI | `research/` job runs `uv sync --locked`, `uv run pytest`, `uv run ruff check .`, `uv run ruff format --check .` (`.github/workflows/ci.yml:49-65`) |

There is **no Python DB access, no HTTP client, and no fixture consumer**. The intended
structure is only described in `docs/edge-relative-python-research-layer-agent-prompts.md`.

> **Update (Prompt 02):** the contracts stage added `pydantic==2.13.5` and the
> `edge_relative_research.contracts` package plus `tests/`. The table above remains the
> Prompt 00 snapshot; see `research/docs/research-data-contracts.md` for the contracts.
>
> **Update (Prompt 03):** the data-access stage added `psycopg==3.3.6` (runtime),
> `testcontainers==4.15.0` (dev), the `db/` and `repositories/` packages, and integration
> tests against a migrated/ seeded PostgreSQL container; see
> `research/docs/research-data-access.md`. Python still has no write path to operational
> state.
>
> **Update (Prompt 04):** dataset versioning added `polars==1.44.2` (Parquet), the
> `datasets/` package (manifest, checksums, local Parquet storage, catalog, builder) and
> migration `V022__dataset_version_reproducibility.sql`; see
> `research/docs/dataset-reproducibility.md`. Python writes only the `research` schema.
>
> **Update (Prompt 05):** the point-in-time stage added the `pit/` package (as-of join,
> guards, normalization-fit boundaries, repository-backed loaders, leakage scanner) with
> adversarial tests; see `research/docs/point-in-time-invariants.md`.

---

## 5. Source-of-truth matrix

| Data | Authoritative source | Written by | Python access (intended) |
|---|---|---|---|
| Canonical M1 candles | `market.candle` (PostgreSQL, scoped exception ADR-001) | `HistoryRepository` backfill only (`HistoryRepository.java:149`) | Read (API and/or SQL) |
| Higher timeframes | Derived on read by `CandleAggregator` | nobody persists them | Read (API) |
| Candle coverage/runs | `market.candle_coverage`, `market.ingestion_run` | backfill | Read |
| Corporate-action factors | `reference.corporate_action(_factor)` | test writers only; empty in live DB | Read |
| Adjusted candle series | Derived on read | `CorporateActionAdjustmentService` | Read |
| Feature definitions | `control.feature_definition` (lazy) + Java registry | `JdbcFeatureSnapshotRepository.ensureDefinition` | Read |
| Feature versions/schemas | Java constants; `market.feature_snapshot` free-text columns | feature engine | Read |
| Feature snapshots | `market.feature_snapshot(_value)` (derived, append-only) | `JdbcFeatureSnapshotRepository` | Read |
| Market/sector context | Feature snapshot header + `reference.*_history` | feature engine / `SectorReferenceInitializer` | Read |
| Setup observations | `operational.setup_observation` (append-only) | `SetupObservationRepository` | Read |
| Model predictions | `operational.model_prediction` (immutable) | **no producer** | Read |
| Risk decisions | `operational.risk_decision(_reason)` (immutable) | `RiskDecisionRepository` | Read |
| Trade plans | `operational.trade_plan(_event)` (immutable + events) | `TradePlanRepository` | Read |
| Trades | `operational.trade` (mutable projection) | **no producer / no runtime** | Read |
| Orders/events | `operational.order_record`, `order_event` | **no producer / no runtime** | Read |
| Fills | `operational.fill` (append-only) | **no producer / no runtime** | Read |
| Executed P&L/outcome | `operational.trade_outcome` | **no producer** | Read |
| Simulated trades/P&L | `research.backtest_trade`, `backtest_equity_point`, `backtest_rejection` | backtest engine | Read (research) |
| Dataset manifests | `research.dataset(_version/_input)` | backtest `ensureDataset`; ingestion does **not** use it | Read/Write (research) |
| Experiments/backtest runs | `research.experiment(_run)`, `research.backtest_run(_spec)` | backtest engine | Read/Write (research) |
| Pattern/outcome/label catalogs | `research.pattern_*`, `research.outcome_*` | **no producer** | Read/Write (research) |
| Training/model candidates | `research.training_run`, `research.model_candidate` | **no producer** | Read/Write (research) |
| Audit | `audit.audit_event` (append-only) | **no producer** | Read |
| Parquet/object storage | none | none | none |

---

## 6. Existing-data matrix (live PostgreSQL, 2026-09-22)

Verified directly against `edge-relative-mono-postgres-1`:

| Table | Rows | Notes |
|---|---:|---|
| `market.candle` | 1,465,284 | all `M1`, all `is_current`; 2021-09-21 → 2026-09-18 |
| — SBIN / NIFTY / RELIANCE / TCS / HDFCBANK / ICICIBANK / BHARTIARTL / ADANIENT | 460,538 / 275,291 / 275,010 / 90,893 / 90,892 / 90,891 / 90,891 / 90,878 | 8 instruments |
| `market.candle_coverage` | 279 | all `COMPLETED` |
| `market.ingestion_run` | 39 | 21 COMPLETED, 15 PARTIAL, 3 QUEUED |
| `market.market_data_incident` | 0 | |
| `market.feature_snapshot` | 8 | anchor `2026-09-18T10:00Z`; M5 (7) + D1 (1); **`snapshot_quality = INCOMPLETE`**, availability `VALID`/`WARMING_UP`; 15 values each; `er-feature-schema-v1` / `er-feature-calc-v1` |
| `reference.instrument` | 21 | runtime-seeded (watchlist import) |
| `reference.sector` / `benchmark` / `sector_benchmark_history` | 11 / 11 / 11 | `SectorReferenceInitializer` |
| `reference.instrument_sector_history` | 4 | SBIN, ICICIBANK, HDFCBANK, TCS |
| `reference.corporate_action(_factor)` | 0 / 0 | only test writers |
| `reference.universe(_membership_history)` | 0 / 0 | no producer |
| `reference.trading_calendar_day` / `trading_session` | 0 / 0 | calendar is config-driven, not DB |
| `control.feature_definition` | 15 | lazily seeded by feature writer |
| `control.feature_version` / `feature_schema` / `feature_schema_version` / `feature_schema_member` | 0 / 0 / 0 / 0 | **never seeded** |
| `control.strategy` / `strategy_version` | 1 / 1 | `ER_RS_CONTINUATION_V1` v1, `RESEARCH` |
| `operational.setup_observation` | **0** | engine reachable via HTTP only; strategy disabled by config |
| `operational.trade` / `trade_plan` / `order_record` / `order_event` / `fill` / `trade_outcome` | **0** | no execution runtime |
| `operational.risk_decision` / `model_prediction` / `recommendation` | **0** | no runtime |
| `audit.audit_event` | 0 | |
| `research.dataset` / `dataset_version` | 1 / 1 | `CANONICAL_M5` v1 `COMMITTED`, `storage_uri=postgres://canonical`, checksum `3c89aca9-…`, `point_in_time_cutoff` NULL, `row_count` NULL |
| `research.experiment` / `experiment_run` | 1 / 14 | from backtest runs |
| `research.backtest_run` / `backtest_run_spec` | 14 / 14 | |
| `research.backtest_trade` | 83 | simulated; `realized_r` populated; `plan_key`/`decision_key` are run-local UUIDs |
| `research.backtest_equity_point` | 35,427 | |
| `research.backtest_rejection` | 0 | table exists; no producer wired to persist per-run rejections here |
| `research.pattern_*` / `outcome_*` / `training_run` / `model_candidate` | 0 | catalogs empty |

Caveat: the populated `market.candle` state is the persisted Docker volume after
operator-driven Groww backfills. A fresh database built from migrations alone contains
**zero** candles; V009/V012/V020 are purges that ran *before* the current re-ingestion.

---

## 7. Missing-data matrix

| Needed for the research pipeline | Exists? | Where it would live | Blocking gap |
|---|---|---|---|
| Point-in-time feature matrix | Partial | `market.feature_snapshot(_value)`; else recompute | Only 8 live rows; no read API/repo; no `as_of` read semantics |
| Features beyond 2026-09-18 | No | same | No scheduled producer; snapshots written only on evaluation |
| Setup-state observations for history | No | `operational.setup_observation` | Live table empty; engine config-disabled; bulk/history evaluation absent |
| Full lifecycle transitions | No | setup_observation sequence | Both callers hard-code `PriorSetup.none()` (`SetupEvaluationService.java:188`, `BacktestEngine.java:333`) |
| Valid-but-skipped / low-rank / NO_TRADE records | No | `operational.recommendation` | Table has vocabulary `NO_SETUP`/`RISK_REJECTED`/`LOW_RANK`… (`V005:778`) but **no Java writer/reader** |
| Risk-rejected candidates (production) | Schema only | `operational.risk_decision(_reason)` | Empty; no runtime |
| Simulated risk rejections | Schema only | `research.backtest_rejection` | Table empty in live DB; backtest only aggregates into `metrics.stageCounts` |
| Executed trades / fills / realized P&L | No | `operational.trade/order/fill/trade_outcome` | Empty; no execution runtime; broker mutations disabled |
| Future-outcome labels (MFE/MAE/target-before-stop/horizons) | No | `research.outcome_*` (catalog) + dataset | No producer, no schema rows, no definitions |
| Pattern windows / similarity indexes | No | `research.pattern_*` | No producer; wide tensors assigned to Parquet (not built) |
| Training runs / model candidates | No | `research.training_run`, `model_candidate` | No producer |
| Dataset manifest/checksums for candles | Partial | `research.dataset_version` | Ingestion does not register a dataset (audit HI-16); backtest pins `version=1` |
| Parquet/object storage | No | ADR-001/002/DD05 §76 | Documented, deferred behind triggers |
| Universe membership history | No | `reference.universe_membership_history` | No producer; watchlist is operational |
| Trading calendar in DB | No | `reference.trading_calendar_day/session` | No producer; calendar is config constants |
| Python package structure, DB/HTTP access, fixtures consumer | No | `research/src/...` | Not started (Prompt 01) |

---

## 8. Answers to the 16 reconnaissance questions

### Q1. What already exists?

Deterministic feature engine (22 feature codes), setup/strategy engine, risk evaluator,
trade-plan lifecycle, canonical M1 history with corporate-action adjustment, full
PostgreSQL schema for operational/research/audit, Java backtest engine reusing the
production engines, frozen feature fixtures, and populated canonical candles. Details in
§5–§6.

### Q2. What is incomplete?

- Live **execution** is entirely absent; `operational.trade/order/fill` have no producer.
- Setup observations are **not lifecycle chains**: every evaluation cold-starts
  (`PriorSetup.none()`), so `INVALIDATED`/`EXPIRED` are unreachable in live/backtest and
  `setup_instance_id` does not carry forward.
- Feature **schema/version registry** tables are unseeded; only free-text version strings
  are persisted.
- The `opportunity`/`recommendation` board has no producer.
- `research.backtest_rejection` is not populated by the engine.
- No Parquet implementation despite the architecture describing it.

### Q3. What is missing?

The entire Python research layer (Prompt 01+): typed contracts, DB access layer, dataset
manifest/reproducibility layer, point-in-time join/leakage guards, dataset builders,
outcome label engine, parity harness, experiment registry, similarity/pattern pipelines,
and quality/profiling/reporting. See §7.

### Q4. Where does authoritative historical data currently live?

In **PostgreSQL**, table `market.candle` (M1 only), per ADR-001. Higher timeframes are
derived on read. There is no Parquet store. The live DB holds ~1.465M M1 rows across 8
instruments. Read access is via `HistoryRepository`/`HistoricalDataReader` and HTTP
`/api/v1/history/*`.

### Q5. Exact tables/files by datum

| Datum | Table / file |
|---|---|
| Setup observations | `operational.setup_observation` (append-only; `V005:203-247`, `V015`) |
| Executed trades | `operational.trade` (`V005:491-530`) — **empty** |
| Fills | `operational.fill` (`V005:611-649`) — **empty** |
| P&L (executed) | `operational.trade.realized_pnl`; `operational.trade_outcome` (`V005:721-753`) — **empty** |
| P&L (simulated) | `research.backtest_trade.gross_pnl/net_pnl/realized_r` (`V018:44-74`) |
| Historical candles | `market.candle` (`V008`, `V010`); coverage `market.candle_coverage`; runs `market.ingestion_run` |
| Feature snapshots | `market.feature_snapshot` + `market.feature_snapshot_value` (`V013`) |
| Strategy versions | `control.strategy` + `control.strategy_version` (`V003:78-110`); seeded `ER_RS_CONTINUATION_V1` v1 (`V014`) |
| Market/sector context | Snapshot header `market_instrument_id`/`sector_id`/`sector_instrument_id` (`V013:20-22`); `reference.instrument_sector_history`, `sector_benchmark_history`, `benchmark_constituent_history` |

### Q6. Can a point-in-time research row be reconstructed now?

**Partially — inputs yes, outcomes no.** A row can be assembled for:
identity/instrument, M5 anchor/close time, feature values (recompute deterministically
from `market.candle`, or read `market.feature_snapshot` scoped to
`(instrument, timeframe, anchor, feature_schema_version, calculation_version)`), market
and sector context (snapshot header or as-of `reference.*_history`), setup status and
gate/quality detail (from `setup_observation.explanation`), and data-quality flags.

It **cannot** currently include executed entry/exit/fills/realized R, future outcome
labels (MFE/MAE/target-before-stop/horizons), or true setup-state transitions. The Java
engine already enforces point-in-time anchoring (`FeatureLeakageTest`,
`FeatureReplayParityTest`); Python has no such guard yet.

### Q7. Are feature versions preserved?

**Partially.** Each `market.feature_snapshot_value` stores `feature_code`,
`feature_version` (`<SEMANTIC>@<parameter_hash>`), and `parameter_hash`; the header
stores `feature_schema_version` (`er-feature-schema-v1`) and `calculation_version`
(`er-feature-calc-v1`). Java semantic versions live in `FeatureVersions.java`. However the
`control.feature_schema*`/`feature_version` registry is **never seeded**, so there is no
relational version catalog and the strategy's `feature_schema_version_id` is NULL.

### Q8. Are labels separated from features?

Structurally **yes**: features live in `market.feature_snapshot*` and labels are intended
for `research.outcome_*`; the feature schema contract marks features immutable and the
design separates feature and outcome datasets (DD04B §11.3). Operationally **no labels
exist yet**, so separation is a contract, not a proven pipeline. `operational.trade_outcome`
is a separate post-trade table but is empty.

### Q9. Are near-setups/rejected/skipped setups persisted?

- **Near-setups:** yes in principle — `setup_status` can be `WATCH`, `FORMING`,
  `NEAR_TRIGGER` (`StrategyEngine.java:424-444`; DB CHECK `V014:12-14`).
- **Rejected:** `REJECTED` is in the DB CHECK but is **not** a `SetupState` and is never
  emitted by the engine. Risk rejection is separate (`risk_decision.decision='REJECT'`).
  `BLOCKED` exists as an engine state and is DB-allowed.
- **Skipped:** no such concept exists anywhere in the codebase.
- **Caveat:** the live `setup_observation` table is currently empty and the engine is
  config-disabled, so none of these are actually persisted in the local DB.

### Q10. Can we distinguish valid-executed / valid-skipped / risk-rejected / invalid / near-trigger / missed?

| Category | Distinguishable? | Evidence |
|---|---|---|
| Valid executed | Schema yes, data no | `setup_observation(VALID)` → `risk_decision(APPROVE)` → `trade_plan` → `trade/fill`; all links exist (`V005:348,413`; `V007:148-162`), but downstream tables are empty |
| Valid skipped | Weak | No `skipped` field/status. `recommendation.non_trade_reason` vocabulary exists but has no producer; backtest only counts `stageCounts`. `trade_plan_event` can record `EXPIRED`/`INVALIDATED` after approval |
| Risk rejected | Yes (schema) | `risk_decision.decision='REJECT'` + `risk_decision_reason`; backtest equivalent `backtest_rejection` (unpopulated) |
| Invalid | Indirect | No `INVALID` status; gate failures are inside `setup_observation.explanation`; status may be `NONE`/`WATCH`/`BLOCKED` |
| Near-trigger | Yes | `setup_observation.setup_status='NEAR_TRIGGER'` |
| Missed | Yes | `setup_observation.setup_status='MISSED'` (entry-extension case) |

### Q11. Java feature logic needing Python parity fixtures

Required: ATR, RRS raw/fast/slow/persistence/slope/acceleration/percentile/trend-state,
RRS_vs_sector and sector RRS, RVOL daily/interval/cumulative, RVE, directional volume
long/short, market ATR/directional-efficiency/price-structure, sector
directional-efficiency/price-structure (`FeatureKeys.java`). Existing fixtures cover only
ATR_V1, RRS_V1, RVOL_V1, RVE_V1 (`contracts/fixtures/features/`). Missing fixtures:
directional volume, market/sector context, RRS-vs-sector/sector-RRS, and RRS trend state.
VWAP distance is documented (DD-02/DD-05) but **not implemented** in Java
(`FeatureDashboardService.java:361-363`).

### Q12. What APIs or DB queries should Python use?

- HTTP (preferred by ADR-002): `/api/v1/history/candles` (`from`,`to`,`adjustment`,`asOf`),
  `/api/v1/history/coverage`, `/api/v1/features/{snapshot,series,dashboard,watchlist}`,
  `/api/v1/setups/{instrumentId}`, `/api/v1/backtests*`, `/api/v1/strategies`,
  `/api/v1/risk-policies`.
- SQL read-only (per `R__grants_template.sql` and DD04B §18): `reference`, `control`,
  `market`, `operational` observation tables.
- SQL read/write: `research` schema.
- Concrete point-in-time join key: `setup_observation` → `market_observation_id`
  (`market.market_observation` has instrument/timeframe/bar close) → matching
  `market.feature_snapshot` by `(instrument_id, timeframe_id, anchor_timestamp)`.

### Q13. Which schemas are read-only for Python?

`reference`, `control`, `market`, and `operational` (including observations and the
append-only evidence tables). This matches DD04B §18 and the inert grants template.

### Q14. Which research tables may Python write?

The whole `research` schema: `dataset`, `dataset_version`, `dataset_version_input`,
`pattern_*`, `similarity_index_metadata`, `outcome_*`, `experiment`, `experiment_run`,
`backtest_run`, `training_run`, `model_candidate` (DD04B §18; `R__grants_template.sql:43-51`).
Note `dataset_version` is content-immutable once `COMMITTED` (`V007:326-360,427-441`).

### Q15. Current schema/code mismatches

1. **`operational.trade_plan` has no `setup_instance_id` column**, but
   `TradePlanRepository.SELECT_COLUMNS` selects `tp.setup_instance_id`
   (`TradePlanRepository.java:29`) and `map()` reads it (line 162). Proven live:
   `ERROR: column tp.setup_instance_id does not exist`. This breaks plan lookup by
   decision/setup/instrument (`findByDecision`, `findBySetup`, `findLatestForInstrument`).
2. **Unseeded feature schema registry.** `V014` selects `control.feature_schema.code =
   'ER_FEATURE_SET'` (`V014:34-38`), but no such row is ever inserted, so
   `strategy_version.feature_schema_version_id` is NULL and
   `StrategyCatalogRepository.latestFeatureSchemaVersionId()` returns NULL. Feature
   versions are therefore preserved only as snapshot free-text, not as catalog lineage.
3. **`research.backtest_rejection` has no uniqueness constraint** (unlike trades/equity)
   and `insertRejections` is a plain append (`BacktestRepository.java:195-203`); a retried
   run could duplicate rejections.
4. **Backtest `dataset_version` pins `version=1` with `ON CONFLICT DO NOTHING`**
   (`BacktestRepository.java:44-50`), so the stored checksum is first-writer and later
   distinct runs do not create new dataset versions.
5. **API-only vs direct-SQL tension:** ADR-002 says "no direct PostgreSQL access from
   Python" (`ADR-002:37-38`), while the grants template and DD04B §18 grant Python SELECT
   on `reference/control/market/operational`. The intended access mode must be resolved.
6. **Grants template is inert:** no `edge_java`/`edge_python`/`edge_migrations` roles exist
   in the local DB (verified `pg_roles`); `R__grants_template.sql` is a guarded `DO $$`
   block that no-ops when roles are absent.
7. Minor: `SetupObservationRepository` falls back to `direction="NONE"` for a null
   direction (`SetupObservationRepository.java:81`) which would violate the
   `direction IN ('LONG','SHORT')` CHECK — currently unreachable because the service
   always supplies LONG/SHORT.

### Q16. Prerequisite changes before research implementation

See §10. In short: resolve Python access mode and provision roles; seed/formalise the
feature schema registry (and fix the `ER_FEATURE_SET` reference); fix the
`trade_plan.setup_instance_id` query; populate/backfill setup observations and decide
whether to implement real lifecycle state; define dataset-manifest conventions (including
the Parquet-vs-Postgres decision); add read models for feature snapshots and setups; and
seed outcome definitions.

---

## 9. Python read/write permission assumptions

Based on `DD04B §18` (Query/Write Ownership) and `R__grants_template.sql`:

```text
edge_python
  READ   : reference, control, market, operational (observations)
  READ/WRITE: research (all tables + sequences)
  NEVER  : insert/update/delete on operational execution/risk/plan tables,
           market candles/feature snapshots, audit, or any deployment authority
```

Intended but **not yet true**:
- the role does not exist locally, so permissions are unenforced/untested;
- ADR-002 prefers API-only feature access over direct SQL, which conflicts with the
  direct-SELECT grant. Decision needed before implementing the data-access layer;
- production access should use a dedicated read-only connection, parameterized queries,
  deterministic ordering, explicit `as_of` boundaries, and a write connection scoped to
  `research` only (Prompt 01/03 requirements).

---

## 10. Prerequisite work

Ordered by dependency. Each is a candidate small task; none is Prompt 01 work.

1. **Resolve Python DB access policy** (API-only vs read-only SQL vs both) and record it;
   provision `edge_python` (and `edge_java`) roles in the local/compose setup so grants
   actually apply, or explicitly test against them.
2. **Fix the `trade_plan.setup_instance_id` read mismatch** (`TradePlanRepository.java:29,162`):
   either add the column via migration and populate it, or drop it from the query/map.
3. **Seed the feature schema/version registry** (`control.feature_schema`,
   `feature_schema_version`, `feature_schema_member`, `feature_version`) — either via an
   initializer that mirrors `FeatureDefinitionRegistry`/`FeatureVersions`, or by removing
   the `ER_FEATURE_SET` dependency in `V014` and documenting free-text versioning. Without
   this, dataset builds cannot select features by schema version reliably.
4. **Decide setup lifecycle semantics.** Either implement a persisted state machine that
   feeds `PriorSetup` from the previous observation (making `INVALIDATED`/`EXPIRED` and
   instance continuity real), or explicitly document that observations are independent
   cold-start snapshots and build research accordingly.
5. **Establish a read path for research inputs.** Add/confirm read models (or HTTP
   endpoints) for `market.feature_snapshot(_value)` and `operational.setup_observation`
   with `as_of`/range semantics and stable ordering.
6. **Define the dataset manifest/checksum convention** for research inputs and outputs,
   including whether canonical candles get a registered `research.dataset_version`
   (ingestion currently does not; audit HI-16) and how feature/outcome datasets record
   `point_in_time_cutoff`, parents, and `row_count`.
7. **Seed outcome schema/definitions** (`research.outcome_schema(_version)`,
   `outcome_definition`) and decide the intrabar ambiguity policy (target-vs-stop) before
   any label engine is built.
8. **Decide the Parquet/object-storage plan.** Per ADR-001/002 the deferral is acceptable
   at current scale; if wide pattern/feature matrices are needed, implement the local/test
   storage abstraction (Prompt 04) rather than expanding PostgreSQL silently.
9. **Data-quality guardrails.** The only live feature snapshots are
   `snapshot_quality=INCOMPLETE` (seed missing M1 minutes on 2026-09-18 per the calendar
   audit). Research must treat quality flags as first-class and fail closed.
10. **Cross-language fixture harness.** Extend `contracts/fixtures/features` to the missing
    features (Q11) and add the Python consumer test so parity is enforced before research.

---

## 11. Proposed incremental implementation sequence

The guide's Prompt 01–30 sequence is sound; the following adds the dependency gates
discovered above.

```text
GATE A (prereqs)   §10 items 1–3, 9          access policy, roles, schema registry, quality
GATE B             Prompt 01  research package foundation (config, db read/write split,
                              time/seed utils, ruff/pytest/hypothesis, CLI stub)
GATE C             Prompt 02  canonical research contracts (ResearchAnchor, FeatureContext,
                              ExecutionContext, OutcomeRecord, DatasetIdentity, Pattern*)
GATE D             Prompt 03  data-access layer over existing schema/API
                              (reference/control/market/operational read, research write)
GATE E             Prompt 04  dataset manifest + reproducibility (research.dataset_version)
GATE F             Prompt 05  point-in-time join + leakage guard
GATE G             Prompt 06  setup-observation dataset  <-- first real dataset
                              (requires lifecourse decision §10.4)
GATE H             Prompt 07  outcome label engine (separate dataset)
GATE I             Prompt 08  Java/Python feature parity harness (needs §10.10 fixtures)
GATE J             Prompt 09+ quality, experiments, analysis, patterns, similarity, ML
```

Practical first deliverables after this reconnaissance:

1. Prereq fixes (§10.2, §10.3) plus the access-policy record.
2. Prompt 01 package skeleton with read-only/write connection split.
3. Prompt 02 contracts + Prompt 04 manifest so no analysis touches an unversioned folder.
4. Prompt 06/07 datasets only once the setup-lifecycle and label-definition decisions exist.

---

## 12. Risks

| Risk | Severity | Notes / mitigation |
|---|---|---|
| No executed trades or fills exist | High | Research on realized outcomes is impossible until execution/paper trading exists. Use theoretical setup outcomes (Prompt 07) and backtest ledger; label them clearly as simulated |
| Cold-start setup evaluations | High | No true lifecycle/time-in-state data. Either implement state chaining or restrict research to anchor-level snapshots and document it |
| Feature schema registry unseeded | High | Dataset lineage/version selection is unreliable; `ER_FEATURE_SET` reference is dead. Fix before dataset builds |
| `trade_plan` query mismatch | Medium | Latent runtime failure in plan reads; fix before relying on trade-plan lineage |
| Python DB role absent / grants inert | High | Permission model untested; risk of accidentally using a superuser connection. Provision roles and test read-only enforcement |
| API-only (ADR-002) vs direct SQL conflict | Medium | Ambiguity can lead to inconsistent access layers and leakage controls. Decide explicitly |
| Point-in-time leakage in Python | High | No join/leakage framework yet. The Java engine's protection does not transfer to Python. Implement Prompt 05 before any dataset |
| Corporate-action adjustment disabled/empty | Medium | `feature.corporate-actions.adjusted-inputs=false` and factor table empty, so historical features are unadjusted across splits. Define research adjustment policy |
| Data-quality gaps | Medium | Live snapshots are `INCOMPLETE`; missing minutes. Must not silently treat missing as zero (AGENTS invariant) |
| Backtest dataset lineage collision | Medium | `dataset_version` pinned to v1 makes checksums first-writer; earlier lineage may be misleading |
| Large-sample bias from backtest-only data | High | 14 runs / 83 simulated trades on ~8 instruments is not a research base for edge claims without out-of-sample/walk-forward discipline (Prompts 21/22) |
| Parquet not implemented | Low–Medium | Acceptable under ADR-001 triggers; risk is silently expanding PostgreSQL beyond its scoped role. Keep the deferral explicit and benchmarked |
| Feature gaps (VWAP, etc.) | Low | Documented but unimplemented features must be marked Python-only experimental and excluded from canonical conclusions |
| Environment/pinning drift | Low | `uv 0.12.17` and JDK 26 were used locally vs pinned `uv 0.12.10`/JDK 25; keep CI as the authority |

---

## 13. Validation performed

| Check | Command | Result |
|---|---|---|
| Python sync | `uv sync --locked` (uv 0.12.17 provisioned CPython 3.13.15) | PASS |
| Python tests | `uv run pytest -q` | **1 passed** |
| Python lint | `uv run ruff check .` | PASS |
| Python format | `uv run ruff format --check .` | PASS (2 files) |
| Backend migrations | Flyway inside Testcontainers `postgres:18.4-bookworm` via `SchemaInvariantTest` | **22 migrations applied** (V001–V021 + R) |
| Schema invariants | `SchemaInvariantTest` | **12 passed** |
| Feature fixtures | `FeatureFixtureTest` | **4 passed** |
| Feature versioning | `FeatureVersioningTest` | **3 passed** |
| Setup persistence | `SetupObservationIntegrationTest` | **1 passed** |
| Live schema inspection | `docker exec … psql` against `edge-relative-mono-postgres-1` | Confirmed counts and the `tp.setup_instance_id` error |

Environment caveats (not caused by this task):

- `uv` was not installed; it was installed to the pre-approved temp dir (`0.12.17`).
  The pinned `0.12.10` was not available. `uv sync --locked` recreated `research/.venv`
  (git-ignored) against CPython 3.13.15.
- **JDK 25 is not installed** (only 26 and 21). The supported `./mvnw verify` is blocked
  by the enforcer (`backend/pom.xml:42-45`), so targeted backend tests were run with
  JDK 26 and `-Denforcer.skip=true`. The **full `./mvnw verify` suite was not run**; only
  the four tests above were executed. The Spring Boot app and Flyway work under JDK 26.
- Frontend checks were not in scope and were not run.

Not verified / out of scope: full backend test suite, frontend lint/typecheck/test/build,
and live Groww ingestion (network credentials not used).

---

## 14. Evidence-based point-in-time reconstruction recipe (current capability)

For any M5 anchor on a trading day, a research row can be assembled today as:

1. **Identity/time:** `reference.instrument` (`instrument_id`, stable `instrument_key`),
   `reference.timeframe.code='M5'`, session date via `NseTradingCalendar` (Asia/Kolkata,
   `nse-session-v1`), minutes-since-open from the 09:15 IST session open.
2. **Candles:** HTTP `GET /api/v1/history/candles?instrumentId&timeframe=M1|M5&from&to`
   (optionally `adjustment=SPLIT_BONUS&asOf=…`); confirmed bars carry `complete`
   (`HistoricalDataQueryService.markInProgressIncomplete`).
3. **Features (point-in-time):** either re-run the deterministic `FeatureEngine` on those
   candles, or read `market.feature_snapshot` scoped by
   `(instrument_id, timeframe_id, anchor_timestamp, feature_schema_version, calculation_version)`
   and `feature_snapshot_value` (`feature_code`, `feature_version`, `parameter_hash`,
   `value`, `quality`, `availability`).
4. **Market/sector context:** snapshot header (`market_instrument_id`, `sector_id`,
   `sector_instrument_id`) or as-of `reference.instrument_sector_history` /
   `sector_benchmark_history` evaluated at the session date.
5. **Setup state:** `operational.setup_observation` joined by
   `(strategy_version_id, instrument_id, observed_at)`; gate/quality detail parsed from
   `explanation` JSON.
6. **Execution/outcome:** **not available** (no trades/fills/labels). Theoretical outcomes
   must be computed later by the Python label engine from future candles, kept in a
   separate dataset (Prompt 07).

Point-in-time guarantees that already exist for steps 1–5 come from the Java engine's
tests (`FeatureLeakageTest`, `FeatureReplayParityTest`, `FeatureVersioningTest`) and the
as-of reference/corporate-action resolvers. Python must implement its own equivalent
guards (Prompt 05) rather than assume them.

---

## 15. Known limitations of this report

- Row counts reflect the local developer database on 2026-09-22; they are not a
  production baseline.
- The full backend test suite was not executed (JDK 25 unavailable); conclusions about
  non-targeted code paths are based on source inspection.
- DD-06/DD-09 are referenced by DD-05 but do not exist; no requirements were invented
  for them, per `AGENTS.md`.
