"""Grid generation, sweep execution and ranking for one-shot research searches."""

from __future__ import annotations

import itertools
from dataclasses import dataclass
from typing import Any, Callable, Mapping, Sequence

from edge_relative_research.backtest.client import BacktestApiClient


def grid_configs(
    base_params: Mapping[str, Any], grid: Mapping[str, Sequence[Any]]
) -> list[dict[str, Any]]:
    """Cartesian product of a parameter grid over a base strategy-parameter set."""
    keys = sorted(grid)
    if not keys:
        return [dict(base_params)]
    configs: list[dict[str, Any]] = []
    for combination in itertools.product(*(grid[key] for key in keys)):
        config = dict(base_params)
        config.update(dict(zip(keys, combination)))
        configs.append(config)
    return configs


@dataclass(frozen=True)
class SearchResult:
    config: Mapping[str, Any]
    run_key: str
    status: str
    metrics: Mapping[str, Any]


def run_search(
    client: BacktestApiClient,
    base_request: Mapping[str, Any],
    configs: Sequence[Mapping[str, Any]],
    poll_seconds: float = 2.0,
    timeout_seconds: float = 3600.0,
    on_result: Callable[[SearchResult], None] | None = None,
) -> list[SearchResult]:
    """Submit all configurations as one sweep and collect the finished metrics."""
    runs = client.sweep(base_request, configs)
    if len(runs) != len(configs):
        raise RuntimeError(f"sweep returned {len(runs)} runs for {len(configs)} configs")
    results: list[SearchResult] = []
    for config, run in zip(configs, runs):
        run_key = str(run["runKey"])
        finished = client.wait(run_key, poll_seconds=poll_seconds, timeout_seconds=timeout_seconds)
        result = SearchResult(
            config=dict(config),
            run_key=run_key,
            status=str(finished.get("status")),
            metrics=dict(finished.get("metrics") or {}),
        )
        results.append(result)
        if on_result is not None:
            on_result(result)
    return results


def rank(
    results: Sequence[SearchResult], metric: str = "netPnl", descending: bool = True
) -> list[SearchResult]:
    """Rank by a numeric metric; runs missing the metric sort last regardless of direction."""

    def value(result: SearchResult) -> float:
        raw = result.metrics.get(metric)
        return float("nan") if raw is None else float(raw)

    present = [result for result in results if result.metrics.get(metric) is not None]
    missing = [result for result in results if result.metrics.get(metric) is None]
    present.sort(key=value, reverse=descending)
    return present + missing
