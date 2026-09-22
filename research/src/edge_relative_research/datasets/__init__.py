"""Versioned, reproducible dataset storage and catalog.

A dataset version is immutable once committed. The on-disk manifest carries the content
checksum and parent lineage, and the ``research.dataset_version`` row carries the same
identity so a later experiment can never run against an unidentified mutable folder of
Parquet files.
"""

from .builder import (
    Clock,
    DatasetBuilder,
    DatasetBuildSpec,
    ParentSpec,
    SystemClock,
)
from .catalog import (
    AllocatedVersion,
    DatasetCatalog,
    ParentVersionDetail,
)
from .checksums import canonical_json, checksum_payload, sha256_hex
from .errors import (
    ChecksumMismatchError,
    DatasetAlreadyCommittedError,
    DatasetBuildInProgressError,
    DatasetError,
    DatasetNotFoundError,
    InvalidParentLineageError,
)
from .manifest import DatasetManifest, ParentRef, PartitionEntry
from .storage import DatasetStorage, LocalDatasetStorage

__all__ = [
    "AllocatedVersion",
    "ChecksumMismatchError",
    "Clock",
    "DatasetAlreadyCommittedError",
    "DatasetBuildInProgressError",
    "DatasetBuildSpec",
    "DatasetBuilder",
    "DatasetCatalog",
    "DatasetError",
    "DatasetManifest",
    "DatasetNotFoundError",
    "DatasetStorage",
    "InvalidParentLineageError",
    "LocalDatasetStorage",
    "ParentRef",
    "ParentSpec",
    "ParentVersionDetail",
    "PartitionEntry",
    "SystemClock",
    "canonical_json",
    "checksum_payload",
    "sha256_hex",
]
