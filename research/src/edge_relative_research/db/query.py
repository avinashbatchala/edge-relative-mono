"""Parameterized query execution with deterministic, streamable reads.

Every value is bound as a query parameter; no value is ever interpolated into SQL text.
Large result sets can be consumed incrementally through a server-side cursor so the
research layer never needs to materialise an entire table in memory.
"""

from __future__ import annotations

import time
from collections.abc import Iterator, Mapping, Sequence
from typing import Any
from uuid import uuid4

import psycopg
from psycopg.rows import dict_row

from .instrumentation import NullObserver, QueryObserver, QueryStats

Params = Sequence[Any] | Mapping[str, Any] | None


class QueryExecutor:
    """Run parameterized statements against one connection with instrumentation."""

    def __init__(
        self,
        connection: psycopg.Connection,
        observer: QueryObserver | None = None,
        batch_size: int = 1000,
    ) -> None:
        self._connection = connection
        self._observer = observer if observer is not None else NullObserver()
        self._batch_size = max(1, batch_size)

    @property
    def batch_size(self) -> int:
        return self._batch_size

    def fetch_all(self, name: str, sql: str, params: Params = None) -> list[dict[str, Any]]:
        start = time.perf_counter()
        with self._connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(sql, params)
            rows = cursor.fetchall()
        self._record(name, sql, len(rows), start, streamed=False)
        return rows

    def fetch_one(self, name: str, sql: str, params: Params = None) -> dict[str, Any] | None:
        start = time.perf_counter()
        with self._connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(sql, params)
            row = cursor.fetchone()
        self._record(name, sql, 0 if row is None else 1, start, streamed=False)
        return row

    def fetch_scalar(self, name: str, sql: str, params: Params = None) -> Any:
        row = self.fetch_one(name, sql, params)
        if row is None:
            return None
        return next(iter(row.values()))

    def stream(self, name: str, sql: str, params: Params = None) -> Iterator[dict[str, Any]]:
        start = time.perf_counter()
        row_count = 0
        cursor = self._connection.cursor(name=f"er_research_{uuid4().hex}", row_factory=dict_row)
        try:
            cursor.itersize = self._batch_size
            cursor.execute(sql, params)
            for row in cursor:
                row_count += 1
                yield row
        finally:
            cursor.close()
            self._record(name, sql, row_count, start, streamed=True)

    def _record(
        self,
        name: str,
        sql: str,
        row_count: int,
        start: float,
        *,
        streamed: bool,
    ) -> None:
        self._observer.record(
            QueryStats(
                name=name,
                sql=sql,
                row_count=row_count,
                duration_seconds=time.perf_counter() - start,
                streamed=streamed,
            )
        )
