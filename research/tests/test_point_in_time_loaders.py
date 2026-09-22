"""Integration tests for repository-backed point-in-time loaders."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from edge_relative_research.pit import MissingAsOfError, PointInTimeLoader

UTC = timezone.utc


def _loader(repos, when: datetime) -> PointInTimeLoader:
    return PointInTimeLoader(repos.reference, operational=repos.operational, as_of_timestamp=when)


def test_loader_requires_as_of(repos) -> None:
    with pytest.raises(MissingAsOfError):
        PointInTimeLoader(repos.reference, operational=repos.operational, as_of_timestamp=None)


def test_symbol_change_is_point_in_time(repos) -> None:
    assert (
        _loader(repos, datetime(2019, 6, 1, 10, 0, tzinfo=UTC)).identity(1).identifier_value
        == "RELIANCEOLD"
    )
    assert (
        _loader(repos, datetime(2022, 1, 1, 10, 0, tzinfo=UTC)).identity(1).identifier_value
        == "RELIANCE"
    )
    assert (
        _loader(repos, datetime(2025, 6, 1, 10, 0, tzinfo=UTC)).identity(1).identifier_value
        == "RELIANCE-NEW"
    )


def test_sector_mapping_is_point_in_time(repos) -> None:
    assert _loader(repos, datetime(2022, 1, 1, 10, 0, tzinfo=UTC)).sector(1).sector_code == "ENERGY"
    assert (
        _loader(repos, datetime(2024, 1, 1, 10, 0, tzinfo=UTC)).sector(1).sector_code == "FINANCE"
    )


def test_benchmark_membership_is_point_in_time(repos) -> None:
    early = _loader(repos, datetime(2023, 6, 1, 10, 0, tzinfo=UTC))
    assert early.benchmark_membership(1).benchmark_code == "NIFTY50"
    late = _loader(repos, datetime(2025, 1, 1, 10, 0, tzinfo=UTC))
    assert late.benchmark_membership(1) is None


def test_universe_membership_before_effective_is_empty(repos) -> None:
    loader = _loader(repos, datetime(2019, 6, 1, 10, 0, tzinfo=UTC))
    assert loader.universe_members(1) == []
    assert [
        member.instrument_id
        for member in _loader(repos, datetime(2024, 1, 1, 10, 0, tzinfo=UTC)).universe_members(1)
    ] == [1]


def test_setup_observations_are_bounded_by_as_of(repos) -> None:
    loader = _loader(repos, datetime(2026, 9, 1, 4, 6, 0, tzinfo=UTC))
    rows = loader.setup_observations(1)
    assert [row.setup_observation_id for row in rows] == [1]
    assert all(row.observed_at <= loader.as_of_timestamp for row in rows)


def test_trading_sessions_as_of(repos) -> None:
    loader = _loader(repos, datetime(2026, 9, 1, 4, 6, 0, tzinfo=UTC))
    sessions = loader.trading_sessions(1)
    assert len(sessions) == 1
    assert sessions[0].session_type == "NORMAL"


def test_feature_alignment_uses_joiner(repos) -> None:
    loader = _loader(repos, datetime(2026, 9, 1, 4, 6, 0, tzinfo=UTC))
    base = [{"instrument_id": 1, "anchor_timestamp": datetime(2026, 9, 1, 4, 5, 30, tzinfo=UTC)}]
    features = [
        {
            "instrument_id": 1,
            "feature_key": "ATR",
            "anchor_timestamp": datetime(2026, 9, 1, 4, 5, 0, tzinfo=UTC),
        }
    ]
    aligned = loader.align_features(
        base,
        features,
        base_entity_key="instrument_id",
        record_entity_key="instrument_id",
        base_anchor_key="anchor_timestamp",
        valid_from_key="anchor_timestamp",
        result_key="feature",
    )
    assert aligned[0]["feature"]["feature_key"] == "ATR"
