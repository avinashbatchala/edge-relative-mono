"""Unit tests for the reusable point-in-time as-of join."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from edge_relative_research.pit import (
    FutureDataError,
    MissingAsOfError,
    PointInTimeError,
    PointInTimeJoiner,
)

UTC = timezone.utc
ANCHOR = datetime(2026, 9, 1, 4, 10, tzinfo=UTC)


def _sector_rows() -> list[dict]:
    return [
        {
            "instrument_id": 1,
            "sector_code": "ENERGY",
            "valid_from": datetime(2020, 1, 1, tzinfo=UTC),
            "valid_to": datetime(2023, 1, 1, tzinfo=UTC),
        },
        {
            "instrument_id": 1,
            "sector_code": "FINANCE",
            "valid_from": datetime(2023, 1, 1, tzinfo=UTC),
            "valid_to": None,
        },
    ]


def test_joiner_requires_as_of() -> None:
    with pytest.raises(MissingAsOfError):
        PointInTimeJoiner(None)


def test_active_at_returns_effective_row() -> None:
    joiner = PointInTimeJoiner(ANCHOR)
    records = joiner.normalize(_sector_rows(), entity_key="instrument_id")
    assert (
        joiner.active_at(records, datetime(2022, 6, 1, tzinfo=UTC), entity_key=1).payload[
            "sector_code"
        ]
        == "ENERGY"
    )
    assert (
        joiner.active_at(records, datetime(2024, 6, 1, tzinfo=UTC), entity_key=1).payload[
            "sector_code"
        ]
        == "FINANCE"
    )
    assert joiner.active_at(records, datetime(2019, 6, 1, tzinfo=UTC), entity_key=1) is None


def test_future_record_is_not_selected_and_asserted() -> None:
    joiner = PointInTimeJoiner(datetime(2024, 1, 1, tzinfo=UTC))
    records = joiner.normalize(_sector_rows(), entity_key="instrument_id")
    assert joiner.active_at(records, datetime(2019, 6, 1, tzinfo=UTC), entity_key=1) is None
    with pytest.raises(FutureDataError):
        joiner.assert_no_future(records, datetime(2022, 6, 1, tzinfo=UTC))


def test_normalize_validates_windows() -> None:
    joiner = PointInTimeJoiner(ANCHOR)
    with pytest.raises(PointInTimeError):
        joiner.normalize(
            [
                {
                    "instrument_id": 1,
                    "valid_from": datetime(2024, 1, 1, tzinfo=UTC),
                    "valid_to": datetime(2023, 1, 1, tzinfo=UTC),
                }
            ],
            entity_key="instrument_id",
        )


def test_align_attaches_point_in_time_mapping() -> None:
    joiner = PointInTimeJoiner(ANCHOR)
    base = [
        {"instrument_id": 1, "anchor_timestamp": datetime(2022, 6, 1, tzinfo=UTC)},
        {"instrument_id": 1, "anchor_timestamp": datetime(2024, 6, 1, tzinfo=UTC)},
    ]
    aligned = joiner.align(
        base,
        _sector_rows(),
        base_entity_key="instrument_id",
        record_entity_key="instrument_id",
        result_key="sector",
    )
    assert aligned[0]["sector"]["sector_code"] == "ENERGY"
    assert aligned[1]["sector"]["sector_code"] == "FINANCE"


def test_align_rejects_base_row_after_ceiling() -> None:
    joiner = PointInTimeJoiner(datetime(2023, 1, 1, tzinfo=UTC))
    base = [{"instrument_id": 1, "anchor_timestamp": datetime(2024, 6, 1, tzinfo=UTC)}]
    with pytest.raises(FutureDataError):
        joiner.align(
            base,
            _sector_rows(),
            base_entity_key="instrument_id",
            record_entity_key="instrument_id",
        )
