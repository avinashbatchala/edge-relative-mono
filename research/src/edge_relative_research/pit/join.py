"""Reusable point-in-time as-of join.

The joiner resolves effective-dated records strictly at or before an anchor. A record
whose ``valid_from`` is after the anchor is never selected, and the base anchor itself
may not exceed the joiner's required ``as_of_timestamp`` ceiling. Valid windows are
half-open ``[valid_from, valid_to)``.
"""

from __future__ import annotations

import dataclasses
from collections.abc import Hashable, Iterable, Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from ..timeutil import ensure_utc
from .access import get_optional, get_value
from .errors import FutureDataError, PointInTimeError
from .guards import assert_not_future, ensure_as_of


@dataclass(frozen=True, slots=True)
class TemporalRecord:
    entity_key: Hashable
    valid_from: datetime
    valid_to: datetime | None
    payload: Any


class PointInTimeJoiner:
    """Resolve and align effective-dated rows as of a required ceiling instant."""

    def __init__(self, as_of_timestamp: datetime | None) -> None:
        self._as_of = ensure_as_of(as_of_timestamp)

    @property
    def as_of_timestamp(self) -> datetime:
        return self._as_of

    def normalize(
        self,
        records: Iterable[Any],
        *,
        entity_key: str,
        valid_from_key: str = "valid_from",
        valid_to_key: str = "valid_to",
        payload: Any = None,
    ) -> list[TemporalRecord]:
        normalized: list[TemporalRecord] = []
        for record in records:
            if isinstance(record, TemporalRecord):
                normalized.append(record)
                continue
            valid_from = ensure_utc(get_value(record, valid_from_key))
            valid_to_raw = get_optional(record, valid_to_key)
            valid_to = None if valid_to_raw is None else ensure_utc(valid_to_raw)
            if valid_to is not None and valid_to <= valid_from:
                raise PointInTimeError("valid_to must be after valid_from")
            key = get_value(record, entity_key)
            normalized.append(
                TemporalRecord(
                    entity_key=key,
                    valid_from=valid_from,
                    valid_to=valid_to,
                    payload=record if payload is None else payload,
                )
            )
        return normalized

    def active_at(
        self,
        records: Sequence[TemporalRecord],
        anchor: datetime,
        *,
        entity_key: Hashable,
    ) -> TemporalRecord | None:
        limit = ensure_utc(anchor)
        matches = [
            record
            for record in records
            if record.entity_key == entity_key
            and record.valid_from <= limit
            and (record.valid_to is None or record.valid_to > limit)
        ]
        if not matches:
            return None
        return max(matches, key=lambda record: record.valid_from)

    def assert_no_future(
        self,
        records: Sequence[TemporalRecord],
        anchor: datetime | None = None,
    ) -> None:
        limit = self._as_of if anchor is None else ensure_utc(anchor)
        for record in records:
            if record.valid_from > limit:
                raise FutureDataError(
                    f"temporal record for {record.entity_key!r} starts at "
                    f"{record.valid_from.isoformat()}, after {limit.isoformat()}"
                )

    def align(
        self,
        base_rows: Iterable[Mapping[str, Any]],
        records: Iterable[Any],
        *,
        base_entity_key: str,
        record_entity_key: str,
        base_anchor_key: str = "anchor_timestamp",
        valid_from_key: str = "valid_from",
        valid_to_key: str = "valid_to",
        result_key: str = "point_in_time",
    ) -> list[dict[str, Any]]:
        normalized = self.normalize(
            records,
            entity_key=record_entity_key,
            valid_from_key=valid_from_key,
            valid_to_key=valid_to_key,
        )
        aligned: list[dict[str, Any]] = []
        for row in base_rows:
            anchor = ensure_utc(get_value(row, base_anchor_key))
            assert_not_future(anchor, self._as_of, label=f"base.{base_anchor_key}")
            match = self.active_at(normalized, anchor, entity_key=get_value(row, base_entity_key))
            merged: dict[str, Any] = dict(row)
            merged[result_key] = None if match is None else match.payload
            aligned.append(merged)
        return aligned


def row_to_mapping(row: Any) -> dict[str, Any]:
    if isinstance(row, Mapping):
        return dict(row)
    if dataclasses.is_dataclass(row) and not isinstance(row, type):
        return {field.name: getattr(row, field.name) for field in dataclasses.fields(row)}
    raise TypeError(f"cannot convert {type(row)!r} to a mapping")
