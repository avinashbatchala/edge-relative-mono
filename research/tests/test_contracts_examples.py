"""Examples built from the shared cross-language feature fixtures.

These tests prove the Python contracts can carry the exact values the Java engine
freezes in ``contracts/fixtures/features`` without interpreting them.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from edge_relative_research import contracts

REPO_ROOT = Path(__file__).resolve().parents[2]
SHARED_FEATURE_FIXTURES = REPO_ROOT / "contracts" / "fixtures" / "features"

pytestmark = pytest.mark.skipif(
    not SHARED_FEATURE_FIXTURES.is_dir(),
    reason="shared feature fixtures are not present",
)


def _shared(name: str) -> dict:
    return json.loads((SHARED_FEATURE_FIXTURES / name).read_text(encoding="utf-8"))


def _last_non_null(values: list[object]) -> object:
    for value in reversed(values):
        if value is not None:
            return value
    raise AssertionError("fixture has no expected value")


def test_atr_fixture_maps_to_feature_context() -> None:
    fixture = _shared("atr-v1.json")
    anchor = fixture["bars"][-1]["closeTime"]
    expected = _last_non_null(fixture["expected"])
    version = contracts.FeatureVersionStamp.of(
        "ATR", fixture["semanticVersion"], "er-feature-calc-v1", fixture["parameters"]
    )
    value = contracts.FeatureValue(
        feature_key="ATR",
        version=version,
        anchor_timestamp=anchor,
        timeframe=contracts.Timeframe.M5,
        availability=contracts.FeatureAvailability.VALID,
        quality=contracts.FeatureQuality.GOOD,
        value=float(expected),
    )
    context = contracts.FeatureContext(
        feature_schema_version="er-feature-schema-v1",
        calculation_version="er-feature-calc-v1",
        snapshot_quality=contracts.FeatureQuality.GOOD,
        snapshot_availability=contracts.FeatureAvailability.VALID,
        stock=contracts.ContextBlock(
            context_type=contracts.ContextKind.STOCK,
            anchor_timestamp=anchor,
            timeframe=contracts.Timeframe.M5,
            quality=contracts.FeatureQuality.GOOD,
            availability=contracts.FeatureAvailability.VALID,
            features={"ATR": value},
        ),
        time_of_day=contracts.TimeOfDayContext(
            session_date="2026-09-01",
            session_open="2026-09-01T03:45:00Z",
            session_close="2026-09-01T10:00:00Z",
            minutes_since_open=20,
            minutes_to_close=355,
            day_of_week=2,
        ),
    )
    assert context.feature("ATR").value == 2.0  # type: ignore[union-attr]
    assert context.feature_versions["ATR"].startswith("ATR_V1@")
    assert context.feature_versions["ATR"] == version.display_version
    assert contracts.FeatureContext.model_validate_json(context.model_dump_json()) == context


def test_rrs_fixture_carries_numeric_and_categorical_values() -> None:
    fixture = _shared("rrs-v1.json")
    anchor = fixture["bars"][-1]["closeTime"]
    version = contracts.FeatureVersionStamp.of(
        "RRS_RAW",
        fixture["semanticVersion"],
        "er-feature-calc-v1",
        fixture["parameters"],
    )
    raw = contracts.FeatureValue(
        feature_key="RRS_RAW",
        version=version,
        anchor_timestamp=anchor,
        timeframe=contracts.Timeframe.M5,
        availability=contracts.FeatureAvailability.VALID,
        quality=contracts.FeatureQuality.GOOD,
        value=float(_last_non_null(fixture["expectedRaw"])),
    )
    trend_version = contracts.FeatureVersionStamp.of(
        "RRS_TREND_STATE",
        fixture["semanticVersion"],
        "er-feature-calc-v1",
        fixture["parameters"],
    )
    trend = contracts.FeatureValue(
        feature_key="RRS_TREND_STATE",
        version=trend_version,
        anchor_timestamp=anchor,
        timeframe=contracts.Timeframe.M5,
        availability=contracts.FeatureAvailability.VALID,
        quality=contracts.FeatureQuality.GOOD,
        label=str(_last_non_null(fixture["expectedTrendState"])),
    )
    assert raw.value == 1.0
    assert trend.label == "NEUTRAL"
    assert raw.value_kind is contracts.ValueKind.DERIVED
    round_tripped = contracts.FeatureValue.model_validate_json(raw.model_dump_json())
    assert round_tripped.value == 1.0


def test_parameter_hash_matches_java_canonicalization() -> None:
    params = {"smoothing": "WILDER", "length": 3}
    assert contracts.compute_parameter_hash(params) == contracts.compute_parameter_hash(
        {"length": 3, "smoothing": "WILDER"}
    )
    stamp = contracts.FeatureVersionStamp.of("ATR", "ATR_V1", "er-feature-calc-v1", params)
    assert stamp.parameter_hash == contracts.compute_parameter_hash(stamp.parameters)
    assert len(stamp.parameter_hash) == 12
