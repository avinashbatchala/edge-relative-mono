from __future__ import annotations

import json
from typing import Any

import pytest

from edge_relative_research.backtest.client import BacktestApiClient, BacktestApiError


def _transport(responses: dict[tuple[str, str], tuple[int, bytes]]):
    calls: list[tuple[str, str, bytes | None]] = []

    def transport(method: str, url: str, body: bytes | None) -> tuple[int, bytes]:
        calls.append((method, url, body))
        if (method, url) not in responses:
            raise AssertionError(f"unexpected call {method} {url}")
        return responses[(method, url)]

    return transport, calls


def test_start_get_and_trades_parse_and_encode() -> None:
    transport, calls = _transport(
        {
            ("POST", "http://api/api/v1/backtests"): (200, json.dumps({"runKey": "k1"}).encode()),
            ("GET", "http://api/api/v1/backtests/k1"): (
                200,
                json.dumps({"status": "SUCCEEDED"}).encode(),
            ),
            ("GET", "http://api/api/v1/backtests/k1/trades?limit=500&offset=0&symbol=SBIN"): (
                200,
                json.dumps([{"symbol": "SBIN"}]).encode(),
            ),
        }
    )
    client = BacktestApiClient("http://api", transport=transport)

    assert client.start({"symbols": ["SBIN"]})["runKey"] == "k1"
    assert client.get("k1")["status"] == "SUCCEEDED"
    assert client.trades("k1", symbol="SBIN")[0]["symbol"] == "SBIN"
    assert calls[0][2] == json.dumps({"symbols": ["SBIN"]}).encode()


def test_non_2xx_raises_with_status_and_body() -> None:
    transport, _ = _transport(
        {("POST", "http://api/api/v1/backtests"): (422, b'{"errors":["bad"]}')}
    )
    client = BacktestApiClient("http://api", transport=transport)

    with pytest.raises(BacktestApiError) as error:
        client.start({"symbols": []})
    assert error.value.status == 422
    assert "bad" in error.value.body


def test_wait_polls_until_terminal() -> None:
    states = iter(["RUNNING", "RUNNING", "SUCCEEDED"])

    def transport(method: str, url: str, body: bytes | None) -> tuple[int, bytes]:
        return 200, json.dumps({"status": next(states)}).encode()

    client = BacktestApiClient("http://api", transport=transport)
    polls: list[str] = []
    run = client.wait("k1", poll_seconds=0, on_poll=lambda r: polls.append(str(r["status"])))

    assert run["status"] == "SUCCEEDED"
    assert polls == ["RUNNING", "RUNNING", "SUCCEEDED"]


def test_sweep_posts_base_and_configs() -> None:
    captured: dict[str, Any] = {}

    def transport(method: str, url: str, body: bytes | None) -> tuple[int, bytes]:
        captured["url"] = url
        captured["body"] = json.loads(body or b"{}")
        return 200, b'[{"runKey":"a"},{"runKey":"b"}]'

    client = BacktestApiClient("http://api", transport=transport)
    runs = client.sweep({"symbols": ["SBIN"]}, [{"x": 1}, {"x": 2}], max_configs=10)

    assert [run["runKey"] for run in runs] == ["a", "b"]
    assert captured["url"].endswith("/api/v1/backtests/sweep")
    assert captured["body"]["base"] == {"symbols": ["SBIN"]}
    assert captured["body"]["configs"] == [{"x": 1}, {"x": 2}]
    assert captured["body"]["maxConfigs"] == 10
