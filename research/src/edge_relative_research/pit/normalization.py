"""Normalization-fit guards.

A normalization statistic (mean, std, quantile, winsor bound, z-score baseline) may only
be computed from data strictly at or before its first use, and inside the training
window. Fitting on the full sample or on the validation/test period is leakage.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass, field
from datetime import datetime

from ..timeutil import ensure_utc
from .access import get_optional
from .errors import NormalizationLeakError, PointInTimeError


@dataclass(frozen=True, slots=True)
class NormalizationBaseline:
    name: str
    fit_start: datetime
    fit_end: datetime
    statistics: Mapping[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        start = ensure_utc(self.fit_start)
        end = ensure_utc(self.fit_end)
        if end < start:
            raise PointInTimeError(f"normalization {self.name}: fit_end precedes fit_start")
        object.__setattr__(self, "fit_start", start)
        object.__setattr__(self, "fit_end", end)

    def assert_fit_not_after(self, anchor: datetime, *, label: str | None = None) -> None:
        limit = ensure_utc(anchor)
        if self.fit_end > limit:
            raise NormalizationLeakError(
                f"normalization {label or self.name} fit_end {self.fit_end.isoformat()} is "
                f"after use at {limit.isoformat()}"
            )

    def assert_fit_within(self, window_start: datetime, window_end: datetime) -> None:
        start = ensure_utc(window_start)
        end = ensure_utc(window_end)
        if self.fit_start < start or self.fit_end > end:
            raise NormalizationLeakError(
                f"normalization {self.name} fit window [{self.fit_start.isoformat()}, "
                f"{self.fit_end.isoformat()}] is not inside [{start.isoformat()}, "
                f"{end.isoformat()}]"
            )


def assert_fit_before_use(baseline: NormalizationBaseline, anchor: datetime) -> None:
    baseline.assert_fit_not_after(anchor)


def assert_fit_within_train(
    baseline: NormalizationBaseline, train_start: datetime, train_end: datetime
) -> None:
    baseline.assert_fit_within(train_start, train_end)


def assert_fit_before_validation(
    baseline: NormalizationBaseline, validation_start: datetime
) -> None:
    limit = ensure_utc(validation_start)
    if baseline.fit_end > limit:
        raise NormalizationLeakError(
            f"normalization {baseline.name} fit_end {baseline.fit_end.isoformat()} overlaps "
            f"validation starting {limit.isoformat()}"
        )


def assert_baseline_not_fit_on_test(
    baseline: NormalizationBaseline, test_start: datetime, test_end: datetime
) -> None:
    start = ensure_utc(test_start)
    end = ensure_utc(test_end)
    if baseline.fit_start < end and baseline.fit_end > start:
        raise NormalizationLeakError(
            f"normalization {baseline.name} fit window overlaps the test period "
            f"[{start.isoformat()}, {end.isoformat()}]"
        )


def baseline_from_row(row: object, *, name_key: str = "name") -> NormalizationBaseline:
    """Build a baseline from a row carrying ``fit_start``/``fit_end`` (fail closed)."""

    fit_start = get_optional(row, "fit_start", "normalization_fit_start")
    fit_end = get_optional(row, "fit_end", "normalization_fit_end")
    if fit_start is None or fit_end is None:
        raise NormalizationLeakError("normalization row has no fit window")
    name = get_optional(row, name_key) or "normalization"
    return NormalizationBaseline(name=str(name), fit_start=fit_start, fit_end=fit_end)
