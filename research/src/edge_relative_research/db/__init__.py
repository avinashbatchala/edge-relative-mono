"""Database access primitives for the research layer.

Python reads production schemas (``reference``, ``control``, ``market``,
``operational``) read-only and owns writes only to ``research``. Nothing in this
package grants authority over orders, fills, risk decisions, trade plans, positions or
deployment state.
"""

from .config import DatabaseConfig
from .connection import ResearchDatabase
from .instrumentation import (
    CollectingObserver,
    NullObserver,
    QueryObserver,
    QueryStats,
)
from .query import QueryExecutor

__all__ = [
    "CollectingObserver",
    "DatabaseConfig",
    "NullObserver",
    "QueryExecutor",
    "QueryObserver",
    "QueryStats",
    "ResearchDatabase",
]
