"""Catalog write adapter for ``research.dataset`` and ``research.dataset_version``.

Python owns writes only to the ``research`` schema. These statements must run on a
writable connection (``ResearchDatabase.research_write``); they use parameterized SQL and
rely on the database triggers to keep COMMITTED content immutable.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any
from uuid import UUID

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

from ..contracts.enums import DatasetType
from ..repositories.models import DatasetVersionRow, from_row
from .errors import (
    DatasetAlreadyCommittedError,
    DatasetBuildInProgressError,
    DatasetNotFoundError,
)

_SELECT_VERSION = (
    "SELECT dv.dataset_version_id, dv.dataset_version_key, dv.dataset_id, "
    "d.code AS dataset_code, dv.version, dv.status, dv.feature_schema_version_id, "
    "dv.outcome_schema_version_id, dv.point_in_time_cutoff, dv.universe, dv.start_timestamp, "
    "dv.end_timestamp, dv.storage_uri, dv.partition_manifest_uri, dv.row_count, dv.checksum, "
    "dv.code_version, dv.build_parameters, dv.failure, dv.committed_at, dv.retired_at, "
    "dv.created_at "
    "FROM research.dataset_version dv JOIN research.dataset d ON d.dataset_id = dv.dataset_id "
)


@dataclass(frozen=True, slots=True)
class ParentVersionDetail:
    dataset_version_id: int
    dataset_version_key: UUID
    dataset_code: str
    version: int
    status: str
    checksum: str | None
    point_in_time_cutoff: datetime | None


@dataclass(frozen=True, slots=True)
class AllocatedVersion:
    version: int
    dataset_version_id: int
    dataset_version_key: UUID


class DatasetCatalog:
    """Read/write access to the dataset catalog."""

    def __init__(self, connection: psycopg.Connection) -> None:
        self._connection = connection

    def ensure_dataset(
        self,
        *,
        code: str,
        name: str,
        dataset_type: DatasetType,
        description: str | None = None,
    ) -> int:
        with self._connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                "INSERT INTO research.dataset "
                "(dataset_key, code, name, dataset_type, description) "
                "VALUES (gen_random_uuid(), %s, %s, %s, %s) ON CONFLICT (code) DO NOTHING",
                (code, name, dataset_type.value, description),
            )
            cursor.execute("SELECT dataset_id FROM research.dataset WHERE code = %s", (code,))
            row = cursor.fetchone()
        self._connection.commit()
        return int(row["dataset_id"])

    def find_version(self, dataset_code: str, version: int) -> DatasetVersionRow | None:
        row = self._fetchone(
            _SELECT_VERSION + "WHERE d.code = %s AND dv.version = %s",
            (dataset_code, version),
        )
        return None if row is None else from_row(DatasetVersionRow, row)

    def next_version(self, dataset_id: int) -> int:
        row = self._fetchone(
            "SELECT COALESCE(MAX(version), 0) + 1 AS next_version "
            "FROM research.dataset_version WHERE dataset_id = %s",
            (dataset_id,),
        )
        return int(row["next_version"])

    def allocate_version(
        self,
        *,
        dataset_id: int,
        explicit_version: int | None,
        feature_schema_version_id: int | None,
        outcome_schema_version_id: int | None,
        point_in_time_cutoff: datetime | None,
        universe: str | None,
        start_timestamp: datetime | None,
        end_timestamp: datetime | None,
        storage_uri: str,
        code_version: str,
        build_parameters: dict[str, Any],
    ) -> AllocatedVersion:
        with self._connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (str(dataset_id),))
            if explicit_version is not None:
                version = explicit_version
                cursor.execute(
                    "SELECT status FROM research.dataset_version "
                    "WHERE dataset_id = %s AND version = %s",
                    (dataset_id, version),
                )
                existing = cursor.fetchone()
                if existing is not None:
                    if existing["status"] in ("COMMITTED", "RETIRED"):
                        raise DatasetAlreadyCommittedError(
                            f"dataset {dataset_id} version {version} is already "
                            f"{existing['status']} and cannot be rebuilt"
                        )
                    raise DatasetBuildInProgressError(
                        f"dataset {dataset_id} version {version} is already {existing['status']}"
                    )
            else:
                cursor.execute(
                    "SELECT COALESCE(MAX(version), 0) + 1 AS next_version "
                    "FROM research.dataset_version WHERE dataset_id = %s",
                    (dataset_id,),
                )
                version = int(cursor.fetchone()["next_version"])
            cursor.execute(
                "INSERT INTO research.dataset_version "
                "(dataset_version_key, dataset_id, version, status, feature_schema_version_id, "
                "outcome_schema_version_id, point_in_time_cutoff, universe, start_timestamp, "
                "end_timestamp, storage_uri, code_version, build_parameters) "
                "VALUES (gen_random_uuid(), %s, %s, 'BUILDING', %s, %s, %s, %s, %s, %s, %s, "
                "%s, %s) "
                "RETURNING dataset_version_id, dataset_version_key",
                (
                    dataset_id,
                    version,
                    feature_schema_version_id,
                    outcome_schema_version_id,
                    point_in_time_cutoff,
                    universe,
                    start_timestamp,
                    end_timestamp,
                    storage_uri,
                    code_version,
                    Jsonb(build_parameters),
                ),
            )
            row = cursor.fetchone()
        self._connection.commit()
        return AllocatedVersion(
            version=version,
            dataset_version_id=int(row["dataset_version_id"]),
            dataset_version_key=row["dataset_version_key"],
        )

    def add_parent(
        self,
        dataset_version_id: int,
        input_dataset_version_id: int,
        *,
        input_role: str = "INPUT",
    ) -> None:
        with self._connection.cursor() as cursor:
            cursor.execute(
                "INSERT INTO research.dataset_version_input "
                "(dataset_version_id, input_dataset_version_id, input_role) "
                "VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
                (dataset_version_id, input_dataset_version_id, input_role),
            )
        self._connection.commit()

    def resolve_parent(self, dataset_code: str, version: int) -> ParentVersionDetail:
        rows = self.parent_versions(
            dataset_version_id=None, dataset_code=dataset_code, version=version
        )
        if not rows:
            raise DatasetNotFoundError(f"parent dataset {dataset_code} v{version} not found")
        return rows[0]

    def parent_versions(
        self,
        dataset_version_id: int | None,
        *,
        dataset_code: str | None = None,
        version: int | None = None,
    ) -> list[ParentVersionDetail]:
        if dataset_version_id is not None:
            sql = (
                "SELECT pv.dataset_version_id, pv.dataset_version_key, d.code AS dataset_code, "
                "pv.version, pv.status, pv.checksum, pv.point_in_time_cutoff "
                "FROM research.dataset_version_input dvi "
                "JOIN research.dataset_version pv "
                "ON pv.dataset_version_id = dvi.input_dataset_version_id "
                "JOIN research.dataset d ON d.dataset_id = pv.dataset_id "
                "WHERE dvi.dataset_version_id = %s ORDER BY d.code, pv.version"
            )
            params: list[Any] = [dataset_version_id]
        else:
            sql = (
                "SELECT pv.dataset_version_id, pv.dataset_version_key, d.code AS dataset_code, "
                "pv.version, pv.status, pv.checksum, pv.point_in_time_cutoff "
                "FROM research.dataset_version pv "
                "JOIN research.dataset d ON d.dataset_id = pv.dataset_id "
                "WHERE d.code = %s AND pv.version = %s"
            )
            params = [dataset_code, version]
        with self._connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(sql, params)
            rows = cursor.fetchall()
        return [
            ParentVersionDetail(
                dataset_version_id=int(row["dataset_version_id"]),
                dataset_version_key=row["dataset_version_key"],
                dataset_code=row["dataset_code"],
                version=int(row["version"]),
                status=row["status"],
                checksum=row["checksum"],
                point_in_time_cutoff=row["point_in_time_cutoff"],
            )
            for row in rows
        ]

    def commit_version(
        self,
        *,
        dataset_version_id: int,
        dataset_version_key: UUID,
        checksum: str,
        row_count: int,
        partition_manifest_uri: str,
        committed_at: datetime,
    ) -> None:
        with self._connection.cursor() as cursor:
            cursor.execute(
                "UPDATE research.dataset_version SET status = 'COMMITTED', "
                "dataset_version_key = %s, checksum = %s, row_count = %s, "
                "partition_manifest_uri = %s, committed_at = %s "
                "WHERE dataset_version_id = %s",
                (
                    dataset_version_key,
                    checksum,
                    row_count,
                    partition_manifest_uri,
                    committed_at,
                    dataset_version_id,
                ),
            )
        self._connection.commit()

    def fail_version(self, dataset_version_id: int, failure: dict[str, Any]) -> None:
        with self._connection.cursor() as cursor:
            cursor.execute(
                "UPDATE research.dataset_version SET status = 'FAILED', failure = %s "
                "WHERE dataset_version_id = %s AND status = 'BUILDING'",
                (Jsonb(failure), dataset_version_id),
            )
        self._connection.commit()

    def retire_version(self, dataset_version_id: int) -> None:
        with self._connection.cursor() as cursor:
            cursor.execute(
                "UPDATE research.dataset_version SET status = 'RETIRED', retired_at = now() "
                "WHERE dataset_version_id = %s AND status = 'COMMITTED'",
                (dataset_version_id,),
            )
        self._connection.commit()

    def _fetchone(self, sql: str, params: tuple[Any, ...]) -> dict[str, Any] | None:
        with self._connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(sql, params)
            return cursor.fetchone()
