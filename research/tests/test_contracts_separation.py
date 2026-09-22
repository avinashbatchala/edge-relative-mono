"""Guards that prevent future labels from entering feature/pattern inputs."""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from edge_relative_research import contracts

INPUT_MODULES = ("anchor", "features", "execution", "patterns")
LABEL_FIELD_NAMES = {
    field
    for field in contracts.OutcomeRecord.model_fields
    if field
    not in {
        "anchor_reference",
        "instrument",
        "direction",
        "outcome_schema_version",
        "outcome_definition_version",
        "as_of",
        "ambiguity_policy",
        "value_kind",
        "future_path",
    }
}


def _module_source(module_name: str) -> str:
    module = getattr(contracts, module_name)
    return Path(module.__file__).read_text(encoding="utf-8")  # type: ignore[arg-type]


@pytest.mark.parametrize("module_name", INPUT_MODULES)
def test_input_modules_never_import_outcomes(module_name: str) -> None:
    source = _module_source(module_name)
    assert not re.search(
        r"^\s*(from\s+\.outcomes\s+import|from\s+edge_relative_research\.contracts\.outcomes\s+import|import\s+.*\boutcomes\b)",
        source,
        flags=re.MULTILINE,
    ), f"{module_name} must not import the outcome contract"


def test_label_field_names_do_not_appear_in_feature_contracts() -> None:
    assert LABEL_FIELD_NAMES, "expected outcome contract to carry label fields"
    for model in (
        contracts.ResearchAnchor,
        contracts.FeatureContext,
        contracts.ContextBlock,
        contracts.FeatureValue,
        contracts.ExecutionContext,
        contracts.PatternWindow,
    ):
        overlap = set(model.model_fields) & LABEL_FIELD_NAMES
        assert not overlap, f"{model.__name__} exposes label fields: {overlap}"


def test_no_input_model_references_outcome_record() -> None:
    for model in (
        contracts.ResearchAnchor,
        contracts.FeatureContext,
        contracts.ExecutionContext,
        contracts.PatternWindow,
    ):
        assert "OutcomeRecord" not in str(model.model_fields)


def test_pattern_match_links_outcomes_only_by_reference() -> None:
    fields = contracts.PatternMatch.model_fields
    assert "outcome_reference" in fields
    overlap = set(fields) & LABEL_FIELD_NAMES
    assert not overlap


def test_feature_defaults_are_derived_not_labeled() -> None:
    assert (
        contracts.FeatureContext.model_fields["value_kind"].default is contracts.ValueKind.DERIVED
    )
    assert contracts.FeatureValue.model_fields["value_kind"].default is contracts.ValueKind.DERIVED
    assert contracts.PatternWindow.model_fields["value_kind"].default is contracts.ValueKind.DERIVED
    assert contracts.OutcomeRecord.model_fields["value_kind"].default is contracts.ValueKind.LABELED


def test_horizon_return_defaults_to_labeled() -> None:
    field = contracts.HorizonReturn.model_fields["value_kind"]
    assert field.default is contracts.ValueKind.LABELED
