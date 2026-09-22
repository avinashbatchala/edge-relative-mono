"""Dataset identity and reproducibility metadata.

Mirrors ``research.dataset`` and ``research.dataset_version``. A ``COMMITTED`` version
is content-immutable: it must carry a checksum and commit timestamp, and may later only
become ``RETIRED``. Parent lineage is recorded as stable dataset-version references so a
derived dataset cannot reference a parent relative to a later cutoff by accident.
"""

from __future__ import annotations

from typing import Any
from uuid import UUID

from pydantic import Field, model_validator

from .base import FrozenModel, UtcInstant
from .enums import DatasetStatus, DatasetType


class DatasetIdentity(FrozenModel):
    dataset_code: str
    dataset_type: DatasetType
    version: int = Field(gt=0)
    status: DatasetStatus
    code_version: str

    dataset_key: UUID | None = None
    dataset_version_key: UUID | None = None
    feature_schema_version: str | None = None
    outcome_schema_version: str | None = None
    point_in_time_cutoff: UtcInstant | None = None
    universe: str | None = None
    start_timestamp: UtcInstant | None = None
    end_timestamp: UtcInstant | None = None
    parent_dataset_versions: tuple[str, ...] = ()
    storage_uri: str | None = None
    partition_manifest_uri: str | None = None
    row_count: int | None = Field(default=None, ge=0)
    checksum: str | None = None
    build_parameters: dict[str, Any] = Field(default_factory=dict)
    committed_at: UtcInstant | None = None
    retired_at: UtcInstant | None = None

    @model_validator(mode="after")
    def _commit_shape(self) -> DatasetIdentity:
        if self.status is DatasetStatus.COMMITTED:
            if not self.checksum or not self.checksum.strip():
                raise ValueError("a COMMITTED dataset version requires a checksum")
            if self.committed_at is None:
                raise ValueError("a COMMITTED dataset version requires committed_at")
        return self

    @model_validator(mode="after")
    def _retire_shape(self) -> DatasetIdentity:
        if self.status is DatasetStatus.RETIRED and self.retired_at is None:
            raise ValueError("a RETIRED dataset version requires retired_at")
        return self

    @model_validator(mode="after")
    def _no_failed_credentials(self) -> DatasetIdentity:
        if self.status is DatasetStatus.FAILED and self.committed_at is not None:
            raise ValueError("a FAILED dataset version cannot have committed_at")
        return self

    @model_validator(mode="after")
    def _range_order(self) -> DatasetIdentity:
        if (
            self.start_timestamp is not None
            and self.end_timestamp is not None
            and self.end_timestamp < self.start_timestamp
        ):
            raise ValueError("dataset end_timestamp must not precede start_timestamp")
        return self

    @model_validator(mode="after")
    def _nonblank_uris(self) -> DatasetIdentity:
        for name, value in (
            ("dataset_code", self.dataset_code),
            ("code_version", self.code_version),
        ):
            if not value.strip():
                raise ValueError(f"{name} must be non-blank")
        for name, value in (
            ("storage_uri", self.storage_uri),
            ("partition_manifest_uri", self.partition_manifest_uri),
        ):
            if value is not None and not value.strip():
                raise ValueError(f"{name}, when present, must be non-blank")
        return self

    @property
    def is_immutable(self) -> bool:
        return self.status in (DatasetStatus.COMMITTED, DatasetStatus.RETIRED)
