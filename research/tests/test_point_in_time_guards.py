"""Unit tests for the point-in-time guards."""

from __future__ import annotations

from datetime import date, datetime, timezone

import pytest

from edge_relative_research.pit import (
    FullDayVolumeError,
    FutureConstituentError,
    FutureCorporateActionError,
    FutureDataError,
    FuturePivotError,
    IncompleteBarError,
    MissingAsOfError,
    OutcomeInFeatureError,
    PointInTimeError,
    VolumeScope,
    assert_closed_bar,
    assert_constituent_as_of,
    assert_corporate_action_known,
    assert_feature_timestamps,
    assert_is_active_member,
    assert_no_full_day_volume,
    assert_no_outcome_columns,
    assert_not_future,
    assert_pivot_confirmed_by,
    assert_rows_not_future,
    assert_volume_scope,
    ensure_as_of,
)

UTC = timezone.utc
ANCHOR = datetime(2026, 9, 1, 4, 10, tzinfo=UTC)
SESSION_CLOSE = datetime(2026, 9, 1, 10, 0, tzinfo=UTC)
FUTURE = datetime(2026, 9, 1, 11, 0, tzinfo=UTC)
PAST = datetime(2026, 9, 1, 4, 0, tzinfo=UTC)


def test_as_of_is_required() -> None:
    with pytest.raises(MissingAsOfError):
        ensure_as_of(None)
    assert ensure_as_of(ANCHOR) == ANCHOR


def test_future_timestamp_is_rejected() -> None:
    assert_not_future(PAST, ANCHOR, label="past")
    assert_not_future(ANCHOR, ANCHOR, label="equal")
    with pytest.raises(FutureDataError):
        assert_not_future(FUTURE, ANCHOR, label="future")


def test_rows_not_future() -> None:
    rows = [{"timestamp": PAST}, {"timestamp": FUTURE}]
    with pytest.raises(FutureDataError):
        assert_rows_not_future(rows, ANCHOR)


def test_closed_bar_semantics() -> None:
    assert_closed_bar({"close_time": PAST, "complete": True}, ANCHOR)
    with pytest.raises(IncompleteBarError):
        assert_closed_bar({"close_time": FUTURE, "complete": True}, ANCHOR)
    with pytest.raises(IncompleteBarError):
        assert_closed_bar({"close_time": PAST, "complete": False}, ANCHOR)
    with pytest.raises(IncompleteBarError):
        assert_closed_bar({"close_time": PAST, "quality_state": "INCOMPLETE"}, ANCHOR)


def test_feature_timestamps_validated() -> None:
    assert_feature_timestamps([{"anchor_timestamp": PAST}], ANCHOR)
    with pytest.raises(FutureDataError):
        assert_feature_timestamps([{"anchor_timestamp": FUTURE}], ANCHOR)
    with pytest.raises(PointInTimeError):
        assert_feature_timestamps([{"feature_key": "ATR"}], ANCHOR)


def test_volume_scope_intraday() -> None:
    with pytest.raises(FullDayVolumeError):
        assert_volume_scope(VolumeScope.SESSION_TOTAL, ANCHOR, SESSION_CLOSE)
    assert_volume_scope(VolumeScope.SESSION_TOTAL, SESSION_CLOSE, SESSION_CLOSE)
    assert_volume_scope(VolumeScope.CUMULATIVE_TO_ANCHOR, ANCHOR, SESSION_CLOSE)
    assert_volume_scope(VolumeScope.INTERVAL, ANCHOR, SESSION_CLOSE)


def test_full_day_volume_columns_rejected_intraday() -> None:
    with pytest.raises(FullDayVolumeError):
        assert_no_full_day_volume({"session_total_volume": 12345}, ANCHOR, SESSION_CLOSE)
    assert_no_full_day_volume({"session_total_volume": 12345}, SESSION_CLOSE, SESSION_CLOSE)
    assert_no_full_day_volume({"rvol_interval": 1.4}, ANCHOR, SESSION_CLOSE)
    with pytest.raises(FullDayVolumeError):
        assert_no_full_day_volume(["atr", "day_volume"], ANCHOR, SESSION_CLOSE)


def test_pivot_confirmation() -> None:
    assert_pivot_confirmed_by({"confirmed_at": PAST}, ANCHOR)
    with pytest.raises(FuturePivotError):
        assert_pivot_confirmed_by({"confirmed_at": FUTURE}, ANCHOR)
    with pytest.raises(FuturePivotError):
        assert_pivot_confirmed_by({"pivot_price": 100.0}, ANCHOR)


def test_constituent_and_membership() -> None:
    assert_constituent_as_of({"valid_from": date(2020, 1, 1)}, ANCHOR)
    with pytest.raises(FutureConstituentError):
        assert_constituent_as_of({"valid_from": date(2027, 1, 1)}, ANCHOR)
    assert_is_active_member({"valid_from": date(2020, 1, 1), "valid_to": None}, ANCHOR)
    with pytest.raises(FutureConstituentError):
        assert_is_active_member(
            {"valid_from": date(2020, 1, 1), "valid_to": date(2026, 1, 1)}, ANCHOR
        )


def test_corporate_action_known_time() -> None:
    assert_corporate_action_known({"available_at": PAST}, ANCHOR)
    with pytest.raises(FutureCorporateActionError):
        assert_corporate_action_known({"available_at": FUTURE}, ANCHOR)
    with pytest.raises(FutureCorporateActionError):
        assert_corporate_action_known({"ex_date": date(2026, 9, 2)}, ANCHOR)
    with pytest.raises(FutureCorporateActionError):
        assert_corporate_action_known({"action_type": "SPLIT"}, ANCHOR)


def test_outcome_columns_rejected() -> None:
    assert_no_outcome_columns(["atr", "rvol_interval", "target_reference", "label"])
    with pytest.raises(OutcomeInFeatureError):
        assert_no_outcome_columns(["atr", "mfe_r"])
    with pytest.raises(OutcomeInFeatureError):
        assert_no_outcome_columns(["return_5m"])
    with pytest.raises(OutcomeInFeatureError):
        assert_no_outcome_columns(["target_before_stop"])
