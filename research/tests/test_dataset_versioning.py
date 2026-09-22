"""Integration tests for the dataset builder lifecycle and catalog semantics."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

import psycopg
import pytest

from edge_relative_research.contracts.enums import DatasetType
from edge_relative_research.datasets import (
    DatasetAlreadyCommittedError,
    DatasetBuilder,
    DatasetBuildSpec,
    DatasetManifest,
    InvalidParentLineageError,
    LocalDatasetStorage,
    ParentSpec,
)

UTC = timezone.utc
CUTOFF = datetime(2026, 9, 1, 10, 0, tzinfo=UTC)
LATER = datetime(2026, 9, 2, 10, 0, tzinfo=UTC)


def _spec(code: str, **overrides: Any) -> DatasetBuildSpec:
    fields: dict[str, Any] = {
        "dataset_code": code,
        "dataset_type": DatasetType.FEATURE,
        "name": code,
    }
    fields.update(overrides)
    return DatasetBuildSpec(**fields)


def _build(
    storage: LocalDatasetStorage,
    catalog: Any,
    code: str,
    *,
    rows: list[dict[str, Any]] | None = None,
    partition_key: str = "part-0",
    **overrides: Any,
) -> DatasetManifest:
    builder = DatasetBuilder(_spec(code, **overrides), storage, catalog)
    builder.add_partition(
        partition_key, rows if rows is not None else [{"instrument_id": 1, "value": 1.0}]
    )
    return builder.commit()


def test_build_commits_manifest_and_catalog(
    dataset_storage: LocalDatasetStorage, catalog: Any
) -> None:
    manifest = _build(
        dataset_storage,
        catalog,
        "TEST_BUILD_BASIC",
        universe="WATCHLIST",
        point_in_time_cutoff=CUTOFF,
        start_timestamp=CUTOFF,
        end_timestamp=CUTOFF,
        build_parameters={"horizon_seconds": 300},
    )
    row = catalog.find_version("TEST_BUILD_BASIC", 1)
    assert row is not None
    assert row.status == "COMMITTED"
    assert row.checksum == manifest.checksum
    assert row.row_count == 1
    assert row.committed_at is not None
    assert row.partition_manifest_uri == "TEST_BUILD_BASIC/v1/manifest.json"
    assert row.universe == "WATCHLIST"
    assert row.build_parameters == {"horizon_seconds": 300}
    assert row.point_in_time_cutoff == CUTOFF
    assert row.start_timestamp == CUTOFF
    assert row.end_timestamp == CUTOFF
    dataset_storage.verify(manifest)
    assert dataset_storage.read_manifest("TEST_BUILD_BASIC", 1).checksum == manifest.checksum


def test_committed_dataset_cannot_be_overwritten(
    dataset_storage: LocalDatasetStorage, catalog: Any, research_connection: Any
) -> None:
    _build(dataset_storage, catalog, "TEST_OVERWRITE")
    with pytest.raises(DatasetAlreadyCommittedError):
        DatasetBuilder(_spec("TEST_OVERWRITE", version=1), dataset_storage, catalog)

    with pytest.raises(psycopg.errors.RaiseException):
        with research_connection.cursor() as cursor:
            cursor.execute(
                "UPDATE research.dataset_version SET checksum = 'tampered' "
                "WHERE dataset_id = (SELECT dataset_id FROM research.dataset WHERE code = %s) "
                "AND version = 1",
                ("TEST_OVERWRITE",),
            )
        research_connection.commit()


def test_failed_build_is_not_committed(dataset_storage: LocalDatasetStorage, catalog: Any) -> None:
    builder = DatasetBuilder(_spec("TEST_FAILED"), dataset_storage, catalog)
    builder.add_partition("part-0", [{"instrument_id": 1}])
    builder.fail("synthetic failure")
    row = catalog.find_version("TEST_FAILED", 1)
    assert row is not None
    assert row.status == "FAILED"
    assert row.committed_at is None
    assert row.checksum is None
    assert row.failure["reason"] == "synthetic failure"
    assert not dataset_storage.version_exists("TEST_FAILED", 1)


def test_context_manager_fails_on_error(dataset_storage: LocalDatasetStorage, catalog: Any) -> None:
    with pytest.raises(RuntimeError):
        with DatasetBuilder(_spec("TEST_CTX_FAIL"), dataset_storage, catalog) as builder:
            builder.add_partition("part-0", [{"instrument_id": 1}])
            raise RuntimeError("boom")
    row = catalog.find_version("TEST_CTX_FAIL", 1)
    assert row is not None and row.status == "FAILED"
    assert not dataset_storage.version_exists("TEST_CTX_FAIL", 1)


def test_future_parent_is_rejected(dataset_storage: LocalDatasetStorage, catalog: Any) -> None:
    _build(dataset_storage, catalog, "TEST_PARENT_FUTURE", point_in_time_cutoff=LATER)
    with pytest.raises(InvalidParentLineageError):
        DatasetBuilder(
            _spec(
                "TEST_CHILD_FUTURE",
                point_in_time_cutoff=CUTOFF,
                parents=(ParentSpec("TEST_PARENT_FUTURE", 1),),
            ),
            dataset_storage,
            catalog,
        )


def test_uncommitted_parent_is_rejected(dataset_storage: LocalDatasetStorage, catalog: Any) -> None:
    parent = DatasetBuilder(_spec("TEST_PARENT_OPEN"), dataset_storage, catalog)
    try:
        with pytest.raises(InvalidParentLineageError):
            DatasetBuilder(
                _spec("TEST_CHILD_OPEN", parents=(ParentSpec("TEST_PARENT_OPEN", 1),)),
                dataset_storage,
                catalog,
            )
    finally:
        parent.fail("cleanup")


def test_changed_parent_changes_derived_lineage(
    dataset_storage: LocalDatasetStorage, catalog: Any
) -> None:
    parent_one = _build(
        dataset_storage, catalog, "TEST_LIN_P", rows=[{"instrument_id": 1, "value": 1.0}]
    )
    parent_two = _build(
        dataset_storage, catalog, "TEST_LIN_P", rows=[{"instrument_id": 1, "value": 2.0}]
    )
    assert parent_one.checksum != parent_two.checksum

    child_one = _build(
        dataset_storage,
        catalog,
        "TEST_LIN_C",
        rows=[{"instrument_id": 1, "value": 10.0}],
        parents=(ParentSpec("TEST_LIN_P", 1),),
    )
    child_two = _build(
        dataset_storage,
        catalog,
        "TEST_LIN_C",
        rows=[{"instrument_id": 1, "value": 10.0}],
        parents=(ParentSpec("TEST_LIN_P", 2),),
    )
    assert child_one.parents[0].dataset_version_key == parent_one.dataset_version_key
    assert child_two.parents[0].dataset_version_key == parent_two.dataset_version_key
    assert child_one.checksum != child_two.checksum

    child_one_row = catalog.find_version("TEST_LIN_C", 1)
    child_two_row = catalog.find_version("TEST_LIN_C", 2)
    parents_one = catalog.parent_versions(child_one_row.dataset_version_id)
    parents_two = catalog.parent_versions(child_two_row.dataset_version_id)
    assert parents_one[0].dataset_version_key == parent_one.dataset_version_key
    assert parents_one[0].checksum == parent_one.checksum
    assert parents_two[0].dataset_version_key == parent_two.dataset_version_key


def test_committed_version_can_be_retired(
    dataset_storage: LocalDatasetStorage, catalog: Any
) -> None:
    _build(dataset_storage, catalog, "TEST_RETIRE")
    row = catalog.find_version("TEST_RETIRE", 1)
    catalog.retire_version(row.dataset_version_id)
    retired = catalog.find_version("TEST_RETIRE", 1)
    assert retired.status == "RETIRED"
    assert retired.retired_at is not None
    assert retired.checksum == row.checksum


def test_rebuild_of_new_version_keeps_prior_versions(
    dataset_storage: LocalDatasetStorage, catalog: Any
) -> None:
    first = _build(dataset_storage, catalog, "TEST_MULTI", rows=[{"value": 1}])
    second = _build(dataset_storage, catalog, "TEST_MULTI", rows=[{"value": 2}])
    assert first.version == 1
    assert second.version == 2
    assert catalog.find_version("TEST_MULTI", 1).status == "COMMITTED"
    assert catalog.find_version("TEST_MULTI", 2).status == "COMMITTED"
    assert dataset_storage.read_partition("TEST_MULTI", 1, "part-0")[0]["value"] == 1
    assert dataset_storage.read_partition("TEST_MULTI", 2, "part-0")[0]["value"] == 2
