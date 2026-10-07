"""Walk-forward per-stock search.

Splits a window into TRAIN / VALIDATION / OUT-OF-SAMPLE, sweeps the same grid on each, selects the
best configuration on validation, and reports how it performs out-of-sample. Selection is never made
on the OOS window, so a promoted configuration is only accepted if it holds outside the window used
to choose it. Nothing is promoted unless the caller explicitly requests it.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Mapping, Sequence

from edge_relative_research.backtest.client import BacktestApiClient
from edge_relative_research.backtest.search import SearchResult, rank, run_search
from edge_relative_research.backtest.walkforward import Segment


@dataclass(frozen=True)
class WalkForwardSearch:
    train: list[SearchResult]
    validation: list[SearchResult]
    out_of_sample: list[SearchResult]
    best: SearchResult | None
    best_out_of_sample: SearchResult | None


def _scoped(base_request: Mapping[str, Any], segment: Segment) -> dict[str, Any]:
    request = dict(base_request)
    request.update(segment.as_iso())
    return request


def run_walkforward_search(
    client: BacktestApiClient,
    base_request: Mapping[str, Any],
    configs: Sequence[Mapping[str, Any]],
    train: Segment,
    validation: Segment,
    out_of_sample: Segment,
    metric: str = "netPnl",
    poll_seconds: float = 2.0,
    timeout_seconds: float = 3600.0,
    on_result: Callable[[SearchResult], None] | None = None,
) -> WalkForwardSearch:
    def segment_run(segment: Segment) -> list[SearchResult]:
        return run_search(
            client,
            _scoped(base_request, segment),
            configs,
            poll_seconds=poll_seconds,
            timeout_seconds=timeout_seconds,
            on_result=on_result,
        )

    train_results = segment_run(train)
    validation_results = segment_run(validation)
    oos_results = segment_run(out_of_sample)

    ranked = rank(validation_results, metric)
    best = ranked[0] if ranked else None
    best_oos = None
    if best is not None:
        best_oos = next(
            (result for result in oos_results if dict(result.config) == dict(best.config)), None
        )
    return WalkForwardSearch(train_results, validation_results, oos_results, best, best_oos)


def promotion_decision(
    candidate: WalkForwardSearch, metric: str = "netPnl", min_out_of_sample: float | None = None
) -> bool:
    """Promote only when the validation winner also clears the OOS threshold on the same config."""
    if candidate.best is None or candidate.best_out_of_sample is None:
        return False
    value = candidate.best_out_of_sample.metrics.get(metric)
    if value is None:
        return False
    return min_out_of_sample is None or float(value) >= float(min_out_of_sample)
