"""Chronological train / validation / out-of-sample splitting.

Time series must never be shuffled. All splits are contiguous, strictly ordered, non-overlapping and
inclusive of their end date, so a backtest window can be handed directly to the Java API.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta


@dataclass(frozen=True)
class Segment:
    label: str
    start: date
    end: date

    def as_iso(self) -> dict[str, str]:
        return {"startDate": self.start.isoformat(), "endDate": self.end.isoformat()}

    def days(self) -> int:
        return (self.end - self.start).days + 1


def _contiguous(start: date, end: date, weights: list[float], labels: list[str]) -> list[Segment]:
    if start > end:
        raise ValueError("start must be on or before end")
    if len(weights) != len(labels) or not weights:
        raise ValueError("weights and labels must be non-empty and the same length")
    if any(weight <= 0 for weight in weights):
        raise ValueError("weights must be positive")
    total_days = (end - start).days + 1
    total_weight = sum(weights)
    segments: list[Segment] = []
    cursor = start
    remaining = total_days
    for index, (weight, label) in enumerate(zip(weights, labels)):
        if index == len(weights) - 1:
            segment_days = remaining
        else:
            segment_days = max(1, round(total_days * weight / total_weight))
            segment_days = min(segment_days, remaining - (len(weights) - index - 1))
        segment_end = cursor + timedelta(days=segment_days - 1)
        segments.append(Segment(label=label, start=cursor, end=segment_end))
        cursor = segment_end + timedelta(days=1)
        remaining -= segment_days
    return segments


def train_validation_oos(
    start: date,
    end: date,
    train_fraction: float = 0.6,
    validation_fraction: float = 0.2,
) -> tuple[Segment, Segment, Segment]:
    """Split [start, end] into contiguous TRAIN / VALIDATION / OOS segments."""
    if train_fraction <= 0 or validation_fraction <= 0 or train_fraction + validation_fraction >= 1:
        raise ValueError("fractions must be positive and leave room for OOS")
    oos_fraction = 1.0 - train_fraction - validation_fraction
    segments = _contiguous(
        start,
        end,
        [train_fraction, validation_fraction, oos_fraction],
        ["TRAIN", "VALIDATION", "OOS"],
    )
    return segments[0], segments[1], segments[2]


def chronological_folds(
    start: date,
    end: date,
    n_folds: int,
    train_fraction: float = 0.5,
) -> list[tuple[Segment, Segment]]:
    """Expanding/rolling walk-forward folds, each a contiguous (TRAIN, TEST) pair.

    Fold i trains on the first part of the window up to the fold and tests on the next block, moving
    strictly forward in time. No test observation precedes its training data.
    """
    if n_folds < 1:
        raise ValueError("n_folds must be >= 1")
    if not 0 < train_fraction < 1:
        raise ValueError("train_fraction must be within (0, 1)")
    total_days = (end - start).days + 1
    if total_days < n_folds + 1:
        raise ValueError("window too short for the requested folds")
    block = total_days / (n_folds + 1)
    folds: list[tuple[Segment, Segment]] = []
    for index in range(n_folds):
        test_start = start + timedelta(days=int(round(block * (index + 1))))
        test_end = (
            end
            if index == n_folds - 1
            else start + timedelta(days=int(round(block * (index + 2))) - 1)
        )
        train_start = start
        train_span = int(round((test_start - start).days * train_fraction))
        train_end = start + timedelta(days=max(0, train_span - 1))
        folds.append(
            (
                Segment("TRAIN", train_start, train_end),
                Segment("TEST", test_start, test_end),
            )
        )
    return folds
