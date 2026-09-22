"""Unit tests for deterministic manifests, checksums and local Parquet storage."""

from __future__ import annotations

import json
from datetime import datetime, timezone

import pytest

from edge_relative_research.contracts.enums import DatasetType
from edge_relative_research.datasets import (
    ChecksumMismatchError,
    DatasetManifest,
    LocalDatasetStorage,
    PartitionEntry,
    canonical_json,
    checksum_payload,
)
from edge_relative_research.datasets.errors import DatasetNotFoundError

UTC = timezone.utc
CREATED = datetime(2026, 9, 2, tzinfo=UTC)
CUTOFF = datetime(2026, 9, 1, 10, 0, tzinfo=UTC)


def _partition(
    key: str = "part-0", content: str = "sha256:content", byte_checksum: str = "sha256:file"
) -> PartitionEntry:
    return PartitionEntry(
        partition_key=key,
        path=f"FEATURES_A/v1/{key}.parquet",
        row_count=2,
        content_checksum=content,
        file_checksum=byte_checksum,
        byte_size=128,
    )


def _manifest(
    *,
    storage_uri: str = "file:///tmp/datasets/FEATURES_A/v1",
    created_at: datetime = CREATED,
    partitions: tuple[PartitionEntry, ...] | None = None,
    build_parameters: dict | None = None,
    code: str = "FEATURES_A",
    version: int = 1,
) -> DatasetManifest:
    return DatasetManifest.create(
        dataset_code=code,
        dataset_type=DatasetType.FEATURE,
        version=version,
        feature_schema_version_id=1,
        outcome_schema_version_id=None,
        point_in_time_cutoff=CUTOFF,
        universe="WATCHLIST",
        start_timestamp=CUTOFF,
        end_timestamp=CUTOFF,
        storage_uri=storage_uri,
        code_version="code-v1",
        created_at=created_at,
        partitions=partitions if partitions is not None else (_partition(),),
        build_parameters={"horizon_seconds": 300} if build_parameters is None else build_parameters,
    )


def test_manifest_checksum_and_key_are_deterministic() -> None:
    first = _manifest()
    second = _manifest()
    assert first.checksum == second.checksum
    assert first.dataset_version_key == second.dataset_version_key
    assert first.identity_payload() == second.identity_payload()


def test_created_at_and_storage_uri_do_not_affect_checksum() -> None:
    base = _manifest()
    later = _manifest(created_at=datetime(2026, 9, 3, tzinfo=UTC))
    moved = _manifest(storage_uri="s3://somewhere/else")
    assert base.checksum == later.checksum == moved.checksum


def test_partition_content_changes_checksum() -> None:
    base = _manifest()
    changed = _manifest(partitions=(_partition(content="sha256:different"),))
    assert base.checksum != changed.checksum


def test_build_parameters_change_checksum() -> None:
    base = _manifest()
    changed = _manifest(build_parameters={"horizon_seconds": 600})
    assert base.checksum != changed.checksum


def test_manifest_identity_ignores_file_byte_checksum() -> None:
    base = _manifest()
    reencoded = _manifest(partitions=(_partition(byte_checksum="sha256:other-bytes"),))
    assert base.checksum == reencoded.checksum


def test_canonical_json_is_order_independent() -> None:
    assert canonical_json({"b": 1, "a": 2}) == canonical_json({"a": 2, "b": 1})
    assert checksum_payload({"x": [1, 2]}) == checksum_payload({"x": [1, 2]})


def test_local_storage_round_trip(tmp_path) -> None:
    storage = LocalDatasetStorage(tmp_path)
    rows = [{"instrument_id": 1, "value": 1.5}, {"instrument_id": 2, "value": 2.5}]
    entry = storage.write_partition("FEATURES_A", 1, "part-0", rows)
    assert entry.row_count == 2
    assert entry.byte_size > 0
    assert entry.path == "FEATURES_A/v1/part-0.parquet"

    manifest = DatasetManifest.create(
        dataset_code="FEATURES_A",
        dataset_type=DatasetType.FEATURE,
        version=1,
        storage_uri=storage.uri("FEATURES_A", 1),
        code_version="code-v1",
        created_at=CREATED,
        partitions=(entry,),
    )
    manifest_uri = storage.write_manifest("FEATURES_A", 1, manifest)
    assert manifest_uri == "FEATURES_A/v1/manifest.json"
    storage.verify(manifest)
    assert storage.read_partition("FEATURES_A", 1, "part-0") == rows
    reloaded = storage.read_manifest("FEATURES_A", 1)
    assert reloaded.checksum == manifest.checksum
    assert reloaded.dataset_version_key == manifest.dataset_version_key


def test_empty_partition_round_trips(tmp_path) -> None:
    storage = LocalDatasetStorage(tmp_path)
    entry = storage.write_partition("FEATURES_A", 1, "empty", [])
    assert entry.row_count == 0
    assert storage.read_partition("FEATURES_A", 1, "empty") == []


def test_corrupted_file_checksum_is_detected(tmp_path) -> None:
    storage = LocalDatasetStorage(tmp_path)
    entry = storage.write_partition("FEATURES_A", 1, "part-0", [{"instrument_id": 1}])
    manifest = DatasetManifest.create(
        dataset_code="FEATURES_A",
        dataset_type=DatasetType.FEATURE,
        version=1,
        storage_uri=storage.uri("FEATURES_A", 1),
        code_version="code-v1",
        created_at=CREATED,
        partitions=(entry,),
    )
    storage.verify(manifest)
    path = storage.version_dir("FEATURES_A", 1) / "part-0.parquet"
    path.write_bytes(path.read_bytes() + b"corruption")
    with pytest.raises(ChecksumMismatchError):
        storage.verify(manifest)


def test_tampered_manifest_is_detected(tmp_path) -> None:
    storage = LocalDatasetStorage(tmp_path)
    entry = storage.write_partition("FEATURES_A", 1, "part-0", [{"instrument_id": 1}])
    manifest = DatasetManifest.create(
        dataset_code="FEATURES_A",
        dataset_type=DatasetType.FEATURE,
        version=1,
        storage_uri=storage.uri("FEATURES_A", 1),
        code_version="code-v1",
        created_at=CREATED,
        partitions=(entry,),
    )
    storage.write_manifest("FEATURES_A", 1, manifest)
    manifest_path = storage.version_dir("FEATURES_A", 1) / "manifest.json"
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    payload["row_count"] = 999
    manifest_path.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(ChecksumMismatchError):
        storage.verify(storage.read_manifest("FEATURES_A", 1))


def test_missing_partition_is_detected(tmp_path) -> None:
    storage = LocalDatasetStorage(tmp_path)
    entry = storage.write_partition("FEATURES_A", 1, "part-0", [{"instrument_id": 1}])
    manifest = DatasetManifest.create(
        dataset_code="FEATURES_A",
        dataset_type=DatasetType.FEATURE,
        version=1,
        storage_uri=storage.uri("FEATURES_A", 1),
        code_version="code-v1",
        created_at=CREATED,
        partitions=(entry,),
    )
    (storage.version_dir("FEATURES_A", 1) / "part-0.parquet").unlink()
    with pytest.raises(ChecksumMismatchError):
        storage.verify(manifest)
    with pytest.raises(DatasetNotFoundError):
        storage.read_partition("FEATURES_A", 1, "part-0")
