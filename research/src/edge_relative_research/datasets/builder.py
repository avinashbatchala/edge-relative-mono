"""Dataset builder lifecycle.

A build is a three-phase lifecycle: allocate a ``BUILDING`` catalog version, write
partitions and finally commit a manifest (or fail and clean up). A committed version is
never overwritten: the catalog row becomes immutable and the builder refuses to reuse an
existing version or storage directory.
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Protocol

from ..contracts.enums import DatasetType
from ..timeutil import ensure_utc
from ..version import code_version as default_code_version
from .catalog import DatasetCatalog, ParentVersionDetail
from .errors import DatasetAlreadyCommittedError, DatasetError, InvalidParentLineageError
from .manifest import DatasetManifest, ParentRef, PartitionEntry
from .storage import DatasetStorage


class Clock(Protocol):
    def now(self) -> datetime: ...


class SystemClock:
    """UTC wall clock; injected so builds can be made deterministic in tests."""

    def now(self) -> datetime:
        return datetime.now(timezone.utc)


@dataclass(frozen=True, slots=True)
class ParentSpec:
    dataset_code: str
    version: int


@dataclass(frozen=True, slots=True)
class DatasetBuildSpec:
    dataset_code: str
    dataset_type: DatasetType
    name: str
    description: str | None = None
    version: int | None = None
    feature_schema_version_id: int | None = None
    outcome_schema_version_id: int | None = None
    point_in_time_cutoff: datetime | None = None
    universe: str | None = None
    start_timestamp: datetime | None = None
    end_timestamp: datetime | None = None
    parents: tuple[ParentSpec, ...] = ()
    build_parameters: Mapping[str, Any] = field(default_factory=dict)


class DatasetBuilder:
    """Build one immutable dataset version into a storage backend and the catalog."""

    def __init__(
        self,
        spec: DatasetBuildSpec,
        storage: DatasetStorage,
        catalog: DatasetCatalog,
        *,
        clock: Clock | None = None,
        code_version: str | None = None,
    ) -> None:
        self._spec = spec
        self._storage = storage
        self._catalog = catalog
        self._clock = clock if clock is not None else SystemClock()
        self._code_version = code_version if code_version is not None else default_code_version()
        self._partitions: list[PartitionEntry] = []
        self._parents: tuple[ParentVersionDetail, ...] = ()
        self._manifest: DatasetManifest | None = None
        self._failed = False
        self._begin()

    @property
    def version(self) -> int:
        return self._allocated_version

    @property
    def manifest(self) -> DatasetManifest | None:
        return self._manifest

    def _begin(self) -> None:
        cutoff = _normalize(self._spec.point_in_time_cutoff)
        start = _normalize(self._spec.start_timestamp)
        end = _normalize(self._spec.end_timestamp)
        if start is not None and end is not None and end < start:
            raise ValueError("end_timestamp must not precede start_timestamp")
        if self._spec.version is not None and self._spec.version < 1:
            raise ValueError("version must be positive")

        dataset_id = self._catalog.ensure_dataset(
            code=self._spec.dataset_code,
            name=self._spec.name,
            dataset_type=self._spec.dataset_type,
            description=self._spec.description,
        )
        self._parents = tuple(self._validate_parents(cutoff))
        version = self._spec.version
        if version is None:
            version = self._catalog.next_version(dataset_id)
        if self._storage.version_exists(self._spec.dataset_code, version):
            raise DatasetAlreadyCommittedError(
                f"storage already contains {self._spec.dataset_code} v{version}; "
                "refusing to overwrite it"
            )
        allocated = self._catalog.allocate_version(
            dataset_id=dataset_id,
            explicit_version=version,
            feature_schema_version_id=self._spec.feature_schema_version_id,
            outcome_schema_version_id=self._spec.outcome_schema_version_id,
            point_in_time_cutoff=cutoff,
            universe=self._spec.universe,
            start_timestamp=start,
            end_timestamp=end,
            storage_uri=self._storage.uri(self._spec.dataset_code, version),
            code_version=self._code_version,
            build_parameters=dict(self._spec.build_parameters),
        )
        self._allocated_version = allocated.version
        self._dataset_version_id = allocated.dataset_version_id
        for parent in self._parents:
            self._catalog.add_parent(self._dataset_version_id, parent.dataset_version_id)

    def _validate_parents(self, cutoff: datetime | None) -> list[ParentVersionDetail]:
        details: list[ParentVersionDetail] = []
        for parent in self._spec.parents:
            detail = self._catalog.resolve_parent(parent.dataset_code, parent.version)
            if detail.status != "COMMITTED":
                raise InvalidParentLineageError(
                    f"parent {parent.dataset_code} v{parent.version} is {detail.status}, "
                    "not COMMITTED"
                )
            if (
                cutoff is not None
                and detail.point_in_time_cutoff is not None
                and detail.point_in_time_cutoff > cutoff
            ):
                raise InvalidParentLineageError(
                    f"parent {parent.dataset_code} v{parent.version} cutoff "
                    f"{detail.point_in_time_cutoff} is later than the child cutoff {cutoff}"
                )
            details.append(detail)
        return details

    def add_partition(
        self, partition_key: str, rows: Sequence[Mapping[str, Any]]
    ) -> PartitionEntry:
        self._ensure_open()
        entry = self._storage.write_partition(
            self._spec.dataset_code, self._allocated_version, partition_key, rows
        )
        self._partitions.append(entry)
        return entry

    def commit(self) -> DatasetManifest:
        if self._manifest is not None:
            return self._manifest
        self._ensure_open()
        parent_refs = tuple(
            ParentRef(
                dataset_code=parent.dataset_code,
                version=parent.version,
                dataset_version_key=parent.dataset_version_key,
                checksum=parent.checksum or "",
            )
            for parent in self._parents
        )
        created_at = self._clock.now()
        manifest = DatasetManifest.create(
            dataset_code=self._spec.dataset_code,
            dataset_type=self._spec.dataset_type,
            version=self._allocated_version,
            feature_schema_version_id=self._spec.feature_schema_version_id,
            outcome_schema_version_id=self._spec.outcome_schema_version_id,
            point_in_time_cutoff=_normalize(self._spec.point_in_time_cutoff),
            universe=self._spec.universe,
            start_timestamp=_normalize(self._spec.start_timestamp),
            end_timestamp=_normalize(self._spec.end_timestamp),
            parents=parent_refs,
            partitions=tuple(self._partitions),
            storage_uri=self._storage.uri(self._spec.dataset_code, self._allocated_version),
            code_version=self._code_version,
            build_parameters=dict(self._spec.build_parameters),
            created_at=created_at,
        )
        manifest_uri = self._storage.write_manifest(
            self._spec.dataset_code, self._allocated_version, manifest
        )
        self._catalog.commit_version(
            dataset_version_id=self._dataset_version_id,
            dataset_version_key=manifest.dataset_version_key,
            checksum=manifest.checksum,
            row_count=manifest.row_count,
            partition_manifest_uri=manifest_uri,
            committed_at=created_at,
        )
        self._manifest = manifest
        return manifest

    def fail(self, reason: str) -> None:
        if self._manifest is not None or self._failed:
            return
        self._storage.delete_version(self._spec.dataset_code, self._allocated_version)
        self._catalog.fail_version(self._dataset_version_id, {"reason": reason})
        self._failed = True

    def _ensure_open(self) -> None:
        if self._manifest is not None or self._failed:
            raise DatasetError("dataset build is already finalized")

    def __enter__(self) -> DatasetBuilder:
        return self

    def __exit__(self, exc_type: object, exc: object, traceback: object) -> bool:
        if self._manifest is not None or self._failed:
            return False
        if exc_type is not None:
            self.fail(f"{getattr(exc_type, '__name__', 'error')}: {exc}")
            return False
        try:
            self.commit()
        except Exception as error:  # noqa: BLE001 - recorded then re-raised
            self.fail(f"commit failed: {error}")
            raise
        return False


def _normalize(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    return ensure_utc(value)
