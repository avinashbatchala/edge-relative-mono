"""HTTP client for the ML ops API.

Reuses the standard-library transport from the backtest client; adds the analysis-run queue, model
registration and per-instrument binding calls, plus the per-anchor training export. Read/execute only:
it never holds broker credentials.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Mapping, Sequence

from edge_relative_research.backtest.client import (
    BacktestApiError,
    _urllib_transport,
)

Transport = Callable[[str, str, "bytes | None"], "tuple[int, bytes]"]


@dataclass(frozen=True)
class MlApiClient:
    base_url: str
    transport: Transport = _urllib_transport

    def _call(self, method: str, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        import json

        body = None if payload is None else json.dumps(payload).encode("utf-8")
        url = self.base_url.rstrip("/") + path
        status, raw = self.transport(method, url, body)
        text = raw.decode("utf-8") if raw else ""
        if status < 200 or status >= 300:
            raise BacktestApiError(status, text)
        return json.loads(text) if text else None

    def enqueue(self, config: Mapping[str, Any], requested_by: str | None = None) -> Mapping[str, Any]:
        return self._call(
            "POST", "/api/v1/ml/analysis-runs", {"config": dict(config), "requestedBy": requested_by}
        )

    def claim(self, lease_owner: str, lease_seconds: int = 3600) -> Mapping[str, Any] | None:
        try:
            return self._call(
                "POST",
                "/api/v1/ml/analysis-runs/claim",
                {"leaseOwner": lease_owner, "leaseSeconds": lease_seconds},
            )
        except BacktestApiError as error:
            if error.status == 204:
                return None
            raise

    def progress(self, key: str, progress: Mapping[str, Any]) -> None:
        self._call("POST", f"/api/v1/ml/analysis-runs/{key}/progress", dict(progress))

    def complete(self, key: str, model_version_id: int, metrics: Mapping[str, Any]) -> None:
        self._call(
            "POST",
            f"/api/v1/ml/analysis-runs/{key}/complete",
            {"modelVersionId": model_version_id, "metrics": dict(metrics)},
        )

    def fail(self, key: str, error: str) -> None:
        self._call("POST", f"/api/v1/ml/analysis-runs/{key}/fail", {"error": error})

    def register_model(self, payload: Mapping[str, Any]) -> Mapping[str, Any]:
        return self._call("POST", "/api/v1/ml/models", dict(payload))

    def create_binding(
        self,
        instrument_id: int,
        model_version_id: int,
        effective_from: str,
        authority_level: str = "RANKER",
        lifecycle_state: str = "RESEARCH",
        effective_to: str | None = None,
        source: str | None = None,
    ) -> Mapping[str, Any]:
        payload: dict[str, Any] = {
            "instrumentId": instrument_id,
            "modelVersionId": model_version_id,
            "authorityLevel": authority_level,
            "effectiveFrom": effective_from,
            "lifecycleState": lifecycle_state,
        }
        if effective_to is not None:
            payload["effectiveTo"] = effective_to
        if source is not None:
            payload["source"] = source
        return self._call("POST", "/api/v1/ml/bindings", payload)

    def export_anchors(self, request: Mapping[str, Any]) -> Sequence[Mapping[str, Any]]:
        """Per-anchor training export from the backtest engine (features + realized outcome)."""
        return self._call("POST", "/api/v1/ml/export/anchors", dict(request))
