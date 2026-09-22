# Research Data Contracts

- **Status:** implemented (Prompt 02). Analysis, datasets and experiments are not built yet.
- **Package:** `research/src/edge_relative_research/contracts/`
- **Companions:** `research/docs/research-layer-current-state.md` (reconnaissance),
  `docs/edge-relative-python-research-layer-agent-prompts.md` (staged plan).

These contracts are the only typed boundary between the authoritative Java/PostgreSQL
system and the Python research layer. They are intentionally explicit about identity,
versions, missing values, data quality, and the feature/outcome split so later stages
cannot accidentally leak future information into feature inputs.

## 1. Why Pydantic

The contracts are frozen [Pydantic v2](https://docs.pydantic.dev/) models
(`pydantic==2.13.5`). Pydantic was chosen because the requirements are validation and
serialization requirements:

- `extra="forbid"` makes every contract a **closed world**: an unknown key — including a
  mis-applied future-label column — is a hard error, not a silent extra.
- `frozen=True` makes contracts immutable after construction.
- Native JSON round-tripping and machine-readable JSON Schema make serialization and
  schema tests possible.
- Enums and `Decimal` are first-class.

Pydantic is a data-validation library, not an ML or trading-calculation dependency; no
calculation is performed in these contracts. Python still owns no authoritative trading
state.

## 2. Design rules (from the prompt, made enforceable)

| Rule | How it is enforced |
|---|---|
| Use stable IDs, not symbols | `InstrumentRef.instrument_id` is required; `symbol` is optional display metadata. |
| No future outcomes inside feature contracts | Module split; `value_kind == LABELED` is rejected on feature/execution/pattern models; tests assert no outcome field names appear in input models. |
| Represent missing values explicitly | `FeatureAvailability` on every value; `value`/`label` are `None` when not `VALID`; `VALID` requires a value and non-`VALID` forbids one. `None` never becomes `0`. |
| Represent data-quality states explicitly | `FeatureQuality` and `FeatureAvailability` on `FeatureValue`, `ContextBlock` and `FeatureContext`; `worst_quality` severity helper. |
| Distinguish observed / derived / labeled | `ValueKind` on values and records; `Fill` must be `OBSERVED`, features/patterns/execution are `DERIVED`, `OutcomeRecord` is `LABELED`. |
| Do not hide version metadata | Every contract that depends on a version carries it: feature schema/calculation, feature stamps, strategy version, outcome schema/definition, pattern schema, normalization and method versions, dataset and code versions. |

## 3. Package layout

```text
research/src/edge_relative_research/
├── __init__.py            package version
├── version.py             code_version() for dataset/experiment lineage
├── timeutil.py            UTC + Asia/Kolkata session helpers
└── contracts/
    ├── base.py            FrozenModel, UtcInstant
    ├── enums.py           controlled vocabularies mirrored from Java/DB
    ├── common.py          InstrumentRef, FeatureVersionStamp, compute_parameter_hash
    ├── anchor.py          ResearchAnchor
    ├── features.py        FeatureValue, ContextBlock, TimeOfDayContext, FeatureContext
    ├── execution.py       Fill, CostBreakdown, ExecutionContext
    ├── outcomes.py        HorizonReturn, FuturePathMetadata, OutcomeRecord   <-- labels only
    ├── dataset.py         DatasetIdentity
    └── patterns.py        PatternWindow, PatternMatch
```

The import direction is one-way: `features`/`anchor`/`execution`/`patterns` never import
`outcomes`. Labels are reachable only by stable reference strings.

## 4. Vocabulary alignment

All enum values are byte-for-byte identical to the Java/DB spellings, so contracts can be
built from persisted rows without translation.

| Contract enum | Source |
|---|---|
| `Direction` | `strategy/domain/Direction.java` |
| `SetupState` | `strategy/domain/SetupState.java` + `V014__setup_engine.sql` (adds `REJECTED`) |
| `SetupInitialization` | `strategy/domain/SetupInitialization.java` |
| `Timeframe` | `reference.timeframe` (`V002`, seeded `V011`) |
| `FeatureAvailability` | `feature/domain/FeatureAvailability.java` |
| `FeatureQuality` | `feature/domain/FeatureQuality.java` (ordered by severity) |
| `Side`, `ExitReason` | `operational.order_record` / `operational.trade` CHECK constraints (`V005`) |
| `DatasetType`, `DatasetStatus` | `research.dataset` / `research.dataset_version` (`V006`) |
| `LabelState`, `AmbiguityPolicy` | research-only (new label vocabulary; ambiguity is explicit) |
| `Cohort` | staged similarity design (Prompt 17) |

`compute_parameter_hash` reproduces Java `FeatureVersion.hash` (SHA-256 over sorted
`key=value;`, first six bytes hex) so a persisted `feature_snapshot_value.parameter_hash`
can be matched exactly.

## 5. Contract reference

### 5.1 `ResearchAnchor`

*What the system knew at one point in time for one instrument, timeframe and strategy
version.* The unit of analysis and the link key for outcomes.

| Field | Type | Notes / source |
|---|---|---|
| `instrument` | `InstrumentRef` | required; `instrument_id` identity |
| `exchange_timestamp` | `UtcInstant` | tz-aware UTC; `setup_observation.observed_at` |
| `timeframe` | `Timeframe` | e.g. `M5` |
| `strategy_version` | `str` | `control.strategy_version` code/version, e.g. `ER_RS_CONTINUATION_V1/v1` |
| `strategy_version_id` | `int?` | `strategy_version_id` |
| `setup_state` | `SetupState` | `setup_observation.setup_status` |
| `direction` | `Direction` | `LONG`/`SHORT` |
| `observation_key` / `observation_id` | `UUID?` / `int?` | market observation |
| `market_observation_key` / `_id` | `UUID?` / `int?` | `setup_observation.market_observation_id` |
| `setup_observation_key` / `_id` | `UUID?` / `int?` | `setup_observation` |
| `setup_instance_id` | `UUID?` | `setup_observation.setup_instance_id` |
| `setup_initialization` | `SetupInitialization?` | `initialization_reason` |
| `session_date`, `minutes_since_open` | `date?`, `int?` | derived in `Asia/Kolkata` |

Invariants: at least one observation/setup identity must be present; `setup_instance_id`
must be absent while `setup_state` is `NONE`. `identity` returns a stable link string.

### 5.2 `FeatureContext`

*Point-in-time feature state for one anchor.* Measurement only — no strategy decision,
position or label.

| Field | Type | Notes |
|---|---|---|
| `feature_schema_version` | `str` | `er-feature-schema-v1` |
| `calculation_version` | `str` | `er-feature-calc-v1` |
| `stock` | `ContextBlock` | subject stock features (`ATR`, `RRS_*`, `RVOL_*`, `RVE`, …) |
| `market` | `ContextBlock?` | `MARKET_ATR`, `MARKET_DIRECTIONAL_EFFICIENCY`, `MARKET_PRICE_STRUCTURE` |
| `sector` | `ContextBlock?` | `SECTOR_RRS_RAW`, `SECTOR_DIRECTIONAL_EFFICIENCY`, `SECTOR_PRICE_STRUCTURE` |
| `time_of_day` | `TimeOfDayContext?` | derived session context |
| `snapshot_quality` / `snapshot_availability` | enums | `market.feature_snapshot` header state |
| `source_data_revision` | `str?` | e.g. `canonical-m1-v1` |
| `feature_versions` (property) | `dict[str, str]` | key → `RRS_V1@<hash>` |

`FeatureValue` carries `feature_key`, a `FeatureVersionStamp`, `anchor_timestamp`,
`timeframe`, `availability`, `quality`, optional numeric `value` or categorical `label`,
`lineage`, and `value_kind` (always non-`LABELED`). `ContextBlock` rejects a feature
anchored after its block, and `FeatureContext` requires market/sector anchors to equal the
stock anchor.

### 5.3 `ExecutionContext`

*Actual or simulated trade execution.* Observed facts and derived arithmetic are kept
separate; money is `Decimal`, quantities are `int`.

| Field group | Fields |
|---|---|
| identity | `trade_key` (UUID), `instrument`, `direction` |
| entry reference | `planned_entry_low/high`, `entry_reference_price`, `structural_invalidation`, `protective_stop`, `target_reference`, `planned_slippage` |
| quantities | `planned_quantity`, `filled_entry_quantity`, `filled_exit_quantity` |
| fills | `entry_fills: tuple[Fill,...]`, `exit_fills: tuple[Fill,...]` |
| actuals | `average_entry_price`, `average_exit_price`, `actual_slippage` |
| costs & P&L | `realized_gross_pnl`, `explicit_costs: CostBreakdown`, `net_pnl`, `realized_r`, `holding_seconds`, `exit_reason` |

Invariants: entry fills are `BUY`, exit fills are `SELL`; fill quantities reconcile with
`filled_*_quantity`; `Fill.value_kind` must be `OBSERVED`; the record may not be
`LABELED`. `CostBreakdown.total` and `entry_timestamp`/`exit_timestamp` are properties.

### 5.4 `OutcomeRecord` (labels only)

*Versioned future outcomes for one anchor.* Links by `anchor_reference` string only.

Fields: `horizon_returns` (per-horizon forward returns), `maximum_favourable_excursion`
/ `maximum_adverse_excursion`, `mfe_r` / `mae_r`, `target_before_stop`, `target_hit`,
`stop_hit`, `time_to_target_seconds` / `time_to_stop_seconds` / `time_to_mfe_seconds` /
`time_to_mae_seconds`, `ambiguity_policy`, `ambiguous`, `future_path`
(`FuturePathMetadata`), plus `outcome_schema_version`, `outcome_definition_version`,
`as_of`. `value_kind` must be `LABELED`.

Intrabar ambiguity is explicit: `LabelState.UNKNOWN` is used when target/stop ordering is
unknowable from bar data; it is never guessed. `FuturePathMetadata` records coverage
(bar count, first/last bar, missing bars, session-close truncation).

### 5.5 `DatasetIdentity`

*Reproducibility metadata.* Mirrors `research.dataset` / `research.dataset_version`.

Fields: `dataset_code`, `dataset_type`, `version`, `status`, `code_version`,
`dataset_key`, `dataset_version_key`, `feature_schema_version`, `outcome_schema_version`,
`point_in_time_cutoff`, `parent_dataset_versions`, `storage_uri`,
`partition_manifest_uri`, `row_count`, `checksum`, `committed_at`, `retired_at`.

Invariants mirror the DB checks: `COMMITTED` requires a checksum and `committed_at`;
`RETIRED` requires `retired_at`; `FAILED` cannot have `committed_at`. Committed versions
are treated as immutable (`is_immutable`).

### 5.6 `PatternWindow` and `PatternMatch`

`PatternWindow` records deterministic lineage for a sequence around an anchor:
`pattern_key`, `anchor`, `instrument`, `timeframe`, `feature_schema_version`,
`pattern_schema_version`, `normalization_version`, `source_observation_start/end`,
`sequence_length`, `market_regime`, `sector_regime`, `sector_id`, `minutes_since_open`,
`storage_uri`, `storage_row_reference`, `dataset_version_reference`. It may not be
`LABELED`, and its source range may not extend past the anchor.

`PatternMatch` links a query pattern to a historical pattern:
`query_pattern_key`, `matched_pattern_key`, `matched_anchor`, `matched_instrument`,
`matched_anchor_timestamp`, `cohort`, `method_version`, `score`, `rank`, `as_of_limit`,
and `outcome_reference` (a stable key, never outcome values). The matched anchor must
predate `as_of_limit`.

## 6. Feature/outcome separation guarantee

The exit criterion — *future stages cannot accidentally mix future labels with feature
inputs* — is enforced at four levels:

1. **Module boundary.** `outcomes.py` is the only module with label fields; input
   modules do not import it (`test_input_modules_never_import_outcomes`).
2. **Closed-world models.** `extra="forbid"` rejects an unknown key such as `mfe_r` on a
   `FeatureContext` (`test_extra_fields_are_forbidden_everywhere`).
3. **Value-kind guard.** `LABELED` is rejected on `FeatureValue`, `ContextBlock`,
   `FeatureContext`, `ExecutionContext`, `PatternWindow`, `PatternMatch`.
4. **Name-disjointness tests.** `test_label_field_names_do_not_appear_in_feature_contracts`
   and `test_no_input_model_references_outcome_record` fail if a label vocabulary or the
   `OutcomeRecord` type leaks into an input model.

## 7. Examples from fixtures

Static examples live in `research/tests/fixtures/` and are validated and round-tripped by
the test suite. Cross-language examples are built from the shared frozen fixtures in
`contracts/fixtures/features/`.

### 7.1 Anchor

```json
{
  "instrument": { "instrument_id": 1, "instrument_key": "1111…", "symbol": "RELIANCE" },
  "exchange_timestamp": "2026-09-01T04:05:00Z",
  "timeframe": "M5",
  "strategy_version": "ER_RS_CONTINUATION_V1/v1",
  "setup_state": "VALID",
  "direction": "LONG",
  "setup_observation_key": "44444444-4444-4444-4444-444444444444",
  "setup_instance_id": "55555555-5555-5555-5555-555555555555",
  "setup_initialization": "LIFECYCLE_START",
  "minutes_since_open": 20
}
```

### 7.2 Feature context built from the shared ATR fixture

The Java fixture `contracts/fixtures/features/atr-v1.json` ends at
`2026-09-01T04:05:00Z` with an expected ATR of `2.0`. The Python contract reproduces it
without reinterpreting the formula:

```python
version = FeatureVersionStamp.of(
    "ATR", "ATR_V1", "er-feature-calc-v1", {"length": 3, "smoothing": "WILDER"}
)
value = FeatureValue(
    feature_key="ATR",
    version=version,
    anchor_timestamp="2026-09-01T04:05:00Z",
    timeframe=Timeframe.M5,
    availability=FeatureAvailability.VALID,
    quality=FeatureQuality.GOOD,
    value=2.0,
)
```

`test_rrs_fixture_carries_numeric_and_categorical_values` additionally proves that
`RRS_RAW` maps to a numeric value and `RRS_TREND_STATE` maps to the categorical label
`NEUTRAL` from the same fixture.

### 7.3 Execution (closed long trade)

```json
{
  "trade_key": "66666666-6666-6666-6666-666666666666",
  "direction": "LONG",
  "entry_fills": [
    { "side": "BUY", "quantity": 60, "price": "100.70", "exchange_timestamp": "2026-09-01T04:06:00Z" },
    { "side": "BUY", "quantity": 40, "price": "100.825", "exchange_timestamp": "2026-09-01T04:06:30Z" }
  ],
  "exit_fills": [
    { "side": "SELL", "quantity": 100, "price": "110.20", "exchange_timestamp": "2026-09-01T05:30:00Z" }
  ],
  "average_entry_price": "100.75",
  "average_exit_price": "110.20",
  "explicit_costs": { "brokerage": "20.00", "stt": "15.00" },
  "net_pnl": "903.90",
  "realized_r": "3.2869",
  "exit_reason": "TARGET"
}
```

### 7.4 Outcome

```json
{
  "anchor_reference": "setup-observation:44444444-4444-4444-4444-444444444444",
  "outcome_schema_version": "er-outcome-schema-v1",
  "outcome_definition_version": "ER_TPB_V1",
  "target_before_stop": "HIT",
  "stop_hit": "NOT_HIT",
  "maximum_favourable_excursion": 9.45,
  "maximum_adverse_excursion": 0.8,
  "horizon_returns": [ { "horizon_seconds": 3600, "return_fraction": 0.094, "available": true } ]
}
```

## 8. Versioning and lineage

- Feature identity is `FeatureVersionStamp`: `semantic_version` (`RRS_V1`),
  `calculation_version` (`er-feature-calc-v1`), `parameters`, and `parameter_hash`.
- Feature schema/calculation versions are carried on `FeatureContext`.
- Strategy lineage is on `ResearchAnchor`; outcome definition versions are on
  `OutcomeRecord`; pattern and normalization versions are on `PatternWindow`.
- Dataset identity records `code_version` (use `version.code_version()`), parent dataset
  versions, cutoff, checksum and manifest URI.
- `UtcInstant` rejects naive datetimes so a cutoff can never silently shift.

## 9. Extending the contracts

1. Prefer adding a field to the correct side of the split; never move a label into an
   input model.
2. Use a controlled enum from `contracts/enums.py` when the value is persisted by Java
   or the database; add the value to the mirror table in section 4.
3. Default new numeric/statistical fields to `None` with an availability state rather
   than zero.
4. Add a fixture under `research/tests/fixtures/` and include the model in the round-trip
   parametrization.
5. Keep the wire format closed: do not serialize derived helpers.

## 10. Known limitations

- The contracts are defined; no producer populates them yet. The data-access layer is
  Prompt 03.
- `SetupState.REJECTED` is representable but is not emitted by the Java engine; treat it
  as a persisted-only value until the setup lifecycle is completed (see the
  reconnaissance prerequisite list).
- `TimeOfDayContext` models only the fixed `[09:15, 15:30)` NSE session; holidays and
  special sessions are not modelled yet and must not be invented.
- `Cohort` and the pattern contracts anticipate Prompt 16–20; no pattern storage exists.
- Pydantic is pinned to `==2.13.5`; `uv.lock` is authoritative.
