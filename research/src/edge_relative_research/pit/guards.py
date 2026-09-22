"""Point-in-time guards.

Each guard is a pure assertion that raises a specific error. They are the building
blocks of the join framework and the leakage scanner, and they fail closed: when a
known-time cannot be established, the guard refuses rather than assuming it is safe.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping, Sequence
from datetime import date, datetime, time, timezone
from typing import Any

from ..timeutil import ensure_utc
from .access import get_optional, get_value
from .errors import (
    FullDayVolumeError,
    FutureConstituentError,
    FutureCorporateActionError,
    FutureDataError,
    FuturePivotError,
    IncompleteBarError,
    MissingAsOfError,
    OutcomeInFeatureError,
    PointInTimeError,
)
from .vocab import VolumeScope, find_outcome_columns, is_full_day_volume_column

_UTC = timezone.utc


def _to_utc(value: Any, *, label: str) -> datetime:
    if isinstance(value, datetime):
        return ensure_utc(value)
    if isinstance(value, date):
        return datetime.combine(value, time.min, tzinfo=_UTC)
    raise PointInTimeError(f"{label} is not a date/datetime: {value!r}")


def ensure_as_of(as_of_timestamp: datetime | None, *, label: str = "as_of_timestamp") -> datetime:
    """Require and normalize a point-in-time coordinate. ``None`` is an error."""

    if as_of_timestamp is None:
        raise MissingAsOfError(f"{label} is required for point-in-time access")
    return ensure_utc(as_of_timestamp)


def assert_not_future(timestamp: Any, anchor: Any, *, label: str = "source") -> None:
    """Raise if ``timestamp`` is later than ``anchor``."""

    moment = _to_utc(timestamp, label=label)
    limit = _to_utc(anchor, label="anchor")
    if moment > limit:
        raise FutureDataError(
            f"future data: {label} {moment.isoformat()} is after anchor {limit.isoformat()}"
        )


def assert_rows_not_future(
    rows: Iterable[Any],
    anchor: Any,
    *,
    timestamp_keys: Sequence[str] = ("timestamp", "anchor_timestamp", "observed_at"),
    label: str = "row",
) -> None:
    for index, row in enumerate(rows):
        timestamp = get_optional(row, *timestamp_keys)
        if timestamp is None:
            raise PointInTimeError(f"{label}[{index}] has no timestamp to validate")
        assert_not_future(timestamp, anchor, label=f"{label}[{index}]")


def assert_closed_bar(bar: Any, anchor: Any, *, label: str = "bar") -> None:
    """A higher-timeframe bar may only be read once it has closed and is complete."""

    limit = _to_utc(anchor, label="anchor")
    close_time = get_optional(bar, "close_time")
    if close_time is not None:
        close = _to_utc(close_time, label=f"{label}.close_time")
        if close > limit:
            raise IncompleteBarError(
                f"{label} closes at {close.isoformat()} which is after anchor {limit.isoformat()}"
            )
    complete = get_optional(bar, "complete", "is_complete")
    if complete is False:
        raise IncompleteBarError(f"{label} is marked incomplete at {limit.isoformat()}")
    quality = get_optional(bar, "quality_state", "quality")
    if isinstance(quality, str) and quality.upper() == "INCOMPLETE":
        raise IncompleteBarError(f"{label} quality is INCOMPLETE at {limit.isoformat()}")


def assert_feature_timestamps(
    features: Iterable[Any],
    anchor: Any,
    *,
    timestamp_keys: Sequence[str] = ("anchor_timestamp", "timestamp", "observation_timestamp"),
    label: str = "feature",
) -> None:
    """Every feature must carry a timestamp no later than the anchor."""

    limit = _to_utc(anchor, label="anchor")
    for index, feature in enumerate(features):
        timestamp = get_optional(feature, *timestamp_keys)
        if timestamp is None:
            raise PointInTimeError(f"{label}[{index}] has no timestamp to validate")
        assert_not_future(timestamp, limit, label=f"{label}[{index}]")


def assert_volume_scope(
    scope: VolumeScope | str,
    anchor: Any,
    session_close: Any,
    *,
    label: str = "volume",
) -> None:
    scope_value = scope if isinstance(scope, VolumeScope) else VolumeScope(str(scope))
    if scope_value is VolumeScope.SESSION_TOTAL and _to_utc(anchor, label="anchor") < _to_utc(
        session_close, label="session_close"
    ):
        raise FullDayVolumeError(
            f"{label} uses SESSION_TOTAL scope before session close "
            f"(anchor {_to_utc(anchor, label='anchor').isoformat()})"
        )


def assert_no_full_day_volume(
    source: Any, anchor: Any, session_close: Any, *, label: str = "row"
) -> None:
    """Reject full-session volume columns inside an intraday row."""

    if isinstance(source, Mapping):
        names = [str(key) for key in source]
    elif isinstance(source, (str, bytes)):
        names = [source.decode() if isinstance(source, bytes) else source]
    elif isinstance(source, (list, tuple, set, frozenset)):
        names = [str(item) for item in source]
    else:
        names = [name for name in dir(source) if not name.startswith("_")]
    offending = sorted(name for name in names if is_full_day_volume_column(name))
    if offending and _to_utc(anchor, label="anchor") < _to_utc(
        session_close, label="session_close"
    ):
        raise FullDayVolumeError(
            f"{label} contains full-session volume columns {offending} before session close"
        )


def assert_pivot_confirmed_by(
    pivot: Any,
    anchor: Any,
    *,
    confirmed_keys: Sequence[str] = ("confirmed_at", "pivot_confirmed_at", "confirmation_time"),
    label: str = "pivot",
) -> None:
    confirmed = get_optional(pivot, *confirmed_keys)
    if confirmed is None:
        raise FuturePivotError(
            f"{label} has no confirmation timestamp; unconfirmed pivots may not be used"
        )
    limit = _to_utc(anchor, label="anchor")
    confirmed_at = _to_utc(confirmed, label=f"{label}.confirmed_at")
    if confirmed_at > limit:
        raise FuturePivotError(
            f"{label} is confirmed at {confirmed_at.isoformat()}, after anchor {limit.isoformat()}"
        )


def assert_constituent_as_of(
    membership: Any,
    anchor: Any,
    *,
    valid_from_keys: Sequence[str] = ("valid_from", "effective_from"),
    label: str = "constituent",
) -> None:
    valid_from = get_optional(membership, *valid_from_keys)
    if valid_from is None:
        raise FutureConstituentError(f"{label} has no effective date")
    if _to_utc(valid_from, label=f"{label}.valid_from") > _to_utc(anchor, label="anchor"):
        raise FutureConstituentError(
            f"{label} is effective from {valid_from}, after anchor {anchor}"
        )


def assert_corporate_action_known(
    action: Any,
    anchor: Any,
    *,
    known_keys: Sequence[str] = ("available_at", "known_at"),
    effective_keys: Sequence[str] = ("ex_date", "effective_date"),
    label: str = "corporate action",
) -> None:
    known = get_optional(action, *known_keys)
    if known is None:
        known = get_optional(action, *effective_keys)
    if known is None:
        raise FutureCorporateActionError(
            f"{label} has no known-time or effective date; refusing to use it"
        )
    if _to_utc(known, label=f"{label}.known_at") > _to_utc(anchor, label="anchor"):
        raise FutureCorporateActionError(
            f"{label} was not known until {known}, after anchor {anchor}"
        )


def assert_no_outcome_columns(columns: Iterable[str], *, context: str = "feature") -> None:
    offending = find_outcome_columns(columns)
    if offending:
        raise OutcomeInFeatureError(f"{context} inputs contain future-outcome columns: {offending}")


def assert_is_active_member(
    membership: Any,
    anchor: Any,
    *,
    valid_from_keys: Sequence[str] = ("valid_from", "effective_from"),
    valid_to_keys: Sequence[str] = ("valid_to", "effective_to"),
    label: str = "membership",
) -> None:
    """A membership must be effective and not stale at the anchor."""

    limit = _to_utc(anchor, label="anchor")
    valid_from = get_optional(membership, *valid_from_keys)
    valid_to = get_optional(membership, *valid_to_keys)
    if valid_from is None:
        raise FutureConstituentError(f"{label} has no effective date")
    if _to_utc(valid_from, label=f"{label}.valid_from") > limit:
        raise FutureConstituentError(f"{label} is not yet effective at {limit.isoformat()}")
    if valid_to is not None and _to_utc(valid_to, label=f"{label}.valid_to") <= limit:
        raise FutureConstituentError(f"{label} is no longer effective at {limit.isoformat()}")


def get_column_names(source: Iterable[Any] | Mapping[str, Any]) -> list[str]:
    if isinstance(source, Mapping):
        return [str(key) for key in source]
    return [str(get_value(item, "name", default=item)) for item in source]
