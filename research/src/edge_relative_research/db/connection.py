"""Connection management with a hard read-only boundary.

Production schemas are read through a session marked ``read_only``; the server rejects
any write even if a query is mistaken. Research-owned writes use a separate writable
session. Both use ``REPEATABLE READ`` so a multi-query research read sees one consistent
snapshot, which is required for reproducible point-in-time assembly.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

import psycopg
from psycopg.rows import dict_row

from .config import DatabaseConfig
from .instrumentation import NullObserver, QueryObserver
from .query import QueryExecutor


class ResearchDatabase:
    """Factory for read-only and research-write connections."""

    def __init__(
        self,
        config: DatabaseConfig,
        observer: QueryObserver | None = None,
    ) -> None:
        self._config = config
        self._observer = observer if observer is not None else NullObserver()

    @property
    def config(self) -> DatabaseConfig:
        return self._config

    @contextmanager
    def connect(self, *, read_only: bool) -> Iterator[psycopg.Connection]:
        connection = psycopg.connect(
            **self._config.connect_kwargs(),
            row_factory=dict_row,
        )
        try:
            connection.read_only = read_only
            connection.isolation_level = psycopg.IsolationLevel.REPEATABLE_READ
            yield connection
        except BaseException:
            connection.rollback()
            raise
        else:
            connection.rollback()
        finally:
            connection.close()

    @contextmanager
    def read_only(self) -> Iterator[psycopg.Connection]:
        with self.connect(read_only=True) as connection:
            yield connection

    @contextmanager
    def research_write(self) -> Iterator[psycopg.Connection]:
        with self.connect(read_only=False) as connection:
            yield connection

    def executor(self, connection: psycopg.Connection) -> QueryExecutor:
        return QueryExecutor(connection, observer=self._observer)
