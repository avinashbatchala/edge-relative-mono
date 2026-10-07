from __future__ import annotations

import polars as pl

from edge_relative_research.backtest.labels import LABEL_FIELDS, labels_frame, trade_labels


def _trade(**overrides: object) -> dict:
    base = {
        "symbol": "SBIN",
        "direction": "LONG",
        "entryPattern": "M5_3_8_CONFIRMATION",
        "entryAt": "2024-06-03T04:30:00Z",
        "exitReason": "TARGET",
        "realizedR": 2.0,
        "mfeR": 2.1,
        "maeR": -0.4,
        "netPnl": 1200.0,
        "holdingSeconds": 3600,
    }
    base.update(overrides)
    return base


def test_trade_labels_keep_nulls_and_types() -> None:
    rows = trade_labels([_trade(), _trade(realizedR=None, mfeR=None, maeR=None, exitReason=None)])
    assert len(rows) == 2
    assert rows[0]["realizedR"] == 2.0
    assert rows[1]["realizedR"] is None  # missing never coerced to zero
    assert set(rows[0]) == set(LABEL_FIELDS)


def test_labels_frame_has_stable_schema_and_values() -> None:
    frame = labels_frame([_trade()])
    assert frame.columns == list(LABEL_FIELDS)
    assert frame.schema["realizedR"] == pl.Float64
    assert frame.schema["holdingSeconds"] == pl.Int64
    assert frame.row(0, named=True)["symbol"] == "SBIN"


def test_labels_frame_empty_keeps_schema() -> None:
    frame = labels_frame([])
    assert frame.height == 0
    assert frame.columns == list(LABEL_FIELDS)
