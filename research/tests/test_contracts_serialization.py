"""Serialization round-trips for every canonical research contract."""

from __future__ import annotations

import json
from decimal import Decimal
from pathlib import Path

import pytest
from pydantic import BaseModel, ValidationError

from edge_relative_research import contracts

FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"

ROUND_TRIP_CASES = [
    (contracts.ResearchAnchor, "research_anchor.json"),
    (contracts.FeatureContext, "feature_context.json"),
    (contracts.ExecutionContext, "execution_context.json"),
    (contracts.OutcomeRecord, "outcome_record.json"),
    (contracts.DatasetIdentity, "dataset_identity.json"),
    (contracts.PatternWindow, "pattern_window.json"),
    (contracts.PatternMatch, "pattern_match.json"),
]


def _load(name: str) -> dict:
    return json.loads((FIXTURES_DIR / name).read_text(encoding="utf-8"))


@pytest.mark.parametrize(("model", "fixture"), ROUND_TRIP_CASES)
def test_json_round_trip_is_lossless(model: type[BaseModel], fixture: str) -> None:
    first = model.model_validate(_load(fixture))
    reloaded = model.model_validate_json(first.model_dump_json())
    assert reloaded == first
    assert reloaded.model_dump() == first.model_dump()


@pytest.mark.parametrize(("model", "fixture"), ROUND_TRIP_CASES)
def test_python_dict_round_trip(model: type[BaseModel], fixture: str) -> None:
    first = model.model_validate(_load(fixture))
    reloaded = model.model_validate(first.model_dump())
    assert reloaded == first


def test_missing_value_is_preserved_not_zeroed(
    anchor: contracts.ResearchAnchor,
) -> None:
    value = contracts.FeatureValue(
        feature_key="RRS_RAW",
        version=contracts.FeatureVersionStamp(
            feature_key="RRS_RAW",
            semantic_version="RRS_V1",
            calculation_version="er-feature-calc-v1",
            parameter_hash="deadbeef1234",
        ),
        anchor_timestamp=anchor.exchange_timestamp,
        timeframe=contracts.Timeframe.M5,
        availability=contracts.FeatureAvailability.INSUFFICIENT_HISTORY,
        quality=contracts.FeatureQuality.INCOMPLETE,
    )
    dumped = json.loads(value.model_dump_json())
    assert dumped["value"] is None
    assert dumped["label"] is None
    assert dumped["availability"] == "INSUFFICIENT_HISTORY"
    assert value.available is False


def test_decimal_money_survives_json(
    execution_context: contracts.ExecutionContext,
) -> None:
    dumped = execution_context.model_dump_json()
    reloaded = contracts.ExecutionContext.model_validate_json(dumped)
    assert reloaded.average_entry_price == Decimal("100.75")
    assert reloaded.explicit_costs is not None
    assert reloaded.explicit_costs.total == Decimal("41.10")
    assert reloaded.net_pnl == Decimal("903.90")
    assert isinstance(reloaded.net_pnl, Decimal)


def test_enums_serialize_as_strings(anchor: contracts.ResearchAnchor) -> None:
    dumped = json.loads(anchor.model_dump_json())
    assert dumped["direction"] == "LONG"
    assert dumped["setup_state"] == "VALID"
    assert dumped["timeframe"] == "M5"


def test_contracts_are_frozen(anchor: contracts.ResearchAnchor) -> None:
    with pytest.raises((ValidationError, TypeError)):
        anchor.setup_state = contracts.SetupState.WATCH  # type: ignore[misc]


def test_fill_quantities_reconcile(
    execution_context: contracts.ExecutionContext,
) -> None:
    assert sum(fill.quantity for fill in execution_context.entry_fills) == 100
    assert execution_context.filled_entry_quantity == 100
    assert execution_context.entry_timestamp is not None
    assert execution_context.exit_timestamp is not None
    assert execution_context.entry_timestamp < execution_context.exit_timestamp
