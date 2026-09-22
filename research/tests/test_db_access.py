"""Integration tests for the data-access layer against a real PostgreSQL schema.

These run against a Testcontainers PostgreSQL with the backend Flyway migrations applied
and a small seeded dataset. They skip when Docker is unavailable.
"""

from __future__ import annotations

from datetime import date, datetime, timezone
from decimal import Decimal

import psycopg
import pytest

from edge_relative_research.db import ResearchDatabase

UTC = timezone.utc


# --- read-only safety --------------------------------------------------------


def test_read_only_connection_rejects_writes(database: ResearchDatabase) -> None:
    with pytest.raises(psycopg.errors.ReadOnlySqlTransaction):
        with database.read_only() as connection:
            connection.execute(
                "INSERT INTO research.dataset (dataset_key, code, name, dataset_type) "
                "VALUES (gen_random_uuid(), 'SHOULD_FAIL', 'x', 'OTHER')"
            )


def test_symbol_lookup_is_parameterized(repos) -> None:
    malicious = "RELIANCE' OR 1=1 --"
    assert repos.reference.resolve_symbol(malicious, date(2024, 1, 1)) is None
    resolved = repos.reference.resolve_symbol("RELIANCE", date(2024, 1, 1))
    assert resolved is not None
    assert resolved.instrument_id == 1


def test_instrument_identity_is_stable_not_symbol(repos) -> None:
    instrument = repos.reference.instrument(1)
    assert instrument is not None
    assert instrument.instrument_id == 1
    assert instrument.instrument_key is not None
    assert instrument.canonical_symbol == "RELIANCE"


# --- temporal as-of semantics ------------------------------------------------


def test_symbol_changed_historically(repos) -> None:
    assert repos.reference.identifier_as_of(1, date(2019, 6, 1)).identifier_value == "RELIANCEOLD"
    assert repos.reference.identifier_as_of(1, date(2022, 1, 1)).identifier_value == "RELIANCE"
    assert repos.reference.identifier_as_of(1, date(2025, 6, 1)).identifier_value == "RELIANCE-NEW"
    assert repos.reference.identifier_as_of(1, date(2010, 1, 1)) is None


def test_sector_changed_historically(repos) -> None:
    early = repos.reference.sector_as_of(1, date(2022, 1, 1))
    late = repos.reference.sector_as_of(1, date(2024, 1, 1))
    assert early is not None and early.sector_code == "ENERGY"
    assert late is not None and late.sector_code == "FINANCE"
    history = repos.reference.sector_history(1)
    assert [row.sector_code for row in history] == ["ENERGY", "FINANCE"]


def test_historical_benchmark_membership(repos) -> None:
    benchmark = repos.reference.benchmark_for_sector_as_of(1, date(2024, 1, 1))
    assert benchmark is not None and benchmark.code == "NIFTY50"
    old_members = repos.reference.benchmark_constituents_as_of(1, date(2023, 6, 1))
    new_members = repos.reference.benchmark_constituents_as_of(1, date(2025, 1, 1))
    assert [row.instrument_id for row in old_members] == [1]
    assert [row.instrument_id for row in new_members] == [2]
    assert repos.reference.benchmark_membership_for_instrument(1, date(2025, 1, 1)) is None
    membership = repos.reference.benchmark_membership_for_instrument(1, date(2023, 1, 1))
    assert membership is not None and membership.benchmark_code == "NIFTY50"


def test_no_future_temporal_mapping_returned(repos) -> None:
    # FINANCE mapping begins 2023, so a 2022 read must still see ENERGY.
    assert repos.reference.sector_as_of(1, date(2022, 12, 31)).sector_code == "ENERGY"
    # Company 2 joins the benchmark only in 2024.
    assert repos.reference.benchmark_membership_for_instrument(2, date(2023, 1, 1)) is None
    # Universe membership begins 2020.
    assert repos.reference.universe_members_as_of(1, date(2019, 1, 1)) == []
    # A corporate-action factor is only known from its available_at instant.
    assert repos.reference.corporate_action_factors_as_of(1, datetime(2024, 4, 1, tzinfo=UTC)) == []
    factors = repos.reference.corporate_action_factors_as_of(1, datetime(2024, 5, 15, tzinfo=UTC))
    assert factors[0].price_factor == Decimal("0.5")


