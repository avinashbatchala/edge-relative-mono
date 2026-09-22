"""Schema and validation rules that make the contracts explicit and closed-world."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import pytest
from pydantic import ValidationError

from edge_relative_research import contracts

FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"


def _load(name: str) -> dict:
    return json.loads((FIXTURES_DIR / name).read_text(encoding="utf-8"))


def _version(key: str) -> contracts.FeatureVersionStamp:
    return contracts.FeatureVersionStamp(
        feature_key=key,
        semantic_version="RRS_V1",
        calculation_version="er-feature-calc-v1",
        parameter_hash="deadbeef1234",
    )


def test_extra_fields_are_forbidden_everywhere() -> None:
    with pytest.raises(ValidationError):
        contracts.InstrumentRef(instrument_id=1, unexpected="x")  # type: ignore[call-arg]
    with pytest.raises(ValidationError):
        contracts.ResearchAnchor.model_validate(
            {**_load("research_anchor.json"), "future_label": 1.0}
        )
    with pytest.raises(ValidationError):
        contracts.FeatureContext.model_validate({**_load("feature_context.json"), "mfe_r": 1.0})


def test_instrument_identity_requires_instrument_id() -> None:
    with pytest.raises(ValidationError):
        contracts.InstrumentRef(symbol="RELIANCE")  # type: ignore[call-arg]
    ref = contracts.InstrumentRef(instrument_id=7, symbol=" RELIANCE ")
    assert ref.symbol == "RELIANCE"
    assert ref.instrument_id == 7


def test_feature_value_valid_requires_value() -> None:
    with pytest.raises(ValidationError):
        contracts.FeatureValue(
            feature_key="ATR",
            version=_version("ATR"),
            anchor_timestamp=datetime(2026, 9, 1, 4, 5, tzinfo=timezone.utc),
            timeframe=contracts.Timeframe.M5,
            availability=contracts.FeatureAvailability.VALID,
            quality=contracts.FeatureQuality.GOOD,
        )


def test_feature_value_non_valid_cannot_carry_value() -> None:
    with pytest.raises(ValidationError):
        contracts.FeatureValue(
            feature_key="ATR",
            version=_version("ATR"),
            anchor_timestamp=datetime(2026, 9, 1, 4, 5, tzinfo=timezone.utc),
            timeframe=contracts.Timeframe.M5,
            availability=contracts.FeatureAvailability.STALE,
            quality=contracts.FeatureQuality.STALE,
            value=1.0,
        )


def test_feature_value_cannot_be_labeled() -> None:
    with pytest.raises(ValidationError):
        contracts.FeatureValue(
            feature_key="ATR",
            version=_version("ATR"),
            anchor_timestamp=datetime(2026, 9, 1, 4, 5, tzinfo=timezone.utc),
            timeframe=contracts.Timeframe.M5,
            availability=contracts.FeatureAvailability.VALID,
            quality=contracts.FeatureQuality.GOOD,
            value=1.0,
            value_kind=contracts.ValueKind.LABELED,
        )


def test_feature_value_rejects_numeric_and_label_together() -> None:
    with pytest.raises(ValidationError):
        contracts.FeatureValue(
            feature_key="RRS_TREND_STATE",
            version=_version("RRS_TREND_STATE"),
            anchor_timestamp=datetime(2026, 9, 1, 4, 5, tzinfo=timezone.utc),
            timeframe=contracts.Timeframe.M5,
            availability=contracts.FeatureAvailability.VALID,
            quality=contracts.FeatureQuality.GOOD,
            value=1.0,
            label="NEUTRAL",
        )


def test_context_block_rejects_future_feature() -> None:
    anchor = datetime(2026, 9, 1, 4, 5, tzinfo=timezone.utc)
    later = datetime(2026, 9, 1, 4, 10, tzinfo=timezone.utc)
    with pytest.raises(ValidationError):
        contracts.ContextBlock(
            context_type=contracts.ContextKind.STOCK,
            anchor_timestamp=anchor,
            timeframe=contracts.Timeframe.M5,
            quality=contracts.FeatureQuality.GOOD,
            availability=contracts.FeatureAvailability.VALID,
            features={
                "ATR": contracts.FeatureValue(
                    feature_key="ATR",
                    version=_version("ATR"),
                    anchor_timestamp=later,
                    timeframe=contracts.Timeframe.M5,
                    availability=contracts.FeatureAvailability.VALID,
                    quality=contracts.FeatureQuality.GOOD,
                    value=1.0,
                )
            },
        )


def test_feature_context_requires_aligned_context_anchor() -> None:
    data = _load("feature_context.json")
    data["market"]["anchor_timestamp"] = "2026-09-01T04:10:00Z"
    with pytest.raises(ValidationError):
        contracts.FeatureContext.model_validate(data)


def test_dataset_commit_shape_is_enforced() -> None:
    base = _load("dataset_identity.json")
    missing_checksum = {**base, "checksum": None}
    with pytest.raises(ValidationError):
        contracts.DatasetIdentity.model_validate(missing_checksum)
    missing_committed_at = {**base, "committed_at": None}
    with pytest.raises(ValidationError):
        contracts.DatasetIdentity.model_validate(missing_committed_at)
    failed_with_commit = {**base, "status": "FAILED"}
    with pytest.raises(ValidationError):
        contracts.DatasetIdentity.model_validate(failed_with_commit)
    retired_without_time = {**base, "status": "RETIRED", "retired_at": None}
    with pytest.raises(ValidationError):
        contracts.DatasetIdentity.model_validate(retired_without_time)


def test_pattern_source_range_cannot_exceed_anchor() -> None:
    data = _load("pattern_window.json")
    data["source_observation_end"] = "2026-09-01T05:00:00Z"
    with pytest.raises(ValidationError):
        contracts.PatternWindow.model_validate(data)


def test_pattern_match_cannot_reference_future_pattern() -> None:
    data = _load("pattern_match.json")
    data["as_of_limit"] = "2026-08-01T00:00:00Z"
    with pytest.raises(ValidationError):
        contracts.PatternMatch.model_validate(data)


def test_research_anchor_requires_identity() -> None:
    data = _load("research_anchor.json")
    for key in (
        "observation_key",
        "observation_id",
        "setup_observation_key",
        "setup_observation_id",
    ):
        data.pop(key, None)
    with pytest.raises(ValidationError):
        contracts.ResearchAnchor.model_validate(data)


def test_research_anchor_instance_requires_lifecycle() -> None:
    data = _load("research_anchor.json")
    data["setup_state"] = "NONE"
    with pytest.raises(ValidationError):
        contracts.ResearchAnchor.model_validate(data)


def test_naive_timestamps_are_rejected() -> None:
    data = _load("research_anchor.json")
    data["exchange_timestamp"] = "2026-09-01T04:05:00"
    with pytest.raises(ValidationError):
        contracts.ResearchAnchor.model_validate(data)


def test_setup_state_covers_persisted_vocabulary() -> None:
    persisted = {
        "NONE",
        "WATCH",
        "FORMING",
        "NEAR_TRIGGER",
        "VALID",
        "INVALIDATED",
        "EXPIRED",
        "MISSED",
        "BLOCKED",
        "REJECTED",
    }
    assert {state.value for state in contracts.SetupState} == persisted


def test_quality_severity_and_worst() -> None:
    assert contracts.FeatureQuality.GOOD.trustworthy is True
    assert contracts.FeatureQuality.INCOMPLETE.trustworthy is False
    assert (
        contracts.worst_quality([contracts.FeatureQuality.GOOD, contracts.FeatureQuality.STALE])
        is contracts.FeatureQuality.STALE
    )
    assert contracts.worst_quality([]) is contracts.FeatureQuality.GOOD


def test_json_schema_exposes_required_identity_fields() -> None:
    schema = contracts.ResearchAnchor.model_json_schema()
    required = set(schema["required"])
    assert {"instrument", "exchange_timestamp", "timeframe", "setup_state"} <= required
    feature_schema = contracts.FeatureContext.model_json_schema()
    assert {"feature_schema_version", "stock", "snapshot_quality"} <= set(
        feature_schema["properties"]
    )
    assert "value_kind" in feature_schema["properties"]


def test_strategy_and_schema_versions_are_never_hidden() -> None:
    feature = contracts.FeatureContext.model_json_schema()["properties"]
    assert "feature_schema_version" in feature
    assert "calculation_version" in feature
    dataset = contracts.DatasetIdentity.model_json_schema()["properties"]
    assert "code_version" in dataset
    outcome = contracts.OutcomeRecord.model_json_schema()["properties"]
    assert "outcome_schema_version" in outcome
    assert "outcome_definition_version" in outcome
    pattern = contracts.PatternWindow.model_json_schema()["properties"]
    assert "pattern_schema_version" in pattern
    assert "normalization_version" in pattern
    match = contracts.PatternMatch.model_json_schema()["properties"]
    assert "method_version" in match
