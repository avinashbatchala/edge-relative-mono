"""Minimal HTTP client for the Java backtest API.

Uses only the standard library so the research layer does not gain a dependency for orchestration.
The client is read/execute-only: it starts and reads backtests; it never writes research state and
has no path to broker credentials.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable, Mapping, Sequence

# (method, url, body_bytes) -> (status_code, response_bytes). Injectable for tests.
Transport = Callable[[str, str, "bytes | None"], "tuple[int, bytes]"]


class BacktestApiError(RuntimeError):
    """Raised when the API returns a non-2xx response."""

    def __init__(self, status: int, body: str) -> None:
        super().__init__(f"backtest API returned {status}: {body}")
        self.status = status
        self.body = body


def _urllib_transport(method: str, url: str, body: "bytes | None") -> "tuple[int, bytes]":
    request = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:  # noqa: S310 (fixed http(s) base)
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


@dataclass(frozen=True)
class BacktestApiClient:
    base_url: str
    transport: Transport = _urllib_transport

    def _call(self, method: str, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        url = self.base_url.rstrip("/") + path
        status, raw = self.transport(method, url, body)
        text = raw.decode("utf-8") if raw else ""
        if status < 200 or status >= 300:
            raise BacktestApiError(status, text)
        return json.loads(text) if text else None

    def start(self, request: Mapping[str, Any]) -> Mapping[str, Any]:
        return self._call("POST", "/api/v1/backtests", request)

    def get(self, run_key: str) -> Mapping[str, Any]:
        return self._call("GET", f"/api/v1/backtests/{run_key}")

    def trades(
        self,
        run_key: str,
        symbol: str | None = None,
        limit: int = 500,
        offset: int = 0,
    ) -> Sequence[Mapping[str, Any]]:
        query = f"?limit={limit}&offset={offset}"
        if symbol:
            query += f"&symbol={urllib.parse.quote(symbol)}"
        return self._call("GET", f"/api/v1/backtests/{run_key}/trades{query}")

    def sweep(
        self,
        base: Mapping[str, Any],
        configs: Sequence[Mapping[str, Any]],
        max_configs: int | None = None,
    ) -> Sequence[Mapping[str, Any]]:
        payload: dict[str, Any] = {"base": base, "configs": list(configs)}
        if max_configs is not None:
            payload["maxConfigs"] = max_configs
        return self._call("POST", "/api/v1/backtests/sweep", payload)

    def wait(
        self,
        run_key: str,
        poll_seconds: float = 2.0,
        timeout_seconds: float = 1800.0,
        on_poll: Callable[[Mapping[str, Any]], None] | None = None,
    ) -> Mapping[str, Any]:
        deadline = time.monotonic() + timeout_seconds
        while True:
            run = self.get(run_key)
            if on_poll is not None:
                on_poll(run)
            state = run.get("status")
            if state in ("SUCCEEDED", "FAILED", "CANCELLED"):
                return run
            if time.monotonic() >= deadline:
                raise TimeoutError(f"run {run_key} did not finish within {timeout_seconds}s")
            time.sleep(poll_seconds)