def test_calendar_and_corporate_actions(repos) -> None:
    days = repos.reference.trading_days(1, date(2026, 9, 1), date(2026, 9, 2))
    assert [day.trading_date for day in days] == [date(2026, 9, 1), date(2026, 9, 2)]
    sessions = repos.reference.sessions(1, date(2026, 9, 1), date(2026, 9, 1))
    assert len(sessions) == 1 and sessions[0].session_type == "NORMAL"
    actions = repos.reference.corporate_actions(1)
    assert actions[0].action_type == "SPLIT"


# --- setup observations and classification -----------------------------------


def test_setup_observations_ordered_and_bounded(repos) -> None:
    rows = repos.operational.setup_observations(instrument_id=1)
    assert [row.setup_status for row in rows] == ["VALID", "NEAR_TRIGGER", "MISSED"]
    assert [row.observed_at for row in rows] == sorted(row.observed_at for row in rows)
    bounded = repos.operational.setup_observations(
        instrument_id=1, from_timestamp=datetime(2026, 9, 1, 4, 10, tzinfo=UTC)
    )
    assert [row.setup_observation_id for row in bounded] == [2, 3]
    assert len(repos.operational.setup_observations(instrument_id=1, limit=1)) == 1


def test_skipped_and_rejected_setup_classification(repos) -> None:
    executed = repos.operational.classify_setup(1)
    assert executed.setup_status == "VALID"
    assert executed.risk_decision == "APPROVE"
    assert executed.trade_id == 1
    rejected = repos.operational.classify_setup(2)
    assert rejected.setup_status == "NEAR_TRIGGER"
    assert rejected.risk_decision == "REJECT"
    assert rejected.risk_primary_reason_code == "INVALID_SETUP"
    assert rejected.trade_id is None
    skipped = repos.operational.classify_setup(3)
    assert skipped.setup_status == "MISSED"
    assert skipped.risk_decision is None
    assert skipped.trade_id is None

    rejected_rows = repos.operational.setup_classifications(states=["NEAR_TRIGGER"])
    assert [row.setup_observation_id for row in rejected_rows] == [2]
    reasons = repos.operational.risk_decision_reasons(2)
    assert reasons[0].reason_code == "INVALID_SETUP"
    decisions = repos.operational.risk_decisions(decisions=["REJECT"])
    assert [row.risk_decision_id for row in decisions] == [2]


def test_deterministic_ordering(repos) -> None:
    first = [r.setup_observation_id for r in repos.operational.setup_observations()]
    second = [r.setup_observation_id for r in repos.operational.setup_observations()]
    assert first == second == [1, 2, 3]


# --- orders and fills --------------------------------------------------------


def test_multiple_fills_for_one_order(repos) -> None:
    fills = repos.operational.fills(order_id=1)
    assert [fill.fill_id for fill in fills] == [1, 2]
    assert [fill.received_timestamp for fill in fills] == sorted(
        fill.received_timestamp for fill in fills
    )
    assert sum(fill.quantity for fill in fills) == 40


def test_partial_fill(repos) -> None:
    order = repos.operational.order(1)
    assert order is not None
    assert order.status == "PARTIAL"
    assert order.filled_quantity == 40
    assert order.requested_quantity == 100
    partial = repos.operational.orders(statuses=["PARTIAL"])
    assert [row.order_id for row in partial] == [1]
    summary = repos.operational.order_fill_summary(1)
    assert summary is not None
    assert summary.fill_count == 2
    assert summary.filled_quantity == 40
    assert float(summary.average_price) == pytest.approx(100.5)
    assert summary.first_received_at < summary.last_received_at


def test_trade_and_plan_lineage_reads(repos) -> None:
    trade = repos.operational.trade(1)
    assert trade is not None and trade.status == "OPEN"
    plan = repos.operational.trade_plan(1)
    assert plan is not None and plan.setup_observation_id == 1
    assert repos.operational.trade(trade.trade_id).trade_plan_id == plan.trade_plan_id


# --- control and research metadata -------------------------------------------


