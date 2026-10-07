"""Backtest orchestration for the research layer.

The canonical backtester is the Java application; this package drives it over HTTP (standard
library only) by sweeping point-in-time parameter configurations, and never owns or mutates
production trading state. It writes nothing to the database.
"""

from edge_relative_research.backtest.client import BacktestApiClient, BacktestApiError
from edge_relative_research.backtest.search import SearchResult, grid_configs, rank, run_search
from edge_relative_research.backtest.walkforward import (
    Segment,
    chronological_folds,
    train_validation_oos,
)

__all__ = [
    "BacktestApiClient",
    "BacktestApiError",
    "SearchResult",
    "Segment",
    "chronological_folds",
    "grid_configs",
    "rank",
    "run_search",
    "train_validation_oos",
]
