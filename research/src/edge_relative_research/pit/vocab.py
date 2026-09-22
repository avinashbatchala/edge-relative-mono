"""Leakage vocabulary.

Two families are named explicitly so a guard can reject them regardless of how a column
was produced: future-outcome/label columns, and full-session volume columns.
"""

from __future__ import annotations

from enum import StrEnum


class VolumeScope(StrEnum):
    """How a volume figure was aggregated relative to the anchor.

    ``SESSION_TOTAL`` is only valid once the session has closed; using it intraday mixes
    the future into the row.
    """

    INTERVAL = "INTERVAL"
    CUMULATIVE_TO_ANCHOR = "CUMULATIVE_TO_ANCHOR"
    SESSION_TOTAL = "SESSION_TOTAL"


OUTCOME_COLUMNS: frozenset[str] = frozenset(
    {
        "mfe",
        "mae",
        "mfe_r",
        "mae_r",
        "maximum_favourable_excursion",
        "maximum_adverse_excursion",
        "target_before_stop",
        "target_before_stop_hit",
        "target_hit",
        "stop_hit",
        "stop_before_target",
        "time_to_target",
        "time_to_target_seconds",
        "time_to_stop",
        "time_to_stop_seconds",
        "time_to_mfe",
        "time_to_mfe_seconds",
        "time_to_mae",
        "time_to_mae_seconds",
        "realized_r",
        "realized_pnl",
        "gross_pnl",
        "net_pnl",
        "forward_return",
        "horizon_return",
        "label_state",
        "outcome",
    }
)

OUTCOME_PREFIXES: tuple[str, ...] = ("outcome_", "label_", "forward_", "future_", "horizon_")
RETURN_PREFIX = "return_"

FULL_DAY_VOLUME_COLUMNS: frozenset[str] = frozenset(
    {
        "day_volume",
        "daily_volume",
        "full_day_volume",
        "session_volume",
        "session_total_volume",
        "total_day_volume",
        "cumulative_volume_final",
        "eod_volume",
    }
)


def is_outcome_column(name: str) -> bool:
    lowered = name.lower()
    if lowered in OUTCOME_COLUMNS:
        return True
    if lowered.startswith(RETURN_PREFIX):
        return True
    return any(lowered.startswith(prefix) for prefix in OUTCOME_PREFIXES)


def is_full_day_volume_column(name: str) -> bool:
    return name.lower() in FULL_DAY_VOLUME_COLUMNS


def find_outcome_columns(names: object) -> list[str]:
    return sorted({name for name in names if is_outcome_column(str(name))})  # type: ignore[union-attr]
