"""UTC and NSE (Asia/Kolkata) time helpers.

Machine timestamps are timezone-aware UTC ``datetime`` values. Session semantics are
computed in ``Asia/Kolkata`` and only converted back to UTC when stored or compared.
Per repository invariants, the NSE cash session is a fixed ``[09:15, 15:30)`` window;
holidays and special sessions are not modelled here yet and must not be invented.
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

UTC = timezone.utc
EXCHANGE_TZ = ZoneInfo("Asia/Kolkata")

SESSION_OPEN_TIME = time(9, 15)
SESSION_CLOSE_TIME = time(15, 30)


def ensure_utc(value: datetime) -> datetime:
    """Return ``value`` as a tz-aware UTC ``datetime``.

    Naive datetimes are rejected: without a zone the same wall-clock value is
    ambiguous and could silently shift a point-in-time cutoff.
    """

    if value.tzinfo is None or value.tzinfo.utcoffset(value) is None:
        raise ValueError("timestamp must be timezone-aware; naive datetimes are ambiguous")
    return value.astimezone(UTC)


def exchange_time(value: datetime) -> datetime:
    """Convert an instant to exchange-local (``Asia/Kolkata``) time."""

    return ensure_utc(value).astimezone(EXCHANGE_TZ)


def exchange_session_date(value: datetime) -> date:
    """Return the NSE session date for an instant."""

    return exchange_time(value).date()


def session_open(value: datetime) -> datetime:
    """Return the exchange-local session open for the instant's session date."""

    return datetime.combine(exchange_session_date(value), SESSION_OPEN_TIME, tzinfo=EXCHANGE_TZ)


def session_close(value: datetime) -> datetime:
    """Return the exchange-local session close for the instant's session date."""

    return datetime.combine(exchange_session_date(value), SESSION_CLOSE_TIME, tzinfo=EXCHANGE_TZ)


def minutes_since_open(value: datetime) -> int:
    """Whole minutes elapsed since the session open."""

    delta = exchange_time(value) - session_open(value)
    return int(delta.total_seconds() // 60)


def minutes_to_close(value: datetime) -> int:
    """Whole minutes remaining until the session close (never negative)."""

    delta = session_close(value) - exchange_time(value)
    return max(0, int(delta.total_seconds() // 60))


def is_session_time(value: datetime) -> bool:
    """True when the instant falls inside the canonical cash session window."""

    local = exchange_time(value).time()
    return SESSION_OPEN_TIME <= local < SESSION_CLOSE_TIME


def add_minutes(value: datetime, minutes: int) -> datetime:
    """Return ``value`` shifted by whole minutes, still tz-aware UTC."""

    return ensure_utc(value) + timedelta(minutes=minutes)
