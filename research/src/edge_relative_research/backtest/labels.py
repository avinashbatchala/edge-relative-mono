"""Outcome labels for research modelling.

Extracts the realised outcome of completed trades (R multiple, fees, and the MFE/MAE excursions that
record how far the trade ran for and against the position) into a columnar frame. These are labels:
they are only ever used as model targets, never as feature inputs, so no future information can leak
into a decision.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import polars as pl

LABEL_FIELDS: tuple[str, ...] = (
    "symbol",
    "direction",
    "entryPattern",
    "entryAt",
    "exitReason",
    "realizedR",
    "mfeR",
    "maeR",
    "netPnl",
    "holdingSeconds",
)

_SCHEMA: dict[str, Any] = {
    "symbol": pl.String,
    "direction": pl.String,
    "entryPattern": pl.String,
    "entryAt": pl.String,
    "exitReason": pl.String,
    "realizedR": pl.Float64,
    "mfeR": pl.Float64,
    "maeR": pl.Float64,
    "netPnl": pl.Float64,
    "holdingSeconds": pl.Int64,
}


def _numeric(value: Any) -> float | None:
    return None if value is None else float(value)


def trade_labels(trades: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """One label row per trade; missing values stay null rather than being coerced to zero."""
    rows: list[dict[str, Any]] = []
    for trade in trades:
        rows.append(
            {
                "symbol": trade.get("symbol"),
                "direction": trade.get("direction"),
                "entryPattern": trade.get("entryPattern"),
                "entryAt": trade.get("entryAt"),
                "exitReason": trade.get("exitReason"),
                "realizedR": _numeric(trade.get("realizedR")),
                "mfeR": _numeric(trade.get("mfeR")),
                "maeR": _numeric(trade.get("maeR")),
                "netPnl": _numeric(trade.get("netPnl")),
                "holdingSeconds": trade.get("holdingSeconds"),
            }
        )
    return rows


def labels_frame(trades: Sequence[Mapping[str, Any]]) -> pl.DataFrame:
    """Columnar outcome frame with a stable schema (empty input yields an empty typed frame)."""
    rows = trade_labels(trades)
    if not rows:
        return pl.DataFrame(schema=_SCHEMA)
    return pl.DataFrame(rows, schema=_SCHEMA)
