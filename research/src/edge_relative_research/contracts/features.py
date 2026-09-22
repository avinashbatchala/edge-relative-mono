"""Feature and context inputs.

This module is the *input* half of the feature/outcome split. Nothing here may contain a
future label: every value carries an explicit availability and quality, and a
``LABELED`` value kind is rejected outright. Future returns, MFE/MAE and target/stop
results live only in :mod:`edge_relative_research.contracts.outcomes`.
"""

from __future__ import annotations

from datetime import date

from pydantic import Field, model_validator

from .base import FrozenModel, UtcInstant
from .common import FeatureVersionStamp
from .enums import (
    ContextKind,
    FeatureAvailability,
    FeatureQuality,
    Timeframe,
    ValueKind,
)


class FeatureValue(FrozenModel):
    """One point-in-time feature observation with quality, availability and lineage.

    ``value`` is present only when ``availability`` is ``VALID``; a categorical feature
    uses ``label`` instead. A missing value is never represented as ``0.0``.
    """

    feature_key: str
    version: FeatureVersionStamp
    anchor_timestamp: UtcInstant
    timeframe: Timeframe
    availability: FeatureAvailability
    quality: FeatureQuality
    value: float | None = None
    label: str | None = None
    lineage: dict[str, str] = Field(default_factory=dict)
    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _shape(self) -> FeatureValue:
        if self.value_kind is ValueKind.LABELED:
            raise ValueError(
                "feature values are inputs and may never be LABELED; "
                "future outcomes belong in OutcomeRecord"
            )
        has_value = self.value is not None or self.label is not None
        if self.availability is FeatureAvailability.VALID and not has_value:
            raise ValueError(f"VALID feature requires a value or label: {self.feature_key}")
        if self.availability is not FeatureAvailability.VALID and has_value:
            raise ValueError(
                f"non-VALID feature must not carry a value: {self.feature_key} "
                f"({self.availability})"
            )
        if self.value is not None and self.label is not None:
            raise ValueError(
                f"feature carries both numeric and categorical value: {self.feature_key}"
            )
        return self

    @property
    def available(self) -> bool:
        return self.availability is FeatureAvailability.VALID


class ContextBlock(FrozenModel):
    """Point-in-time context for one instrument (market, sector or stock)."""

    context_type: ContextKind
    reference_code: str | None = None
    context_instrument_id: int | None = None
    sector_id: int | None = None
    anchor_timestamp: UtcInstant
    timeframe: Timeframe
    quality: FeatureQuality
    availability: FeatureAvailability
    features: dict[str, FeatureValue] = Field(default_factory=dict)
    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _no_labels(self) -> ContextBlock:
        if self.value_kind is ValueKind.LABELED:
            raise ValueError("a feature context block may never be LABELED")
        return self

    @model_validator(mode="after")
    def _feature_timing(self) -> ContextBlock:
        for key, feature in self.features.items():
            if feature.feature_key != key:
                raise ValueError(f"feature map key {key!r} does not match {feature.feature_key!r}")
            if feature.timeframe is not self.timeframe:
                raise ValueError(
                    f"feature {key} timeframe {feature.timeframe} "
                    f"does not match block {self.timeframe}"
                )
            if feature.anchor_timestamp > self.anchor_timestamp:
                raise ValueError(
                    f"feature {key} anchored after its context block; "
                    "future information is not allowed"
                )
        return self

    def feature(self, key: str) -> FeatureValue | None:
        return self.features.get(key)


class TimeOfDayContext(FrozenModel):
    """Derived, calendar-based context. Never a feature value and never a label."""

    session_date: date
    session_open: UtcInstant
    session_close: UtcInstant
    minutes_since_open: int = Field(ge=0)
    minutes_to_close: int = Field(ge=0)
    day_of_week: int = Field(ge=1, le=7)
    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _session_order(self) -> TimeOfDayContext:
        if self.session_close <= self.session_open:
            raise ValueError("session_close must be after session_open")
        if self.minutes_since_open + self.minutes_to_close > 24 * 60:
            raise ValueError("minutes_since_open and minutes_to_close are inconsistent")
        return self


class FeatureContext(FrozenModel):
    """Point-in-time feature state for one anchor.

    Carries the feature schema and calculation versions, the stock feature block, the
    optional market/sector context blocks, derived time-of-day context and aggregate
    data-quality state. It is measurement only: no strategy decision, position or future
    label is representable.
    """

    feature_schema_version: str
    calculation_version: str
    stock: ContextBlock
    snapshot_quality: FeatureQuality
    snapshot_availability: FeatureAvailability
    market: ContextBlock | None = None
    sector: ContextBlock | None = None
    time_of_day: TimeOfDayContext | None = None
    source_data_revision: str | None = None
    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _context_types(self) -> FeatureContext:
        if self.stock.context_type is not ContextKind.STOCK:
            raise ValueError("stock block must have context_type STOCK")
        if self.market is not None and self.market.context_type is not ContextKind.MARKET:
            raise ValueError("market block must have context_type MARKET")
        if self.sector is not None and self.sector.context_type is not ContextKind.SECTOR:
            raise ValueError("sector block must have context_type SECTOR")
        return self

    @model_validator(mode="after")
    def _aligned_anchors(self) -> FeatureContext:
        for name, block in (("market", self.market), ("sector", self.sector)):
            if block is not None and block.anchor_timestamp != self.stock.anchor_timestamp:
                raise ValueError(
                    f"{name} context anchor {block.anchor_timestamp} does not match "
                    f"stock anchor {self.stock.anchor_timestamp}"
                )
        return self

    @model_validator(mode="after")
    def _no_labels(self) -> FeatureContext:
        if self.value_kind is ValueKind.LABELED:
            raise ValueError("a feature context may never be LABELED")
        return self

    @property
    def feature_versions(self) -> dict[str, str]:
        """Map every stock feature key to its parameter-qualified display version."""

        return {
            key: value.version.display_version for key, value in sorted(self.stock.features.items())
        }

    @property
    def feature_codes(self) -> dict[str, str]:
        return {key: value.version.code for key, value in sorted(self.stock.features.items())}

    def feature(self, key: str) -> FeatureValue | None:
        return self.stock.features.get(key)
