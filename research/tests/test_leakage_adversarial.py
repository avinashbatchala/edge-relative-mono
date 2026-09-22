"""Adversarial leakage tests: each injected future-information case must fail loudly."""

from __future__ import annotations

from datetime import date, datetime, timezone

import pytest

from edge_relative_research.pit import (
    FullDayVolumeError,
    FutureConstituentError,
    FutureDataError,
    FuturePivotError,
    LeakageDetectedError,
    LeakageScanner,
    NormalizationBaseline,
    NormalizationLeakError,
    OutcomeInFeatureError,
    PointInTimeJoiner,
    VolumeScope,
    assert_constituent_as_of,
    assert_fit_before_validation,
    assert_fit_within_train,
    assert_no_full_day_volume,
    assert_no_outcome_columns,
    assert_not_future,
    assert_pivot_confirmed_by,
    assert_volume_scope,
)

UTC = timezone.utc
CUTOFF = datetime(2026, 9, 1, 4, 10, tzinfo=UTC)
FUTURE = datetime(2026, 9, 1, 11, 0, tzinfo=UTC)
SESSION_CLOSE = datetime(2026, 9, 1, 10, 0, tzinfo=UTC)


def test_injected_future_row_fails_loudly() -> None:
    rows = [{"instrument_id": 1, "anchor_timestamp": FUTURE, "atr": 1.0, "complete": True}]
    scanner = LeakageScanner(cutoff=CUTOFF)
    result = scanner.scan(rows)
    assert "FUTURE_ROW" in result.codes
    with pytest.raises(LeakageDetectedError):
        scanner.assert_clean(rows)
    with pytest.raises(FutureDataError):
        assert_not_future(FUTURE, CUTOFF, label="row.anchor_timestamp")


def test_injected_future_sector_mapping_fails_loudly() -> None:
    joiner = PointInTimeJoiner(CUTOFF)
    records = joiner.normalize(
        [
            {
                "instrument_id": 1,
                "sector_code": "FUTURE_SECTOR",
                "valid_from": FUTURE,
                "valid_to": None,
            }
        ],
        entity_key="instrument_id",
    )
    # The future mapping is never selected ...
    assert joiner.active_at(records, CUTOFF, entity_key=1) is None
    # ... and the guard refuses it outright.
    with pytest.raises(FutureConstituentError):
        assert_constituent_as_of({"valid_from": FUTURE}, CUTOFF)
    with pytest.raises(FutureConstituentError):
        assert_constituent_as_of({"valid_from": date(2026, 9, 2)}, CUTOFF)


def test_injected_future_full_day_volume_fails_loudly() -> None:
    row = {
        "instrument_id": 1,
        "anchor_timestamp": CUTOFF,
        "session_total_volume": 123456,
        "complete": True,
    }
    scanner = LeakageScanner(cutoff=CUTOFF)
    result = scanner.scan([row], session_close=SESSION_CLOSE)
    assert "FULL_DAY_VOLUME" in result.codes
    with pytest.raises(FullDayVolumeError):
        assert_no_full_day_volume(row, CUTOFF, SESSION_CLOSE)
    with pytest.raises(FullDayVolumeError):
        assert_volume_scope(VolumeScope.SESSION_TOTAL, CUTOFF, SESSION_CLOSE)


def test_injected_future_pivot_confirmation_fails_loudly() -> None:
    row = {
        "instrument_id": 1,
        "anchor_timestamp": CUTOFF,
        "confirmed_at": FUTURE,
        "complete": True,
    }
    scanner = LeakageScanner(cutoff=CUTOFF)
    result = scanner.scan([row])
    assert "FUTURE_PIVOT" in result.codes
    with pytest.raises(FuturePivotError):
        assert_pivot_confirmed_by(row, CUTOFF)


def test_injected_outcome_column_fails_loudly() -> None:
    rows = [
        {
            "instrument_id": 1,
            "anchor_timestamp": CUTOFF,
            "feature_schema_version": "er-feature-schema-v1",
            "mfe_r": 1.4,
            "complete": True,
        }
    ]
    scanner = LeakageScanner(cutoff=CUTOFF)
    result = scanner.scan(rows)
    assert "OUTCOME_COLUMN" in result.codes
    with pytest.raises(OutcomeInFeatureError):
        assert_no_outcome_columns(rows[0].keys(), context="feature")
    with pytest.raises(LeakageDetectedError):
        scanner.assert_clean(rows)


def test_injected_future_normalization_statistic_fails_loudly() -> None:
    baseline = NormalizationBaseline(
        name="atr_zscore",
        fit_start=datetime(2026, 8, 1, tzinfo=UTC),
        fit_end=FUTURE,
        statistics={"mean": 1.0, "std": 0.2},
    )
    with pytest.raises(NormalizationLeakError):
        baseline.assert_fit_not_after(CUTOFF)

    rows = [
        {
            "instrument_id": 1,
            "anchor_timestamp": CUTOFF,
            "atr_zscore": 0.5,
            "fit_end": FUTURE,
            "complete": True,
        }
    ]
    scanner = LeakageScanner(cutoff=CUTOFF)
    result = scanner.scan(rows, normalization_fit_column="fit_end")
    assert "NORMALIZATION_FIT" in result.codes
    with pytest.raises(LeakageDetectedError):
        scanner.assert_clean(rows, normalization_fit_column="fit_end")


def test_normalization_fit_respects_train_test_boundaries() -> None:
    baseline = NormalizationBaseline(
        name="rvol_zscore",
        fit_start=datetime(2020, 1, 1, tzinfo=UTC),
        fit_end=datetime(2020, 12, 31, tzinfo=UTC),
    )
    assert_fit_within_train(
        baseline, datetime(2020, 1, 1, tzinfo=UTC), datetime(2021, 12, 31, tzinfo=UTC)
    )
    with pytest.raises(NormalizationLeakError):
        assert_fit_within_train(
            baseline, datetime(2021, 1, 1, tzinfo=UTC), datetime(2021, 12, 31, tzinfo=UTC)
        )
    with pytest.raises(NormalizationLeakError):
        assert_fit_before_validation(baseline, datetime(2020, 6, 1, tzinfo=UTC))


def test_clean_row_passes_control() -> None:
    rows = [
        {
            "instrument_id": 1,
            "anchor_timestamp": CUTOFF,
            "atr": 1.0,
            "rvol_interval": 1.2,
            "target_reference": 110.0,
            "complete": True,
        }
    ]
    LeakageScanner(cutoff=CUTOFF).assert_clean(rows, session_close=SESSION_CLOSE)
