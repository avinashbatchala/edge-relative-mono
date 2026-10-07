from __future__ import annotations

from edge_relative_research.backtest.search import SearchResult, grid_configs, rank, run_search


class _FakeClient:
    def __init__(self, metrics: dict[str, dict]) -> None:
        self._metrics = metrics
        self.swept: list[list[dict]] = []

    def sweep(self, base: dict, configs: list[dict], max_configs: int | None = None) -> list[dict]:
        self.swept.append(list(configs))
        return [{"runKey": f"k{index}"} for index in range(len(configs))]

    def wait(self, run_key: str, poll_seconds: float = 0, timeout_seconds: float = 0) -> dict:
        return {"status": "SUCCEEDED", "metrics": self._metrics.get(run_key, {})}


def test_grid_configs_expands_cartesian_product_over_base() -> None:
    configs = grid_configs({"keep": "base", "b": 2}, {"b": [10, 20], "c": ["x", "y"]})

    assert len(configs) == 4
    assert {config["b"] for config in configs} == {10, 20}
    assert {config["c"] for config in configs} == {"x", "y"}
    assert all(config["keep"] == "base" for config in configs)


def test_grid_configs_without_grid_returns_base_copy() -> None:
    configs = grid_configs({"a": 1}, {})
    assert configs == [{"a": 1}]
    assert configs[0] is not None


def test_run_search_collects_metrics_in_order() -> None:
    client = _FakeClient({"k0": {"netPnl": 10}, "k1": {"netPnl": 5}})
    results = run_search(client, {"symbols": ["SBIN"]}, [{"x": 1}, {"x": 2}], poll_seconds=0)

    assert [result.run_key for result in results] == ["k0", "k1"]
    assert results[0].metrics["netPnl"] == 10
    assert client.swept == [[{"x": 1}, {"x": 2}]]


def test_rank_descending_puts_missing_metrics_last() -> None:
    results = [
        SearchResult({"p": 1}, "a", "SUCCEEDED", {"netPnl": 5}),
        SearchResult({"p": 2}, "b", "SUCCEEDED", {"netPnl": 9}),
        SearchResult({"p": 3}, "c", "FAILED", {}),
    ]
    assert [result.run_key for result in rank(results, "netPnl")] == ["b", "a", "c"]
