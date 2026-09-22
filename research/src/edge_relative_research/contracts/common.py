"""Identity and versioning primitives shared across contracts.

Stable relational identity is the integer ``instrument_id`` (with the durable
``instrument_key`` UUID when available). A symbol is display metadata only and must
never be used to join or key a research artifact.
"""

from __future__ import annotations

import hashlib
from collections.abc import Mapping
from typing import Annotated
from uuid import UUID

from pydantic import ConfigDict, Field, field_validator

from .base import FrozenModel


def compute_parameter_hash(parameters: Mapping[str, object]) -> str:
    """Reproduce the Java ``FeatureVersion`` parameter hash.

    SHA-256 over the sorted ``key=value;`` canonical string, first six bytes as
    lowercase hex. The same parameters must yield the same hash across languages so a
    persisted feature row can be matched to its exact calculation.
    """

    normalized = {str(key): str(value) for key, value in parameters.items()}
    canonical = ";".join(f"{key}={normalized[key]}" for key in sorted(normalized))
    digest = hashlib.sha256(canonical.encode("utf-8")).digest()
    return digest[:6].hex()


class InstrumentRef(FrozenModel):
    """A stable reference to an instrument.

    ``symbol`` is retained so a human can read a row; it is explicitly *not* identity,
    because NSE symbols can change while the economic instrument persists.
    """

    instrument_id: Annotated[int, Field(gt=0)]
    instrument_key: UUID | None = None
    symbol: str | None = Field(
        default=None,
        description="Display metadata only; never used for identity or joins.",
    )

    @field_validator("symbol")
    @classmethod
    def _nonblank_symbol(cls, value: str | None) -> str | None:
        if value is None:
            return None
        stripped = value.strip()
        if not stripped:
            raise ValueError("symbol, when present, must be non-blank")
        return stripped


class FeatureVersionStamp(FrozenModel):
    """Semantic feature version plus the exact parameters that produced it.

    Mirrors Java ``FeatureVersion``: ``semantic_version`` identifies meaning (e.g.
    ``RRS_V1``), ``calculation_version`` identifies an implementation refactor that
    must not change meaning, and ``parameter_hash`` makes parameter sets
    distinguishable so historical rows can never be silently reinterpreted.
    """

    model_config = ConfigDict(
        frozen=True, extra="forbid", validate_default=True, coerce_numbers_to_str=True
    )

    feature_key: str
    semantic_version: str
    calculation_version: str
    parameters: dict[str, str] = Field(default_factory=dict)
    parameter_hash: str

    @field_validator("feature_key", "semantic_version", "calculation_version")
    @classmethod
    def _nonblank(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("version identity fields must be non-blank")
        return stripped

    @field_validator("parameter_hash")
    @classmethod
    def _nonblank_hash(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("parameter_hash must be non-blank")
        return stripped

    @field_validator("parameters")
    @classmethod
    def _stringify_parameters(cls, value: dict[str, object]) -> dict[str, str]:
        return {
            str(key): str(raw) for key, raw in sorted(value.items(), key=lambda item: str(item[0]))
        }

    @classmethod
    def of(
        cls,
        feature_key: str,
        semantic_version: str,
        calculation_version: str,
        parameters: Mapping[str, object],
    ) -> FeatureVersionStamp:
        """Build a stamp as Java does, computing ``parameter_hash``."""

        normalized = {str(key): str(value) for key, value in parameters.items()}
        return cls(
            feature_key=feature_key,
            semantic_version=semantic_version,
            calculation_version=calculation_version,
            parameters=normalized,
            parameter_hash=compute_parameter_hash(normalized),
        )

    @property
    def display_version(self) -> str:
        """Human-readable, parameter-qualified version, e.g. ``RRS_V1@ab12cd34ef56``."""

        return f"{self.semantic_version}@{self.parameter_hash}"

    @property
    def code(self) -> str:
        return f"{self.feature_key}:{self.semantic_version}"
