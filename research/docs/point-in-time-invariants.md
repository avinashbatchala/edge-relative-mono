# Point-in-Time Invariants and Leakage Guards

- **Status:** implemented (Prompt 05). Dataset construction and analysis are not built yet.
- **Package:** `research/src/edge_relative_research/pit/`
- **Companions:** `research-data-contracts.md`, `research-data-access.md`,
  `dataset-reproducibility.md`.

The exit criterion is that the test suite proves representative future-leakage cases are
detected automatically. Every guard fails closed and loudly: when a known-time cannot be
established, the guard refuses rather than assuming the data is safe.

## 1. Core invariants

1. **A temporal load requires an `as_of_timestamp`.** Missing is an error
   (`MissingAsOfError`), never a silent default to now.
2. **No source timestamp may be later than its anchor.** Selection and alignment assert
   `source_timestamp <= anchor_timestamp`.
3. **The anchor may not exceed the join ceiling.** A base row later than the joiner's
   `as_of_timestamp` is future data.
4. **Missing is not zero; incomplete is not closed.** A bar/feature must be closed and
   complete at the anchor.
5. **Labels live outside feature inputs.** Outcome/label columns are rejected in feature
   contexts.
6. **Normalization is fit on the past only**, inside the training window, before its
   first use.
7. **Known-time, not event-time, governs corporate actions.** A factor is usable only if
   its known-time (`available_at`) or effective date is at or before the anchor.

## 2. Supported point-in-time alignment

`PointInTimeJoiner` (half-open `[valid_from, valid_to)`) and `PointInTimeLoader` resolve:

| Domain | Mechanism |
|---|---|
| Instrument identity | `reference.instrument_identifier` as-of session date |
| Sector mapping | `reference.instrument_sector_history` as-of |
| Benchmark membership | `reference.benchmark_constituent_history` as-of |
| Universe membership | `reference.universe_membership_history` as-of |
| Trading session | `reference.trading_session` for the as-of session date |
| Market / sector / stock features | generic as-of join on `(entity, anchor_timestamp)` |
| Feature normalization baselines | `NormalizationBaseline` fit-window guards |
| Setup state | `operational.setup_observation` bounded by `observed_at < as_of` |

The loader converts the required `as_of_timestamp` to the exchange (`Asia/Kolkata`)
session date for effective-dated reference reads, and re-checks every returned row with the
guards so a future mapping can never be returned even if a query regressed
(`tests/test_point_in_time_loaders.py`).

## 3. Prohibited leakage and detection

| Prohibited | Guard | Scanner code |
|---|---|---|
| Future candles / rows | `assert_not_future`, `assert_rows_not_future` | `FUTURE_ROW`, `FUTURE_TIMESTAMP` |
| Full-day volume in an intraday row | `assert_volume_scope`, `assert_no_full_day_volume` | `FULL_DAY_VOLUME` |
| Future-confirmed pivots | `assert_pivot_confirmed_by` | `FUTURE_PIVOT` |
| Future constituents | `assert_constituent_as_of`, `assert_is_active_member` | n/a (join returns `None`) |
| Future sector classifications | `assert_is_active_member` (via loader) | n/a |
| Future corporate-action knowledge | `assert_corporate_action_known` | n/a |
| Future normalization samples | `NormalizationBaseline.assert_fit_not_after` | `NORMALIZATION_FIT` |
| Outcome columns in features | `assert_no_outcome_columns` | `OUTCOME_COLUMN` |
| Incomplete higher-timeframe bar | `assert_closed_bar` | `INCOMPLETE_BAR` |

## 4. Higher-timeframe closed-bar semantics

`assert_closed_bar(bar, anchor)` rejects a bar when:

- `close_time > anchor` (the bar is not yet closed at the anchor);
- `complete`/`is_complete` is `False`;
- `quality`/`quality_state` is `INCOMPLETE`.

This is the rule behind "a higher timeframe is only usable once its bar has confirmed".

## 5. Normalization and train/test boundaries

`NormalizationBaseline(name, fit_start, fit_end, statistics)` plus:

- `assert_fit_not_after(anchor)` / `assert_fit_before_use` — fit end must not be after use;
- `assert_fit_within_train(baseline, train_start, train_end)` — the whole fit window is
  inside training;
- `assert_fit_before_validation(baseline, validation_start)` and
  `assert_baseline_not_fit_on_test(baseline, test_start, test_end)` — no overlap with
  validation or test.

Fitting on the full sample is a `NormalizationLeakError`.

## 6. Leakage scanner

`LeakageScanner(cutoff=...)` inspects a row set (schema + timestamps) and returns
`LeakageFinding`s; `assert_clean` raises `LeakageDetectedError` with all findings. It
checks for outcome columns, rows/ timestamps after the cutoff, missing anchors,
incomplete bars, full-day volume intraday, future pivots and normalization fit ends after
the anchor. It is the dataset-level backstop for the `as_of`-based loaders.

## 7. Adversarial tests (exit criterion)

`tests/test_leakage_adversarial.py` intentionally injects each case and requires a loud
failure:

| Injected case | Expected |
|---|---|
| Future row | `FutureDataError` / `LeakageDetectedError` (`FUTURE_ROW`) |
| Future sector mapping | `FutureConstituentError`; joiner returns `None` |
| Future full-day volume | `FullDayVolumeError` / `FULL_DAY_VOLUME` |
| Future pivot confirmation | `FuturePivotError` / `FUTURE_PIVOT` |
| Outcome column in features | `OutcomeInFeatureError` / `OUTCOME_COLUMN` |
| Future normalization statistic | `NormalizationLeakError` / `NORMALIZATION_FIT` |

A clean control row passes. Additional suites cover every guard
(`test_point_in_time_guards.py`), the as-of join (`test_point_in_time_join.py`) and the
repository-backed loaders (`test_point_in_time_loaders.py`).

## 8. Limitations and next steps

- The scanner inspects rows/schemas; a Polars-DataFrame convenience wrapper and dataset
  integration belong to the dataset-building stage that will call it.
- The join framework is generic over mappings/objects; canonical contract objects can be
  passed directly.
- `as_of` correctness is only as good as the source tables' validity windows; the
  reference schema enforces non-overlap, and the loader re-asserts on read.
- Feature snapshots are not yet read from PostgreSQL directly; when a feature read adapter
  exists it must call the same guards.