def test_feature_and_schema_control_reads(repos) -> None:
    definitions = repos.control.feature_definitions()
    assert [row.code for row in definitions] == ["ATR", "RRS_RAW"]
    versions = repos.control.feature_versions()
    assert {row.feature_code for row in versions} == {"ATR", "RRS_RAW"}
    schemas = repos.control.feature_schema_versions(schema_code="ER_FEATURE_SET")
    assert len(schemas) == 1
    members = repos.control.feature_schema_members(schemas[0].feature_schema_version_id)
    assert [row.feature_code for row in members] == ["ATR", "RRS_RAW"]
    strategies = repos.control.strategy_versions(strategy_code="ER_RS_CONTINUATION_V1")
    assert strategies[0].version == 1
    assert strategies[0].lifecycle_state == "RESEARCH"


def test_market_observations_revisions_and_candles(repos) -> None:
    observations = repos.market.observations(
        1,
        datetime(2026, 9, 1, 4, 0, tzinfo=UTC),
        datetime(2026, 9, 1, 4, 20, tzinfo=UTC),
        timeframe_code="M5",
    )
    assert [row.market_observation_id for row in observations] == [1, 2, 3]
    assert repos.market.observation(3).quality_status == "DEGRADED"
    revision = repos.market.canonical_revision(1)
    assert revision is not None and revision.is_canonical is True
    quality = repos.market.quality_summary(
        1,
        datetime(2026, 9, 1, 4, 0, tzinfo=UTC),
        datetime(2026, 9, 1, 4, 20, tzinfo=UTC),
    )
    assert {row.quality_status: row.row_count for row in quality} == {
        "GOOD": 2,
        "DEGRADED": 1,
    }
    candles = repos.market.candles(
        1,
        datetime(2026, 9, 1, 3, 44, tzinfo=UTC),
        datetime(2026, 9, 1, 3, 48, tzinfo=UTC),
    )
    assert [row.candle_id for row in candles] == [1, 2]
    streamed = list(
        repos.market.iterate_candles(
            1,
            datetime(2026, 9, 1, 3, 44, tzinfo=UTC),
            datetime(2026, 9, 1, 3, 48, tzinfo=UTC),
        )
    )
    assert [row.candle_id for row in streamed] == [1, 2]


def test_dataset_outcome_pattern_and_experiment_reads(repos) -> None:
    versions = repos.research.dataset_versions(dataset_code="CANONICAL_M5")
    assert [row.status for row in versions] == ["COMMITTED", "BUILDING"]
    committed = versions[0]
    assert committed.checksum == "checksum-v1"
    assert committed.committed_at is not None
    inputs = repos.research.dataset_version_inputs(2)
    assert inputs[0].input_dataset_version_id == 1
    outcomes = repos.research.outcome_definitions(outcome_schema_version_id=1)
    assert outcomes[0].code == "return_5m"
    windows = repos.research.pattern_windows(instrument_id=1)
    assert windows[0].normalization_version == "er-norm-v1"
    runs = repos.research.experiment_runs(experiment_code="EXP_TEST")
    assert runs[0].status == "SUCCEEDED"
    backtests = repos.research.backtest_runs()
    assert backtests[0].engine_revision == "er-backtest-engine-v1"
    assert repos.research.training_runs()[0].algorithm == "logistic_regression"
    assert repos.research.model_candidates()[0].status == "CANDIDATE"


# --- instrumentation ---------------------------------------------------------


def test_query_instrumentation_records_duration_and_rows(repos) -> None:
    repos.reference.instruments()
    stat = repos.observer.stats[-1]
    assert stat.name == "reference.instruments"
    assert stat.row_count == 3
    assert stat.duration_seconds >= 0
    assert stat.streamed is False


def test_streaming_is_instrumented(repos) -> None:
    list(
        repos.market.iterate_candles(
            1,
            datetime(2026, 9, 1, 3, 44, tzinfo=UTC),
            datetime(2026, 9, 1, 3, 48, tzinfo=UTC),
        )
    )
    streamed = [stat for stat in repos.observer.stats if stat.streamed]
    assert streamed and streamed[-1].row_count == 2
