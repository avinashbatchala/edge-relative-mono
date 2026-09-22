"""Controlled vocabularies mirrored from the Java domain and PostgreSQL schema.

The string values match the persisted/Java spellings exactly so a contract can be
round-tripped against the database without translation. See:

- ``strategy/domain/Direction.java`` and ``SetupState.java``
- ``feature/domain/FeatureAvailability.java`` and ``FeatureQuality.java``
- ``reference.timeframe`` (V011) and the ``market``/``operational`` CHECK constraints
- ``backtest/domain/BacktestSpec.java`` (cost/execution policies)
- ``operational.order_record`` / ``fill`` / ``trade`` CHECK constraints
- ``research.dataset`` type/status and ``pattern_window_metadata`` columns
"""

from __future__ import annotations

from enum import StrEnum


class Direction(StrEnum):
    LONG = "LONG"
    SHORT = "SHORT"


class SetupState(StrEnum):
    """Setup lifecycle vocabulary.

    ``REJECTED`` exists in the ``setup_observation`` CHECK constraint but is not emitted
    by the Java ``SetupState`` enum; it is kept here so persisted rows can be
    represented faithfully. ``BLOCKED`` is a Java state and is persisted.
    """

    NONE = "NONE"
    WATCH = "WATCH"
    FORMING = "FORMING"
    NEAR_TRIGGER = "NEAR_TRIGGER"
    VALID = "VALID"
    INVALIDATED = "INVALIDATED"
    EXPIRED = "EXPIRED"
    MISSED = "MISSED"
    BLOCKED = "BLOCKED"
    REJECTED = "REJECTED"


class SetupInitialization(StrEnum):
    LIFECYCLE_START = "LIFECYCLE_START"
    COLD_START_RECONSTRUCTION = "COLD_START_RECONSTRUCTION"


class Timeframe(StrEnum):
    M1 = "M1"
    M3 = "M3"
    M5 = "M5"
    M15 = "M15"
    M30 = "M30"
    H1 = "H1"
    H2 = "H2"
    H4 = "H4"
    D1 = "D1"
    W1 = "W1"


class FeatureQuality(StrEnum):
    """Canonical data-quality state, ordered by monotonic severity."""

    GOOD = "GOOD"
    CORRECTED = "CORRECTED"
    DEGRADED = "DEGRADED"
    SUSPECT = "SUSPECT"
    STALE = "STALE"
    INCOMPLETE = "INCOMPLETE"
    UNAVAILABLE = "UNAVAILABLE"

    @property
    def severity(self) -> int:
        return _FEATURE_QUALITY_SEVERITY[self]

    @property
    def trustworthy(self) -> bool:
        return self in (FeatureQuality.GOOD, FeatureQuality.CORRECTED)


_FEATURE_QUALITY_SEVERITY: dict[FeatureQuality, int] = {
    FeatureQuality.GOOD: 0,
    FeatureQuality.CORRECTED: 1,
    FeatureQuality.DEGRADED: 2,
    FeatureQuality.SUSPECT: 3,
    FeatureQuality.STALE: 4,
    FeatureQuality.INCOMPLETE: 5,
    FeatureQuality.UNAVAILABLE: 6,
}


def worst_quality(qualities: object) -> FeatureQuality:
    """Return the worst quality by severity, defaulting to ``GOOD`` when empty."""

    result = FeatureQuality.GOOD
    for quality in qualities:  # type: ignore[union-attr]
        if quality is None:
            continue
        if quality.severity >= result.severity:
            result = quality
    return result


class FeatureAvailability(StrEnum):
    """Why a feature value is or is not present. Missing is never numeric zero."""

    VALID = "VALID"
    WARMING_UP = "WARMING_UP"
    INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY"
    MISSING_INPUT = "MISSING_INPUT"
    STALE = "STALE"
    INCOMPLETE = "INCOMPLETE"
    INVALID = "INVALID"
    NOT_APPLICABLE = "NOT_APPLICABLE"

    @property
    def has_value(self) -> bool:
        return self is FeatureAvailability.VALID


class ContextKind(StrEnum):
    MARKET = "MARKET"
    SECTOR = "SECTOR"
    STOCK = "STOCK"
    TIME_OF_DAY = "TIME_OF_DAY"


class ValueKind(StrEnum):
    """Distinguishes observed facts, derived measurements, and future labels.

    A feature or pattern contract may never carry ``LABELED`` data; labels live only in
    the outcome contract.
    """

    OBSERVED = "OBSERVED"
    DERIVED = "DERIVED"
    LABELED = "LABELED"


class Side(StrEnum):
    BUY = "BUY"
    SELL = "SELL"


class ExitReason(StrEnum):
    STOP = "STOP"
    TARGET = "TARGET"
    TRAIL = "TRAIL"
    TIME_STOP = "TIME_STOP"
    REGIME_CHANGE = "REGIME_CHANGE"
    STRATEGY_INVALIDATION = "STRATEGY_INVALIDATION"
    RISK_EXIT = "RISK_EXIT"
    MANUAL_EXIT = "MANUAL_EXIT"
    EMERGENCY_EXIT = "EMERGENCY_EXIT"
    SESSION_FLATTEN = "SESSION_FLATTEN"
    OPEN_MARKED_TO_MARKET = "OPEN_MARKED_TO_MARKET"
    OTHER = "OTHER"


class LabelState(StrEnum):
    """Tristate label outcome; ``UNKNOWN`` preserves intrabar ambiguity."""

    HIT = "HIT"
    NOT_HIT = "NOT_HIT"
    UNKNOWN = "UNKNOWN"
    NOT_APPLICABLE = "NOT_APPLICABLE"


class DatasetType(StrEnum):
    MARKET = "MARKET"
    FEATURE = "FEATURE"
    PATTERN = "PATTERN"
    OUTCOME = "OUTCOME"
    BACKTEST = "BACKTEST"
    TRAINING = "TRAINING"
    OTHER = "OTHER"


class DatasetStatus(StrEnum):
    BUILDING = "BUILDING"
    COMMITTED = "COMMITTED"
    RETIRED = "RETIRED"
    FAILED = "FAILED"


class Cohort(StrEnum):
    SAME_STOCK = "SAME_STOCK"
    SAME_STOCK_SAME_REGIME = "SAME_STOCK_SAME_REGIME"
    SAME_SECTOR = "SAME_SECTOR"
    GLOBAL = "GLOBAL"


class AmbiguityPolicy(StrEnum):
    """How target/stop collision inside one bar is resolved for labels."""

    STOP_FIRST_CONSERVATIVE = "STOP_FIRST_CONSERVATIVE"
    TARGET_FIRST_OPTIMISTIC = "TARGET_FIRST_OPTIMISTIC"
    UNKNOWN = "UNKNOWN"
