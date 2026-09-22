"""The research anchor: the unit of analysis.

An anchor identifies *what the system knew at a point in time* for one instrument,
timeframe and strategy version. It is the join key between feature inputs and future
outcomes; outcomes are deliberately absent here and live in
:mod:`edge_relative_research.contracts.outcomes`.
"""

from __future__ import annotations

from datetime import date
from uuid import UUID

from pydantic import Field, model_validator

from .base import FrozenModel, UtcInstant
from .common import InstrumentRef
from .enums import Direction, SetupInitialization, SetupState, Timeframe


class ResearchAnchor(FrozenModel):
    """A point-in-time setup/market observation identity.

    The legacy integer id and the durable UUID key are both retained so a row can be
    traced to ``operational.setup_observation`` without relying on surrogate or display
    values. ``setup_instance_id`` is only present once a lifecycle has started.
    """

    instrument: InstrumentRef
    exchange_timestamp: UtcInstant
    timeframe: Timeframe
    strategy_version: str
    setup_state: SetupState
    direction: Direction

    observation_key: UUID | None = None
    observation_id: int | None = None
    market_observation_key: UUID | None = None
    market_observation_id: int | None = None
    setup_observation_key: UUID | None = None
    setup_observation_id: int | None = None
    setup_instance_id: UUID | None = None
    setup_initialization: SetupInitialization | None = None
    strategy_version_id: int | None = None

    session_date: date | None = None
    minutes_since_open: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def _identity_present(self) -> ResearchAnchor:
        if (
            self.observation_id is None
            and self.observation_key is None
            and self.setup_observation_id is None
            and self.setup_observation_key is None
        ):
            raise ValueError(
                "anchor requires at least one observation or setup-observation identity"
            )
        return self

    @model_validator(mode="after")
    def _instance_only_after_lifecycle(self) -> ResearchAnchor:
        if self.setup_state is SetupState.NONE and self.setup_instance_id is not None:
            raise ValueError("setup_instance_id must be absent while setup_state is NONE")
        return self

    @property
    def identity(self) -> str:
        """Stable string identity used to link outcomes and patterns."""

        if self.setup_observation_key is not None:
            return f"setup-observation:{self.setup_observation_key}"
        if self.setup_observation_id is not None:
            return f"setup-observation-id:{self.setup_observation_id}"
        if self.observation_key is not None:
            return f"market-observation:{self.observation_key}"
        return f"market-observation-id:{self.observation_id}"
