"""Read adapters for the ``market`` schema.

Market observations are the point-in-time anchor registry; candles are the canonical
M1 store (higher timeframes are derived by the Java engine). Timestamp ranges are always
half-open ``[from, to)`` so a boundary bar is never double counted.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import datetime
from typing import Any

from .base import RepositoryBase, where_clause
from .models import (
    CandleCoverageRow,
    CandleRow,
    IngestionRunRow,
    MarketDataIncidentRow,
    MarketObservationRevisionRow,
    MarketObservationRow,
    QualityCountRow,
)

_OBSERVATION_COLUMNS = (
    "mo.market_observation_id, mo.observation_key, mo.instrument_id, mo.timeframe_id, "
    "t.code AS timeframe_code, mo.bar_close_timestamp, mo.market_data_source_id, "
    "mo.quality_status, mo.storage_uri"
)
_CANDLE_COLUMNS = (
    "candle_id, instrument_id, timeframe_id, open_time, close_time, open, high, low, "
    "close, volume, vwap, is_complete, quality_state, candle_definition_version, "
    "source_revision, revision_no, is_current"
)


class MarketRepository(RepositoryBase):
    """Market observation, revision, candle and ingestion reads."""

    def observations(
        self,
        instrument_id: int,
        from_timestamp: datetime,
        to_timestamp: datetime,
        *,
        timeframe_code: str | None = None,
        limit: int | None = None,
    ) -> list[MarketObservationRow]:
        conditions = [
            "mo.instrument_id = %s",
            "mo.bar_close_timestamp >= %s",
            "mo.bar_close_timestamp < %s",
        ]
        params: list[Any] = [instrument_id, from_timestamp, to_timestamp]
        if timeframe_code is not None:
            conditions.append("t.code = %s")
            params.append(timeframe_code)
        sql = (
            f"SELECT {_OBSERVATION_COLUMNS} "
            "FROM market.market_observation mo "
            "JOIN reference.timeframe t ON t.timeframe_id = mo.timeframe_id"
            + where_clause(conditions)
            + " ORDER BY mo.bar_close_timestamp, mo.market_observation_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(MarketObservationRow, "market.observations", sql, params)

    def observation(self, market_observation_id: int) -> MarketObservationRow | None:
        sql = (
            f"SELECT {_OBSERVATION_COLUMNS} "
            "FROM market.market_observation mo "
            "JOIN reference.timeframe t ON t.timeframe_id = mo.timeframe_id "
            "WHERE mo.market_observation_id = %s"
        )
        return self._one(MarketObservationRow, "market.observation", sql, [market_observation_id])

    def revisions(self, market_observation_id: int) -> list[MarketObservationRevisionRow]:
        sql = (
            "SELECT market_observation_revision_id, market_observation_id, revision_no, "
            "quality_status, is_canonical, reason, corrected_payload, created_at "
            "FROM market.market_observation_revision "
            "WHERE market_observation_id = %s ORDER BY revision_no"
        )
        return self._many(
            MarketObservationRevisionRow,
            "market.revisions",
            sql,
            [market_observation_id],
        )

    def canonical_revision(self, market_observation_id: int) -> MarketObservationRevisionRow | None:
        sql = (
            "SELECT market_observation_revision_id, market_observation_id, revision_no, "
            "quality_status, is_canonical, reason, corrected_payload, created_at "
            "FROM market.market_observation_revision "
            "WHERE market_observation_id = %s AND is_canonical"
        )
        return self._one(
            MarketObservationRevisionRow,
            "market.canonical_revision",
            sql,
            [market_observation_id],
        )

    def quality_summary(
        self, instrument_id: int, from_timestamp: datetime, to_timestamp: datetime
    ) -> list[QualityCountRow]:
        sql = (
            "SELECT quality_status, count(*) AS row_count "
            "FROM market.market_observation "
            "WHERE instrument_id = %s AND bar_close_timestamp >= %s "
            "AND bar_close_timestamp < %s "
            "GROUP BY quality_status ORDER BY quality_status"
        )
        return self._many(
            QualityCountRow,
            "market.quality_summary",
            sql,
            [instrument_id, from_timestamp, to_timestamp],
        )

    def incidents(
        self, *, instrument_id: int | None = None, open_only: bool = False
    ) -> list[MarketDataIncidentRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if open_only:
            conditions.append("resolved_at IS NULL")
        sql = (
            "SELECT market_data_incident_id, incident_key, market_data_source_id, "
            "instrument_id, incident_type, severity, detected_at, resolved_at, details "
            "FROM market.market_data_incident"
            + where_clause(conditions)
            + " ORDER BY detected_at DESC"
        )
        return self._many(MarketDataIncidentRow, "market.incidents", sql, params)

    def candles(
        self,
        instrument_id: int,
        from_timestamp: datetime,
        to_timestamp: datetime,
        *,
        timeframe_code: str = "M1",
        limit: int | None = None,
    ) -> list[CandleRow]:
        conditions = [
            "instrument_id = %s",
            "timeframe_id = (SELECT timeframe_id FROM reference.timeframe WHERE code = %s)",
            "open_time >= %s",
            "open_time < %s",
            "is_current",
        ]
        params: list[Any] = [
            instrument_id,
            timeframe_code,
            from_timestamp,
            to_timestamp,
        ]
        sql = (
            f"SELECT {_CANDLE_COLUMNS} FROM market.candle"
            + where_clause(conditions)
            + " ORDER BY open_time"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(CandleRow, "market.candles", sql, params)

    def iterate_candles(
        self,
        instrument_id: int,
        from_timestamp: datetime,
        to_timestamp: datetime,
        *,
        timeframe_code: str = "M1",
    ) -> Iterator[CandleRow]:
        sql = (
            f"SELECT {_CANDLE_COLUMNS} FROM market.candle "
            "WHERE instrument_id = %s "
            "AND timeframe_id = (SELECT timeframe_id FROM reference.timeframe WHERE code = %s) "
            "AND open_time >= %s AND open_time < %s AND is_current "
            "ORDER BY open_time"
        )
        yield from self._stream(
            CandleRow,
            "market.iterate_candles",
            sql,
            [instrument_id, timeframe_code, from_timestamp, to_timestamp],
        )

    def coverage(
        self, instrument_id: int, *, timeframe_code: str | None = None
    ) -> list[CandleCoverageRow]:
        conditions = ["cc.instrument_id = %s"]
        params: list[Any] = [instrument_id]
        if timeframe_code is not None:
            conditions.append("t.code = %s")
            params.append(timeframe_code)
        sql = (
            "SELECT cc.candle_coverage_id, cc.instrument_id, cc.timeframe_id, "
            "cc.market_data_source_id, cc.chunk_start, cc.chunk_end, cc.status, "
            "cc.candle_count, cc.attempts, cc.last_error, cc.last_synced_at "
            "FROM market.candle_coverage cc "
            "JOIN reference.timeframe t ON t.timeframe_id = cc.timeframe_id"
            + where_clause(conditions)
            + " ORDER BY cc.chunk_start"
        )
        return self._many(CandleCoverageRow, "market.coverage", sql, params)

    def ingestion_runs(
        self,
        *,
        instrument_id: int | None = None,
        status: str | None = None,
    ) -> list[IngestionRunRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if status is not None:
            conditions.append("status = %s")
            params.append(status)
        sql = (
            "SELECT ingestion_run_id, run_key, instrument_id, timeframe_id, "
            "market_data_source_id, requested_from, requested_to, status, total_chunks, "
            "completed_chunks, failed_chunks, candles_written, last_error, created_at, "
            "completed_at FROM market.ingestion_run"
            + where_clause(conditions)
            + " ORDER BY created_at DESC"
        )
        return self._many(IngestionRunRow, "market.ingestion_runs", sql, params)
