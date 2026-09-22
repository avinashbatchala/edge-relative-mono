"""Lightweight query instrumentation.

Records duration and row count per query. This is deliberately in-process: no metrics
server, exporter or distributed tracing. Callers may pass a :class:`QueryObserver`;
the default discards observations.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True, slots=True)
class QueryStats:
    name: str
    sql: str
    row_count: int
    duration_seconds: float
    streamed: bool = False

    @property
    def rows_per_second(self) -> float:
        if self.duration_seconds <= 0:
            return 0.0
        return self.row_count / self.duration_seconds


class QueryObserver(Protocol):
    def record(self, stats: QueryStats) -> None: ...


class NullObserver:
    """Drop all observations."""

    def record(self, stats: QueryStats) -> None:  # noqa: ARG002
        return None


class CollectingObserver:
    """Keep observations in memory for tests and local diagnostics."""

    def __init__(self) -> None:
        self.stats: list[QueryStats] = []

    def record(self, stats: QueryStats) -> None:
        self.stats.append(stats)

    @property
    def total_duration_seconds(self) -> float:
        return sum(item.duration_seconds for item in self.stats)

    @property
    def total_rows(self) -> int:
        return sum(item.row_count for item in self.stats)

    def clear(self) -> None:
        self.stats.clear()
