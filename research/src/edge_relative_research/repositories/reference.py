"""Read adapters for the ``reference`` schema.

All temporal reads are as-of: they return the mapping that was effective at the supplied
date and never a mapping whose ``valid_from`` is in the future. Symbol, sector, benchmark
and universe membership all have non-overlapping validity windows in the schema.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

from .base import RepositoryBase, where_clause
from .models import (
    BenchmarkMembershipRow,
    BenchmarkRow,
    CorporateActionFactorRow,
    CorporateActionRow,
    InstrumentIdentifierRow,
    InstrumentRow,
    SectorMappingRow,
    SectorRow,
    TradingCalendarDayRow,
    TradingSessionRow,
    UniverseMembershipRow,
    UniverseRow,
)

_INSTRUMENT_COLUMNS = (
    "instrument_id, instrument_key, exchange_id, instrument_type, segment, "
    "canonical_symbol, display_name, currency_code, tick_size, lot_size, "
    "trading_status, listed_from, listed_to"
)
_INSTRUMENT_COLUMNS_QUALIFIED = (
    "i.instrument_id, i.instrument_key, i.exchange_id, i.instrument_type, i.segment, "
    "i.canonical_symbol, i.display_name, i.currency_code, i.tick_size, i.lot_size, "
    "i.trading_status, i.listed_from, i.listed_to"
)
_IDENTIFIER_COLUMNS = (
    "instrument_identifier_id, instrument_id, exchange_id, identifier_type, "
    "identifier_value, valid_from, valid_to"
)
_SECTOR_MAPPING_COLUMNS = (
    "ish.instrument_id, ish.sector_id, s.code AS sector_code, s.name AS sector_name, "
    "ish.valid_from, ish.valid_to, ish.source"
)


class ReferenceRepository(RepositoryBase):
    """Instrument, calendar, sector, benchmark, corporate-action and universe reads."""

    # --- instruments / identifiers -------------------------------------------

    def instruments(
        self,
        *,
        instrument_ids: list[int] | None = None,
        symbols: list[str] | None = None,
        instrument_types: list[str] | None = None,
        active_only: bool = False,
        limit: int | None = None,
    ) -> list[InstrumentRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_ids:
            conditions.append("instrument_id = ANY(%s)")
            params.append(list(instrument_ids))
        if symbols:
            conditions.append("canonical_symbol = ANY(%s)")
            params.append(list(symbols))
        if instrument_types:
            conditions.append("instrument_type = ANY(%s)")
            params.append(list(instrument_types))
        if active_only:
            conditions.append("trading_status = 'ACTIVE'")
        sql = f"SELECT {_INSTRUMENT_COLUMNS} FROM reference.instrument"
        sql += where_clause(conditions)
        sql += " ORDER BY canonical_symbol, instrument_id"
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(InstrumentRow, "reference.instruments", sql, params)

    def instrument(self, instrument_id: int) -> InstrumentRow | None:
        return self._one(
            InstrumentRow,
            "reference.instrument",
            f"SELECT {_INSTRUMENT_COLUMNS} FROM reference.instrument WHERE instrument_id = %s",
            [instrument_id],
        )

    def identifiers(
        self, instrument_id: int, *, as_of: date | None = None
    ) -> list[InstrumentIdentifierRow]:
        conditions = ["instrument_id = %s"]
        params: list[Any] = [instrument_id]
        if as_of is not None:
            conditions.append("valid_from <= %s AND (valid_to IS NULL OR valid_to > %s)")
            params.extend([as_of, as_of])
        sql = (
            f"SELECT {_IDENTIFIER_COLUMNS} FROM reference.instrument_identifier"
            + where_clause(conditions)
            + " ORDER BY valid_from DESC, instrument_identifier_id DESC"
        )
        return self._many(InstrumentIdentifierRow, "reference.identifiers", sql, params)

    def identifier_as_of(
        self,
        instrument_id: int,
        as_of: date,
        *,
        identifier_type: str = "SYMBOL",
    ) -> InstrumentIdentifierRow | None:
        sql = (
            f"SELECT {_IDENTIFIER_COLUMNS} FROM reference.instrument_identifier "
            "WHERE instrument_id = %s AND identifier_type = %s "
            "AND valid_from <= %s AND (valid_to IS NULL OR valid_to > %s) "
            "ORDER BY valid_from DESC, instrument_identifier_id DESC LIMIT 1"
        )
        return self._one(
            InstrumentIdentifierRow,
            "reference.identifier_as_of",
            sql,
            [instrument_id, identifier_type, as_of, as_of],
        )

    def resolve_symbol(self, symbol: str, as_of: date) -> InstrumentRow | None:
        sql = (
            f"SELECT {_INSTRUMENT_COLUMNS_QUALIFIED} FROM reference.instrument i "
            "JOIN reference.instrument_identifier ii ON ii.instrument_id = i.instrument_id "
            "WHERE ii.identifier_type = 'SYMBOL' AND ii.identifier_value = %s "
            "AND ii.valid_from <= %s AND (ii.valid_to IS NULL OR ii.valid_to > %s) "
            "ORDER BY ii.valid_from DESC LIMIT 1"
        )
        return self._one(InstrumentRow, "reference.resolve_symbol", sql, [symbol, as_of, as_of])

    # --- sectors -------------------------------------------------------------

    def sectors(self, *, active_only: bool = False) -> list[SectorRow]:
        sql = "SELECT sector_id, code, name, active FROM reference.sector"
        if active_only:
            sql += " WHERE active"
        sql += " ORDER BY code"
        return self._many(SectorRow, "reference.sectors", sql)

    def sector_as_of(self, instrument_id: int, as_of: date) -> SectorMappingRow | None:
        sql = (
            f"SELECT {_SECTOR_MAPPING_COLUMNS} "
            "FROM reference.instrument_sector_history ish "
            "JOIN reference.sector s ON s.sector_id = ish.sector_id "
            "WHERE ish.instrument_id = %s "
            "AND ish.valid_from <= %s AND (ish.valid_to IS NULL OR ish.valid_to > %s) "
            "ORDER BY ish.valid_from DESC LIMIT 1"
        )
        return self._one(
            SectorMappingRow,
            "reference.sector_as_of",
            sql,
            [instrument_id, as_of, as_of],
        )

    def sector_history(
        self,
        instrument_id: int,
        *,
        from_date: date | None = None,
        to_date: date | None = None,
    ) -> list[SectorMappingRow]:
        conditions = ["ish.instrument_id = %s"]
        params: list[Any] = [instrument_id]
        if from_date is not None:
            conditions.append("(ish.valid_to IS NULL OR ish.valid_to > %s)")
            params.append(from_date)
        if to_date is not None:
            conditions.append("ish.valid_from < %s")
            params.append(to_date)
        sql = (
            f"SELECT {_SECTOR_MAPPING_COLUMNS} "
            "FROM reference.instrument_sector_history ish "
            "JOIN reference.sector s ON s.sector_id = ish.sector_id"
            + where_clause(conditions)
            + " ORDER BY ish.valid_from"
        )
        return self._many(SectorMappingRow, "reference.sector_history", sql, params)

    # --- benchmarks ----------------------------------------------------------

    def benchmarks(self, *, active_only: bool = False) -> list[BenchmarkRow]:
        sql = (
            "SELECT benchmark_id, benchmark_key, exchange_id, instrument_id, code, name, "
            "benchmark_type, active FROM reference.benchmark"
        )
        if active_only:
            sql += " WHERE active"
        sql += " ORDER BY code"
        return self._many(BenchmarkRow, "reference.benchmarks", sql)

    def benchmark_for_sector_as_of(self, sector_id: int, as_of: date) -> BenchmarkRow | None:
        sql = (
            "SELECT b.benchmark_id, b.benchmark_key, b.exchange_id, b.instrument_id, "
            "b.code, b.name, b.benchmark_type, b.active "
            "FROM reference.sector_benchmark_history sbh "
            "JOIN reference.benchmark b ON b.benchmark_id = sbh.benchmark_id "
            "WHERE sbh.sector_id = %s "
            "AND sbh.valid_from <= %s AND (sbh.valid_to IS NULL OR sbh.valid_to > %s) "
            "ORDER BY sbh.valid_from DESC LIMIT 1"
        )
        return self._one(
            BenchmarkRow,
            "reference.benchmark_for_sector_as_of",
            sql,
            [sector_id, as_of, as_of],
        )

    def benchmark_constituents_as_of(
        self, benchmark_id: int, as_of: date
    ) -> list[BenchmarkMembershipRow]:
        sql = (
            "SELECT bch.benchmark_id, b.code AS benchmark_code, bch.instrument_id, "
            "bch.weight, bch.valid_from, bch.valid_to "
            "FROM reference.benchmark_constituent_history bch "
            "JOIN reference.benchmark b ON b.benchmark_id = bch.benchmark_id "
            "WHERE bch.benchmark_id = %s "
            "AND bch.valid_from <= %s AND (bch.valid_to IS NULL OR bch.valid_to > %s) "
            "ORDER BY bch.instrument_id"
        )
        return self._many(
            BenchmarkMembershipRow,
            "reference.benchmark_constituents_as_of",
            sql,
            [benchmark_id, as_of, as_of],
        )

    def benchmark_membership_for_instrument(
        self, instrument_id: int, as_of: date
    ) -> BenchmarkMembershipRow | None:
        sql = (
            "SELECT bch.benchmark_id, b.code AS benchmark_code, bch.instrument_id, "
            "bch.weight, bch.valid_from, bch.valid_to "
            "FROM reference.benchmark_constituent_history bch "
            "JOIN reference.benchmark b ON b.benchmark_id = bch.benchmark_id "
            "WHERE bch.instrument_id = %s "
            "AND bch.valid_from <= %s AND (bch.valid_to IS NULL OR bch.valid_to > %s) "
            "ORDER BY bch.valid_from DESC LIMIT 1"
        )
        return self._one(
            BenchmarkMembershipRow,
            "reference.benchmark_membership_for_instrument",
            sql,
            [instrument_id, as_of, as_of],
        )

    # --- trading calendar / sessions -----------------------------------------

    def trading_days(
        self, exchange_id: int, from_date: date, to_date: date
    ) -> list[TradingCalendarDayRow]:
        sql = (
            "SELECT trading_calendar_day_id, exchange_id, trading_date, day_type, notes "
            "FROM reference.trading_calendar_day "
            "WHERE exchange_id = %s AND trading_date >= %s AND trading_date <= %s "
            "ORDER BY trading_date"
        )
        return self._many(
            TradingCalendarDayRow,
            "reference.trading_days",
            sql,
            [exchange_id, from_date, to_date],
        )

    def sessions(self, exchange_id: int, from_date: date, to_date: date) -> list[TradingSessionRow]:
        sql = (
            "SELECT ts.trading_session_id, ts.trading_calendar_day_id, tcd.trading_date, "
            "tcd.exchange_id, ts.session_type, ts.opens_at, ts.closes_at "
            "FROM reference.trading_session ts "
            "JOIN reference.trading_calendar_day tcd "
            "ON tcd.trading_calendar_day_id = ts.trading_calendar_day_id "
            "WHERE tcd.exchange_id = %s AND tcd.trading_date >= %s "
            "AND tcd.trading_date <= %s "
            "ORDER BY ts.opens_at"
        )
        return self._many(
            TradingSessionRow,
            "reference.sessions",
            sql,
            [exchange_id, from_date, to_date],
        )

    # --- corporate actions ---------------------------------------------------

    def corporate_actions(
        self,
        instrument_id: int,
        *,
        from_date: date | None = None,
        to_date: date | None = None,
    ) -> list[CorporateActionRow]:
        conditions = ["instrument_id = %s"]
        params: list[Any] = [instrument_id]
        if from_date is not None:
            conditions.append("ex_date >= %s")
            params.append(from_date)
        if to_date is not None:
            conditions.append("ex_date <= %s")
            params.append(to_date)
        sql = (
            "SELECT corporate_action_id, instrument_id, action_type, ex_date, record_date, "
            "effective_date, ratio_numerator, ratio_denominator, cash_amount, currency_code, "
            "source_reference FROM reference.corporate_action"
            + where_clause(conditions)
            + " ORDER BY ex_date, corporate_action_id"
        )
        return self._many(CorporateActionRow, "reference.corporate_actions", sql, params)

    def corporate_action_factors_as_of(
        self, instrument_id: int, as_of: datetime
    ) -> list[CorporateActionFactorRow]:
        sql = (
            "SELECT corporate_action_factor_id, corporate_action_id, instrument_id, "
            "factor_version, price_factor, quantity_factor, definition, available_at "
            "FROM reference.corporate_action_factor "
            "WHERE instrument_id = %s AND available_at <= %s "
            "ORDER BY available_at, factor_version"
        )
        return self._many(
            CorporateActionFactorRow,
            "reference.corporate_action_factors_as_of",
            sql,
            [instrument_id, as_of],
        )

    # --- universe ------------------------------------------------------------

    def universes(self, *, active_only: bool = False) -> list[UniverseRow]:
        sql = "SELECT universe_id, universe_key, code, name, active FROM reference.universe"
        if active_only:
            sql += " WHERE active"
        sql += " ORDER BY code"
        return self._many(UniverseRow, "reference.universes", sql)

    def universe_members_as_of(self, universe_id: int, as_of: date) -> list[UniverseMembershipRow]:
        sql = (
            "SELECT umh.universe_id, u.code AS universe_code, umh.instrument_id, "
            "umh.valid_from, umh.valid_to "
            "FROM reference.universe_membership_history umh "
            "JOIN reference.universe u ON u.universe_id = umh.universe_id "
            "WHERE umh.universe_id = %s "
            "AND umh.valid_from <= %s AND (umh.valid_to IS NULL OR umh.valid_to > %s) "
            "ORDER BY umh.instrument_id"
        )
        return self._many(
            UniverseMembershipRow,
            "reference.universe_members_as_of",
            sql,
            [universe_id, as_of, as_of],
        )
