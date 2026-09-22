"""Point-in-time and leakage errors.

These are intentionally loud: a leakage guard fails the build, it does not warn.
"""

from __future__ import annotations


class PointInTimeError(RuntimeError):
    """Base class for point-in-time correctness failures."""


class MissingAsOfError(PointInTimeError):
    """A temporal loader was called without a required ``as_of_timestamp``."""


class FutureDataError(PointInTimeError):
    """A source timestamp is later than the anchor it is joined to."""


class IncompleteBarError(PointInTimeError):
    """An incomplete or not-yet-closed bar was used at an earlier anchor."""


class FullDayVolumeError(PointInTimeError):
    """Full-session volume was used inside an intraday row."""


class FuturePivotError(PointInTimeError):
    """A pivot was used before its confirmation instant."""


class FutureConstituentError(PointInTimeError):
    """A benchmark/universe constituent was used before it was effective."""


class FutureCorporateActionError(PointInTimeError):
    """A corporate action was applied before it was known."""


class OutcomeInFeatureError(PointInTimeError):
    """A future-outcome/label column appeared among feature inputs."""


class NormalizationLeakError(PointInTimeError):
    """A normalization statistic was fit using data at or after its use."""


class LeakageDetectedError(PointInTimeError):
    """A dataset scan found one or more leakage findings."""
