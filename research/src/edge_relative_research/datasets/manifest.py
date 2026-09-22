"""Deterministic dataset manifest.

A manifest is the authoritative descriptor of a stored dataset version. Its content
checksum is computed over the logical build descriptor only: identity, schema versions,
cutoff, universe, time range, parents, partition content checksums, row count, code
version and build parameters. Volatile fields (wall-clock ``created_at``, absolute
storage URI, per-file byte checksums) are deliberately excluded so identical inputs
produce an identical checksum.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

from pydantic import Field, field_validator

from ..contracts.base import FrozenModel, UtcInstant
from ..contracts.enums import DatasetType
from .checksums import checksum_payload

_VERSION_KEY_NAMESPACE = uuid5(NAMESPACE_URL, "edge-relative-research.dataset")


class ParentRef(FrozenModel):
    """An exact, checksummed parent dataset version."""

    dataset_code: str
    version: int = Field(gt=0)
    dataset_version_key: UUID
    checksum: str


class PartitionEntry(FrozenModel):
    """One stored partition file plus its logical and byte checksums."""

    partition_key: str
    path: str
    row_count: int = Field(ge=0)
    content_checksum: str
    file_checksum: str
    byte_size: int = Field(ge=0)

    @field_validator("partition_key")
    @classmethod
    def _safe_key(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("partition_key must be non-blank")
        if any(char in stripped for char in "/\\"):
            raise ValueError("partition_key must not contain path separators")
        return stripped


class DatasetManifest(FrozenModel):
    dataset_code: str
    dataset_type: DatasetType
    version: int = Field(gt=0)
    feature_schema_version_id: int | None = None
    outcome_schema_version_id: int | None = None
    point_in_time_cutoff: UtcInstant | None = None
    universe: str | None = None
    start_timestamp: UtcInstant | None = None
    end_timestamp: UtcInstant | None = None
    parents: tuple[ParentRef, ...] = ()
    storage_uri: str
    partitions: tuple[PartitionEntry, ...] = ()
    row_count: int = Field(ge=0)
    code_version: str
    build_parameters: dict[str, Any] = Field(default_factory=dict)
    created_at: UtcInstant
    checksum: str

    def identity_payload(self) -> dict[str, Any]:
        """The deterministic subset of the manifest used for the content checksum."""

        return {
            "dataset_code": self.dataset_code,
            "dataset_type": self.dataset_type.value,
            "version": self.version,
            "feature_schema_version_id": self.feature_schema_version_id,
            "outcome_schema_version_id": self.outcome_schema_version_id,
            "point_in_time_cutoff": self.point_in_time_cutoff,
            "universe": self.universe,
            "start_timestamp": self.start_timestamp,
            "end_timestamp": self.end_timestamp,
            "parents": sorted(
                (
                    {
                        "dataset_code": parent.dataset_code,
                        "version": parent.version,
                        "dataset_version_key": parent.dataset_version_key,
                        "checksum": parent.checksum,
                    }
                    for parent in self.parents
                ),
                key=lambda item: (item["dataset_code"], item["version"]),
            ),
            "partitions": sorted(
                (
                    {
                        "partition_key": partition.partition_key,
                        "row_count": partition.row_count,
                        "content_checksum": partition.content_checksum,
                    }
                    for partition in self.partitions
                ),
                key=lambda item: item["partition_key"],
            ),
            "row_count": self.row_count,
            "code_version": self.code_version,
            "build_parameters": self.build_parameters,
        }

    @property
    def dataset_version_key(self) -> UUID:
        """Deterministic key derived from identity and content checksum."""

        return uuid5(
            _VERSION_KEY_NAMESPACE,
            f"{self.dataset_code}:v{self.version}:{self.checksum}",
        )

    @classmethod
    def create(
        cls,
        *,
        dataset_code: str,
        dataset_type: DatasetType,
        version: int,
        storage_uri: str,
        code_version: str,
        created_at: UtcInstant,
        feature_schema_version_id: int | None = None,
        outcome_schema_version_id: int | None = None,
        point_in_time_cutoff: UtcInstant | None = None,
        universe: str | None = None,
        start_timestamp: UtcInstant | None = None,
        end_timestamp: UtcInstant | None = None,
        parents: Iterable[ParentRef] = (),
        partitions: Iterable[PartitionEntry] = (),
        build_parameters: Mapping[str, Any] | None = None,
    ) -> DatasetManifest:
        parent_tuple = tuple(parents)
        partition_tuple = tuple(partitions)
        provisional = cls(
            dataset_code=dataset_code,
            dataset_type=dataset_type,
            version=version,
            feature_schema_version_id=feature_schema_version_id,
            outcome_schema_version_id=outcome_schema_version_id,
            point_in_time_cutoff=point_in_time_cutoff,
            universe=universe,
            start_timestamp=start_timestamp,
            end_timestamp=end_timestamp,
            parents=parent_tuple,
            storage_uri=storage_uri,
            partitions=partition_tuple,
            row_count=sum(partition.row_count for partition in partition_tuple),
            code_version=code_version,
            build_parameters=dict(build_parameters or {}),
            created_at=created_at,
            checksum="pending",
        )
        checksum = checksum_payload(provisional.identity_payload())
        return provisional.model_copy(update={"checksum": checksum})
