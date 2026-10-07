"""Backtest orchestration for the research layer.

The canonical backtester is the Java application; this package drives it over HTTP (standard
library only) by sweeping point-in-time parameter configurations, and never owns or mutates
production trading state. It writes nothing to the database.
"""

from edge_relative_research.backtest.client import BacktestApiClient, BacktestApiError
from edge_relative_research.backtest.labels import labels_frame, trade_labels
from edge_relative_research.backtest.search import SearchResult, grid_configs, rank, run_search
from edge_relative_research.backtest.walkforward import (
    Segment,
    chronological_folds,
    train_validation_oos,
)
from edge_relative_research.backtest.walkforward_search import (
    WalkForwardSearch,
    promotion_decision,
    run_walkforward_search,
)

__all__ = [
    "BacktestApiClient",
    "BacktestApiError",
    "SearchResult",
    "Segment",
    "WalkForwardSearch",
    "chronological_folds",
    "grid_configs",
    "labels_frame",
    "promotion_decision",
    "rank",
    "run_search",
    "run_walkforward_search",
    "trade_labels",
    "train_validation_oos",
]
