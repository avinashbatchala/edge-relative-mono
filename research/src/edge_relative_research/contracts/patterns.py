"""Pattern-window representations and similarity matches.

A pattern is a point-in-time sequence around an anchor. The tensor itself is not held
here (it belongs in columnar/object storage); this contract carries deterministic
lineage: which anchor, which schema and normalization versions, which source observation
range, and a storage reference. A :class:`PatternMatch` references an outcome by stable
key only and never embeds outcome values.
"""

from __future__ import annotations

from uuid import UUID

from pydantic import Field, model_validator

from .anchor import ResearchAnchor
from .base import FrozenModel, UtcInstant
from .common import InstrumentRef
from .enums import Cohort, Timeframe, ValueKind


class PatternWindow(FrozenModel):
    """Versioned, point-in-time sequence identity for one anchor."""

    pattern_key: UUID
    anchor: ResearchAnchor
    instrument: InstrumentRef
    timeframe: Timeframe
    feature_schema_version: str
    pattern_schema_version: str
    normalization_version: str | None = None
    source_observation_start: UtcInstant
    source_observation_end: UtcInstant
    sequence_length: int = Field(gt=0)
    market_regime: str | None = None
    sector_regime: str | None = None
    sector_id: int | None = None
    minutes_since_open: int | None = Field(default=None, ge=0)
    storage_uri: str
    storage_row_reference: str | None = None
    dataset_version_reference: str | None = None
    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _no_labels(self) -> PatternWindow:
        if self.value_kind is ValueKind.LABELED:
            raise ValueError(
                "a pattern window must not contain labels; outcomes live in OutcomeRecord"
            )
        return self

    @model_validator(mode="after")
    def _window_consistency(self) -> PatternWindow:
        if self.source_observation_end < self.source_observation_start:
            raise ValueError("source observation range is inverted")
        if self.source_observation_end > self.anchor.exchange_timestamp:
            raise ValueError(
                "pattern source range extends past its anchor; future information is not allowed"
            )
        if not self.storage_uri.strip():
            raise ValueError("storage_uri must be non-blank")
        return self


class PatternMatch(FrozenModel):
    """A similarity match between a query pattern and a historical pattern.

    ``outcome_reference`` is a stable key, not an outcome. The match itself carries no
    future data, so it can be produced inside a point-in-time cutoff safely.
    """

    query_pattern_key: UUID
    matched_pattern_key: UUID
    matched_anchor: ResearchAnchor
    matched_instrument: InstrumentRef
    matched_anchor_timestamp: UtcInstant
    cohort: Cohort
    method_version: str
    score: float
    rank: int = Field(gt=0)
    as_of_limit: UtcInstant
    outcome_reference: str | None = None
    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _no_labels(self) -> PatternMatch:
        if self.value_kind is ValueKind.LABELED:
            raise ValueError("a pattern match is not a label")
        return self

    @model_validator(mode="after")
    def _matched_anchor_consistency(self) -> PatternMatch:
        if self.matched_anchor.exchange_timestamp != self.matched_anchor_timestamp:
            raise ValueError("matched anchor timestamp does not match its anchor")
        if self.matched_anchor.instrument.instrument_id != self.matched_instrument.instrument_id:
            raise ValueError("matched instrument does not match the matched anchor")
        return self

    @model_validator(mode="after")
    def _cutoff(self) -> PatternMatch:
        if self.matched_anchor_timestamp > self.as_of_limit:
            raise ValueError(
                "matched anchor is later than as_of_limit; future patterns are not allowed"
            )
        return self
