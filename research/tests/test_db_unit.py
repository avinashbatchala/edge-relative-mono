"""Unit tests for configuration, instrumentation and row mapping (no database)."""

from __future__ import annotations

import pytest

from edge_relative_research.db import CollectingObserver, DatabaseConfig, QueryStats
from edge_relative_research.repositories import where_clause
from edge_relative_research.repositories.models import InstrumentRow, from_row


def test_config_prefers_research_overrides() -> None:
    env = {
        "RESEARCH_DB_HOST": "research-host",
        "POSTGRES_HOST": "backend-host",
        "RESEARCH_DB_PORT": "6543",
        "POSTGRES_DB": "edge",
        "POSTGRES_USER": "reader",
        "POSTGRES_PASSWORD": "secret",
    }
    config = DatabaseConfig.from_env(env)
    assert config.host == "research-host"
    assert config.port == 6543
    assert config.dbname == "edge"
    assert config.user == "reader"
    assert config.password == "secret"


def test_config_falls_back_to_postgres_variables() -> None:
    env = {"POSTGRES_HOST": "h", "POSTGRES_DB": "d", "POSTGRES_USER": "u"}
    config = DatabaseConfig.from_env(env)
    assert config.host == "h"
    assert config.port == 5432
    assert config.password is None


def test_config_missing_required_raises() -> None:
    with pytest.raises(ValueError):
        DatabaseConfig.from_env({})


def test_config_dsn_never_includes_password() -> None:
    config = DatabaseConfig("h", 5432, "d", "u", password="secret")
    assert "secret" not in config.dsn()
    assert config.connect_kwargs()["password"] == "secret"


def test_where_clause_is_parameterized_only() -> None:
    assert where_clause([]) == ""
    clause = where_clause(["instrument_id = %s", "setup_status = ANY(%s)"])
    assert clause == " WHERE instrument_id = %s AND setup_status = ANY(%s)"


def test_collecting_observer_totals() -> None:
    observer = CollectingObserver()
    observer.record(QueryStats("a", "SELECT 1", 2, 0.5))
    observer.record(QueryStats("b", "SELECT 2", 3, 1.5, streamed=True))
    assert observer.total_rows == 5
    assert observer.total_duration_seconds == pytest.approx(2.0)
    observer.clear()
    assert observer.stats == []


def test_from_row_requires_every_column() -> None:
    with pytest.raises(KeyError):
        from_row(InstrumentRow, {"instrument_id": 1})
