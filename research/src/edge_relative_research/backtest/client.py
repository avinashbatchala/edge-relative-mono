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


def urllib_transport(timeout: float = 60.0) -> Transport:
    """Build a stdlib transport with the given socket timeout.

    Long-running calls (for example the ML training export, which replays a whole window) need a
    larger timeout than the default interactive calls.
    """

    def transport(method: str, url: str, body: "bytes | None") -> "tuple[int, bytes]":
        request = urllib.request.Request(
            url,
            data=body,
            method=method,
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:  # noqa: S310 (fixed http(s) base)
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.read()

    return transport


_urllib_transport: Transport = urllib_transport(60.0)


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

    def create_binding(
        self,
        instrument_id: int,
        strategy_version_id: int,
        parameters: Mapping[str, Any],
        effective_from: str,
        lifecycle_state: str = "RESEARCH",
        effective_to: str | None = None,
        source: str | None = None,
    ) -> Mapping[str, Any]:
        """Promote a per-instrument parameter set as a new effective-dated binding (append-only)."""
        payload: dict[str, Any] = {
            "instrumentId": instrument_id,
            "strategyVersionId": strategy_version_id,
            "parameters": dict(parameters),
            "effectiveFrom": effective_from,
            "lifecycleState": lifecycle_state,
        }
        if effective_to is not None:
            payload["effectiveTo"] = effective_to
        if source is not None:
            payload["source"] = source
        return self._call("POST", "/api/v1/strategy-bindings", payload)

    def effective_binding(self, instrument_id: int, as_of: str) -> Mapping[str, Any] | None:
        try:
            return self._call(
                "GET", f"/api/v1/strategy-bindings/{instrument_id}/effective?asOf={as_of}"
            )
        except BacktestApiError as error:
            if error.status == 404:
                return None
            raise

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
