"""Future-outcome labels.

This is the *output* half of the feature/outcome split and the only module permitted to
carry ``LABELED`` data. It is never imported by the feature, anchor, execution or
pattern contracts, so a feature row cannot physically acquire a future column. An
outcome links back to an anchor by stable string identity only.
"""

from __future__ import annotations

from pydantic import Field, model_validator

from .base import FrozenModel, UtcInstant
from .common import InstrumentRef
from .enums import AmbiguityPolicy, Direction, LabelState, ValueKind


class HorizonReturn(FrozenModel):
    """Forward return over a fixed horizon from the anchor reference price."""

    horizon_seconds: int = Field(gt=0)
    return_fraction: float | None = None
    reference_price: float | None = None
    terminal_price: float | None = None
    available: bool = False
    value_kind: ValueKind = ValueKind.LABELED

    @model_validator(mode="after")
    def _labeled_only(self) -> HorizonReturn:
        if self.value_kind is not ValueKind.LABELED:
            raise ValueError("horizon returns are labels and must be LABELED")
        if self.available and self.return_fraction is None:
            raise ValueError("available horizon return requires return_fraction")
        return self


class FuturePathMetadata(FrozenModel):
    """What the future window actually contained, so coverage is explicit."""

    bar_count: int = Field(ge=0)
    first_bar_at: UtcInstant | None = None
    last_bar_at: UtcInstant | None = None
    missing_bars: int = Field(ge=0, default=0)
    truncated_at_session_close: bool = False
    data_quality: str | None = None
    value_kind: ValueKind = ValueKind.LABELED

    @property
    def complete(self) -> bool:
        return not self.truncated_at_session_close and self.missing_bars == 0 and self.bar_count > 0


class OutcomeRecord(FrozenModel):
    """Versioned future outcomes for one anchor.

    Long and short arithmetic is direction-aware. Where the intrabar ordering of target
    and stop is unknowable from bar data, ``target_before_stop`` must be ``UNKNOWN``
    rather than fabricated.
    """

    anchor_reference: str
    instrument: InstrumentRef
    direction: Direction
    outcome_schema_version: str
    outcome_definition_version: str
    as_of: UtcInstant

    horizon_returns: tuple[HorizonReturn, ...] = ()
    maximum_favourable_excursion: float | None = Field(default=None, ge=0)
    maximum_adverse_excursion: float | None = Field(default=None, ge=0)
    mfe_r: float | None = Field(default=None, ge=0)
    mae_r: float | None = Field(default=None, ge=0)
    target_before_stop: LabelState | None = None
    target_hit: LabelState | None = None
    stop_hit: LabelState | None = None
    time_to_target_seconds: int | None = Field(default=None, ge=0)
    time_to_stop_seconds: int | None = Field(default=None, ge=0)
    time_to_mfe_seconds: int | None = Field(default=None, ge=0)
    time_to_mae_seconds: int | None = Field(default=None, ge=0)
    ambiguity_policy: AmbiguityPolicy = AmbiguityPolicy.STOP_FIRST_CONSERVATIVE
    ambiguous: bool = False
    future_path: FuturePathMetadata | None = None
    value_kind: ValueKind = ValueKind.LABELED

    @model_validator(mode="after")
    def _labeled_only(self) -> OutcomeRecord:
        if self.value_kind is not ValueKind.LABELED:
            raise ValueError("OutcomeRecord is a label and must be LABELED")
        if not self.anchor_reference.strip():
            raise ValueError("outcome must reference a stable anchor identity")
        return self
