"""Leakage scanner for research datasets.

The scanner inspects a dataset's schema and timestamps and returns explicit findings.
``assert_clean`` turns any finding into a loud :class:`LeakageDetectedError` so a leaking
dataset cannot be silently used.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any

from .access import get_optional
from .errors import LeakageDetectedError
from .guards import ensure_as_of
from .vocab import (
    VolumeScope,
    find_outcome_columns,
    is_full_day_volume_column,
)

OUTCOME_COLUMN = "OUTCOME_COLUMN"
FUTURE_ROW = "FUTURE_ROW"
MISSING_ANCHOR = "MISSING_ANCHOR"
FUTURE_TIMESTAMP = "FUTURE_TIMESTAMP"
INCOMPLETE_BAR = "INCOMPLETE_BAR"
FULL_DAY_VOLUME = "FULL_DAY_VOLUME"
FUTURE_PIVOT = "FUTURE_PIVOT"
NORMALIZATION_FIT = "NORMALIZATION_FIT"


@dataclass(frozen=True, slots=True)
class LeakageFinding:
    code: str
    message: str
    row_index: int | None = None
    column: str | None = None


@dataclass(frozen=True, slots=True)
class LeakageScanResult:
    findings: tuple[LeakageFinding, ...]

    @property
    def ok(self) -> bool:
        return not self.findings

    @property
    def codes(self) -> list[str]:
        return sorted({finding.code for finding in self.findings})

    def assert_clean(self) -> None:
        if self.findings:
            details = "; ".join(f"{finding.code}: {finding.message}" for finding in self.findings)
            raise LeakageDetectedError(
                f"dataset failed leakage scan with {len(self.findings)} finding(s): {details}"
            )


class LeakageScanner:
    """Inspect a table of rows for schema- and timestamp-level leakage."""

    def __init__(self, *, cutoff: datetime | None = None) -> None:
        self._cutoff = ensure_as_of(cutoff) if cutoff is not None else None

    @property
    def cutoff(self) -> datetime | None:
        return self._cutoff

    def scan(
        self,
        rows: list[dict[str, Any]],
        *,
        anchor_column: str = "anchor_timestamp",
        temporal_columns: tuple[str, ...] = (),
        session_close: datetime | None = None,
        volume_scope_column: str | None = None,
        complete_columns: tuple[str, ...] = ("complete", "is_complete"),
        pivot_column: str = "confirmed_at",
        normalization_fit_column: str | None = None,
    ) -> LeakageScanResult:
        findings: list[LeakageFinding] = []
        columns: set[str] = set()
        for row in rows:
            columns.update(str(key) for key in row)

        for column in find_outcome_columns(columns):
            findings.append(
                LeakageFinding(
                    OUTCOME_COLUMN,
                    f"future-outcome column {column!r} present among feature inputs",
                    column=column,
                )
            )

        for index, row in enumerate(rows):
            anchor_value = get_optional(row, anchor_column)
            anchor = self._as_datetime(anchor_value)
            if self._cutoff is not None:
                if anchor is None:
                    findings.append(
                        LeakageFinding(
                            MISSING_ANCHOR,
                            f"row has no {anchor_column} to validate against the cutoff",
                            row_index=index,
                        )
                    )
                elif anchor > self._cutoff:
                    findings.append(
                        LeakageFinding(
                            FUTURE_ROW,
                            f"{anchor_column} {anchor.isoformat()} is after cutoff "
                            f"{self._cutoff.isoformat()}",
                            row_index=index,
                            column=anchor_column,
                        )
                    )

            if anchor is not None:
                for column in temporal_columns:
                    value = self._as_datetime(get_optional(row, column))
                    if value is not None and value > anchor:
                        findings.append(
                            LeakageFinding(
                                FUTURE_TIMESTAMP,
                                f"{column} {value.isoformat()} is after anchor "
                                f"{anchor.isoformat()}",
                                row_index=index,
                                column=column,
                            )
                        )

            for column in complete_columns:
                if get_optional(row, column) is False:
                    findings.append(
                        LeakageFinding(
                            INCOMPLETE_BAR,
                            f"{column} is False at index {index}",
                            row_index=index,
                            column=column,
                        )
                    )

            if anchor is not None and session_close is not None and anchor < session_close:
                for column in sorted(columns):
                    if is_full_day_volume_column(column):
                        findings.append(
                            LeakageFinding(
                                FULL_DAY_VOLUME,
                                f"full-session volume column {column!r} present intraday",
                                row_index=index,
                                column=column,
                            )
                        )
                if volume_scope_column is not None:
                    scope = get_optional(row, volume_scope_column)
                    if (
                        scope == VolumeScope.SESSION_TOTAL
                        or scope == VolumeScope.SESSION_TOTAL.value
                    ):
                        findings.append(
                            LeakageFinding(
                                FULL_DAY_VOLUME,
                                f"{volume_scope_column}=SESSION_TOTAL before session close",
                                row_index=index,
                                column=volume_scope_column,
                            )
                        )

            if anchor is not None:
                pivot = self._as_datetime(get_optional(row, pivot_column))
                if pivot is not None and pivot > anchor:
                    findings.append(
                        LeakageFinding(
                            FUTURE_PIVOT,
                            f"{pivot_column} {pivot.isoformat()} is after anchor "
                            f"{anchor.isoformat()}",
                            row_index=index,
                            column=pivot_column,
                        )
                    )
                if normalization_fit_column is not None:
                    fit_end = self._as_datetime(get_optional(row, normalization_fit_column))
                    if fit_end is not None and fit_end > anchor:
                        findings.append(
                            LeakageFinding(
                                NORMALIZATION_FIT,
                                f"{normalization_fit_column} {fit_end.isoformat()} is after "
                                f"anchor {anchor.isoformat()}",
                                row_index=index,
                                column=normalization_fit_column,
                            )
                        )

        return LeakageScanResult(tuple(findings))

    def assert_clean(self, rows: list[dict[str, Any]], **options: Any) -> None:
        self.scan(rows, **options).assert_clean()

    @staticmethod
    def _as_datetime(value: Any) -> datetime | None:
        if isinstance(value, datetime):
            return value
        return None
