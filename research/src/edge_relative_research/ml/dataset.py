"""Training-matrix assembly from backtest anchor exports.

The Java backtest exports one row per VALID anchor: the exact production feature vector plus, when the
opportunity was taken, its realized outcome (graded R). This module turns those rows into a columnar
frame and ranker-ready (features, label, group) tuples. Missing feature values stay null — never zero —
and an anchor without a realized outcome carries no label and is excluded from training.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Sequence

import polars as pl

ANCHOR_SCHEMA: dict[str, Any] = {
    "tradingDate": pl.String,
    "instrumentId": pl.Int64,
    "symbol": pl.String,
    "direction": pl.String,
    "realizedR": pl.Float64,
    "mfeR": pl.Float64,
    "maeR": pl.Float64,
    "netPnl": pl.Float64,
    "exitReason": pl.String,
}


@dataclass(frozen=True)
class TrainingMatrix:
    features: list[dict[str, float]]
    labels: list[float]
    groups: list[str]
    feature_names: list[str]


def anchor_rows(raw_rows: Sequence[Mapping[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for raw in raw_rows:
        label = raw.get("label") or {}
        realized = label.get("realizedR")
        rows.append(
            {
                "tradingDate": str(raw.get("tradingDate")),
                "instrumentId": int(raw.get("instrumentId")),
                "symbol": raw.get("symbol"),
                "direction": raw.get("direction"),
                "realizedR": None if realized is None else float(realized),
                "mfeR": label.get("mfeR"),
                "maeR": label.get("maeR"),
                "netPnl": label.get("netPnl"),
                "exitReason": label.get("exitReason"),
            }
        )
    return rows


def build_frame(raw_rows: Sequence[Mapping[str, Any]]) -> pl.DataFrame:
    """Flatten anchor rows (including their feature maps) into a typed frame."""
    flattened: list[dict[str, Any]] = []
    feature_names: list[str] = []
    seen: set[str] = set()
    for raw, base in zip(raw_rows, anchor_rows(raw_rows), strict=True):
        features = raw.get("features") or {}
        for name in features:
            if name not in seen:
                seen.add(name)
                feature_names.append(name)
        row = dict(base)
        row.update({f"f_{name}": features.get(name) for name in features})
        flattened.append(row)
    if not flattened:
        schema = dict(ANCHOR_SCHEMA)
        return pl.DataFrame(schema=schema)
    return pl.DataFrame(flattened, infer_schema_length=None)


def feature_names(frame: pl.DataFrame) -> list[str]:
    return sorted(column[2:] for column in frame.columns if column.startswith("f_"))


def labelled(frame: pl.DataFrame) -> pl.DataFrame:
    """Only anchors with a realized outcome can train a supervised ranker."""
    return frame.filter(pl.col("realizedR").is_not_null())


def to_matrix(
    frame: pl.DataFrame,
    feature_columns: Sequence[str],
    *,
    group_by: str = "tradingDate",
) -> TrainingMatrix:
    features: list[dict[str, float]] = []
    labels: list[float] = []
    groups: list[str] = []
    for row in frame.iter_rows(named=True):
        vector = {
            name: float(row[name])
            for name in feature_columns
            if row.get(row_name(name)) is not None
        }
        features.append(vector)
        labels.append(float(row["realizedR"]))
        groups.append(str(row[group_by]))
    return TrainingMatrix(
        features=features,
        labels=labels,
        groups=groups,
        feature_names=list(feature_columns),
    )


def row_name(feature: str) -> str:
    return feature if feature.startswith("f_") else f"f_{feature}"
