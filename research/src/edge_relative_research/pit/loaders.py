"""Repository-backed point-in-time loaders.

Every method requires the loader's ``as_of_timestamp`` and converts it to the exchange
session date for effective-dated reference reads. Returned rows are re-checked with the
guards so a future mapping can never be returned even if a query regressed.
"""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Any

from ..timeutil import exchange_session_date
from .errors import PointInTimeError
from .guards import (
    assert_constituent_as_of,
    assert_is_active_member,
    assert_not_future,
    ensure_as_of,
)
from .join import PointInTimeJoiner

if TYPE_CHECKING:  # pragma: no cover - typing only
    from ..repositories.operational import OperationalRepository
    from ..repositories.reference import ReferenceRepository


class PointInTimeLoader:
    """As-of loaders for reference, calendar and setup-observation inputs."""

    def __init__(
        self,
        reference: ReferenceRepository,
        *,
        operational: OperationalRepository | None = None,
        as_of_timestamp: datetime | None,
    ) -> None:
        self._as_of = ensure_as_of(as_of_timestamp)
        self._reference = reference
        self._operational = operational

    @property
    def as_of_timestamp(self) -> datetime:
        return self._as_of

    @property
    def session_date(self):
        return exchange_session_date(self._as_of)

    @property
    def joiner(self) -> PointInTimeJoiner:
        return PointInTimeJoiner(self._as_of)

    def identity(self, instrument_id: int):
        row = self._reference.identifier_as_of(instrument_id, self.session_date)
        if row is not None:
            assert_constituent_as_of(
                row, self.session_date, label="identifier", valid_from_keys=("valid_from",)
            )
        return row

    def sector(self, instrument_id: int):
        row = self._reference.sector_as_of(instrument_id, self.session_date)
        if row is not None:
            assert_is_active_member(row, self.session_date, label="sector mapping")
        return row

    def benchmark_membership(self, instrument_id: int):
        row = self._reference.benchmark_membership_for_instrument(instrument_id, self.session_date)
        if row is not None:
            assert_is_active_member(row, self.session_date, label="benchmark membership")
        return row

    def benchmark_constituents(self, benchmark_id: int):
        rows = self._reference.benchmark_constituents_as_of(benchmark_id, self.session_date)
        for row in rows:
            assert_is_active_member(row, self.session_date, label="benchmark constituent")
        return rows

    def universe_members(self, universe_id: int):
        rows = self._reference.universe_members_as_of(universe_id, self.session_date)
        for row in rows:
            assert_is_active_member(row, self.session_date, label="universe member")
        return rows

    def trading_sessions(self, exchange_id: int):
        return self._reference.sessions(exchange_id, self.session_date, self.session_date)

    def setup_observations(self, instrument_id: int, **filters: Any):
        if self._operational is None:
            raise PointInTimeError("an operational repository is required for setup reads")
        rows = self._operational.setup_observations(
            instrument_id=instrument_id, to_timestamp=self._as_of, **filters
        )
        for row in rows:
            assert_not_future(row.observed_at, self._as_of, label="setup observation")
        return rows

    def align_features(self, base_rows: Any, feature_rows: Any, **join_options: Any):
        return self.joiner.align(base_rows, feature_rows, **join_options)
