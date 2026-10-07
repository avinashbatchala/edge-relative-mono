from __future__ import annotations

from datetime import date

from edge_relative_research.backtest.walkforward import Segment
from edge_relative_research.backtest.walkforward_search import (
    promotion_decision,
    run_walkforward_search,
)

TRAIN = Segment("TRAIN", date(2023, 1, 1), date(2023, 12, 31))
VALIDATION = Segment("VALIDATION", date(2024, 1, 1), date(2024, 12, 31))
OOS = Segment("OOS", date(2025, 1, 1), date(2025, 12, 31))


class _FakeClient:
    """Returns metrics keyed by (segment-start-date, config-index)."""

    def __init__(self, by_segment: dict[str, list[int]]) -> None:
        self._by_segment = by_segment
        self._segment = ""

    def sweep(self, base: dict, configs: list[dict], max_configs: int | None = None) -> list[dict]:
        self._segment = base["startDate"]
        return [{"runKey": f"{self._segment}|{index}"} for index in range(len(configs))]

    def wait(self, run_key: str, poll_seconds: float = 0, timeout_seconds: float = 0) -> dict:
        segment, index = run_key.split("|")
        return {"status": "SUCCEEDED", "metrics": {"netPnl": self._by_segment[segment][int(index)]}}


def _client() -> _FakeClient:
    return _FakeClient(
        {
            TRAIN.start.isoformat(): [1, 2],
            VALIDATION.start.isoformat(): [5, 9],
            OOS.start.isoformat(): [3, 7],
        }
    )


def test_selects_on_validation_and_reports_out_of_sample() -> None:
    configs = [{"p": 1}, {"p": 2}]
    result = run_walkforward_search(
        _client(), {"symbols": ["SBIN"]}, configs, TRAIN, VALIDATION, OOS, poll_seconds=0
    )

    assert result.best is not None
    assert result.best.config == {"p": 2}
    assert result.best.metrics["netPnl"] == 9
    assert result.best_out_of_sample is not None
    assert result.best_out_of_sample.config == {"p": 2}
    assert result.best_out_of_sample.metrics["netPnl"] == 7


def test_promotion_requires_oos_threshold() -> None:
    result = run_walkforward_search(
        _client(),
        {"symbols": ["SBIN"]},
        [{"p": 1}, {"p": 2}],
        TRAIN,
        VALIDATION,
        OOS,
        poll_seconds=0,
    )
    assert promotion_decision(result, "netPnl", min_out_of_sample=5) is True
    assert promotion_decision(result, "netPnl", min_out_of_sample=8) is False
