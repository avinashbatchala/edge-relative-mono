"""Local Parquet dataset storage abstraction.

The architecture keeps large analytical history in Parquet/object storage and the
catalog in PostgreSQL. This module implements the local/test backend: one Parquet file
per partition under a version directory, plus a deterministic ``manifest.json``. There is
no cloud vendor coupling. A future object-store backend implements the same surface.
"""

from __future__ import annotations

import shutil
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import Any, Protocol

import polars as pl

from .checksums import canonical_json, checksum_payload, sha256_hex
from .errors import ChecksumMismatchError, DatasetNotFoundError
from .manifest import DatasetManifest, PartitionEntry

MANIFEST_FILENAME = "manifest.json"
_EMPTY_COLUMN = "_empty"


class DatasetStorage(Protocol):
    """Storage backend surface used by the dataset builder."""

    def uri(self, dataset_code: str, version: int) -> str: ...

    def version_exists(self, dataset_code: str, version: int) -> bool: ...

    def write_partition(
        self,
        dataset_code: str,
        version: int,
        partition_key: str,
        rows: Sequence[Mapping[str, Any]],
    ) -> PartitionEntry: ...

    def read_partition(
        self, dataset_code: str, version: int, partition_key: str
    ) -> list[dict[str, Any]]: ...

    def write_manifest(self, dataset_code: str, version: int, manifest: DatasetManifest) -> str: ...

    def read_manifest(self, dataset_code: str, version: int) -> DatasetManifest: ...

    def delete_version(self, dataset_code: str, version: int) -> None: ...


class LocalDatasetStorage:
    """Filesystem-backed Parquet storage rooted at a local directory."""

    def __init__(self, root: str | Path) -> None:
        self._root = Path(root).resolve()

    @property
    def root(self) -> Path:
        return self._root

    def version_dir(self, dataset_code: str, version: int) -> Path:
        return self._root / dataset_code / f"v{version}"

    def version_exists(self, dataset_code: str, version: int) -> bool:
        return self.version_dir(dataset_code, version).exists()

    def uri(self, dataset_code: str, version: int) -> str:
        return self.version_dir(dataset_code, version).as_uri()

    def _relative(self, path: Path) -> str:
        return path.relative_to(self._root).as_posix()

    def write_partition(
        self,
        dataset_code: str,
        version: int,
        partition_key: str,
        rows: Sequence[Mapping[str, Any]],
    ) -> PartitionEntry:
        directory = self.version_dir(dataset_code, version)
        directory.mkdir(parents=True, exist_ok=True)
        partition_key = partition_key.strip()
        path = directory / f"{partition_key}.parquet"
        materialized = list(rows)
        frame = (
            pl.DataFrame(materialized)
            if materialized
            else pl.DataFrame({_EMPTY_COLUMN: pl.Series([], dtype=pl.Utf8)})
        )
        frame.write_parquet(path)
        raw = path.read_bytes()
        return PartitionEntry(
            partition_key=partition_key,
            path=self._relative(path),
            row_count=len(materialized),
            content_checksum=checksum_payload(materialized),
            file_checksum=sha256_hex(raw),
            byte_size=len(raw),
        )

    def read_partition(
        self, dataset_code: str, version: int, partition_key: str
    ) -> list[dict[str, Any]]:
        path = self.version_dir(dataset_code, version) / f"{partition_key}.parquet"
        if not path.is_file():
            raise DatasetNotFoundError(f"partition not found: {self._relative(path)}")
        return pl.read_parquet(path).to_dicts()

    def write_manifest(self, dataset_code: str, version: int, manifest: DatasetManifest) -> str:
        directory = self.version_dir(dataset_code, version)
        directory.mkdir(parents=True, exist_ok=True)
        path = directory / MANIFEST_FILENAME
        path.write_bytes(canonical_json(manifest.model_dump(mode="json")))
        return self._relative(path)

    def read_manifest(self, dataset_code: str, version: int) -> DatasetManifest:
        path = self.version_dir(dataset_code, version) / MANIFEST_FILENAME
        if not path.is_file():
            raise DatasetNotFoundError(f"manifest not found: {self._relative(path)}")
        return DatasetManifest.model_validate_json(path.read_bytes())

    def delete_version(self, dataset_code: str, version: int) -> None:
        shutil.rmtree(self.version_dir(dataset_code, version), ignore_errors=True)

    def verify(self, manifest: DatasetManifest) -> None:
        """Recompute the manifest and file checksums; raise on any mismatch."""

        recomputed = checksum_payload(manifest.identity_payload())
        if recomputed != manifest.checksum:
            raise ChecksumMismatchError(
                f"manifest checksum mismatch for {manifest.dataset_code} "
                f"v{manifest.version}: expected {manifest.checksum}, got {recomputed}"
            )
        for partition in manifest.partitions:
            path = self._root / partition.path
            if not path.is_file():
                raise ChecksumMismatchError(f"partition file missing: {partition.path}")
            actual = sha256_hex(path.read_bytes())
            if actual != partition.file_checksum:
                raise ChecksumMismatchError(
                    f"partition {partition.partition_key} file checksum mismatch: "
                    f"expected {partition.file_checksum}, got {actual}"
                )
