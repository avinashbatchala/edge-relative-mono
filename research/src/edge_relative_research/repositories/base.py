"""Shared repository plumbing."""

from __future__ import annotations

from collections.abc import Iterator

from ..db.query import Params, QueryExecutor
from .models import T, from_row


class RepositoryBase:
    """Wraps a :class:`QueryExecutor` and maps rows onto typed models."""

    def __init__(self, executor: QueryExecutor) -> None:
        self._executor = executor

    @property
    def executor(self) -> QueryExecutor:
        return self._executor

    def _many(self, model: type[T], name: str, sql: str, params: Params = None) -> list[T]:
        return [from_row(model, row) for row in self._executor.fetch_all(name, sql, params)]

    def _one(self, model: type[T], name: str, sql: str, params: Params = None) -> T | None:
        row = self._executor.fetch_one(name, sql, params)
        return None if row is None else from_row(model, row)

    def _stream(self, model: type[T], name: str, sql: str, params: Params = None) -> Iterator[T]:
        for row in self._executor.stream(name, sql, params):
            yield from_row(model, row)


def where_clause(conditions: list[str]) -> str:
    """Join pre-written, parameterized predicates; no value is ever concatenated."""

    if not conditions:
        return ""
    return " WHERE " + " AND ".join(conditions)


__all__ = ["RepositoryBase", "where_clause"]
