"""Shared contract machinery.

Every contract is a frozen Pydantic model with ``extra="forbid"``. Freezing prevents a
consumer from mutating a contract in place, and forbidding extra fields means a future
label or an unknown column cannot be smuggled into a contract that is supposed to hold
only point-in-time inputs.
"""

from __future__ import annotations

from datetime import datetime
from typing import Annotated

from pydantic import AfterValidator, BaseModel, ConfigDict

from ..timeutil import ensure_utc

UtcInstant = Annotated[datetime, AfterValidator(ensure_utc)]
"""A timezone-aware instant normalized to UTC. Naive datetimes are rejected."""


class FrozenModel(BaseModel):
    """Immutable, closed-world base for all research contracts."""

    model_config = ConfigDict(frozen=True, extra="forbid", validate_default=True)
