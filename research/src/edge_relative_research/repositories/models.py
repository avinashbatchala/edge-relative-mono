"""Immutable, typed read rows returned by the repository adapters.

These are transport rows, not the canonical research contracts. A later stage maps them
into ``edge_relative_research.contracts``. Field names match the SQL aliases selected by
each repository, and every model is frozen.
"""

from __future__ import annotations

import dataclasses
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from typing import Any, TypeVar
from uuid import UUID

T = TypeVar("T")


def from_row(model: type[T], row: Mapping[str, Any]) -> T:
    """Project a mapping onto a frozen row dataclass, failing on a missing column."""

    values: dict[str, Any] = {}
    for field in dataclasses.fields(model):  # type: ignore[arg-type]
        try:
            values[field.name] = row[field.name]
        except KeyError as exc:
            raise KeyError(
                f"row is missing column {field.name!r} required by {model.__name__}"
            ) from exc
    return model(**values)  # type: ignore[call-arg]


# --- reference ---------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class InstrumentRow:
    instrument_id: int
    instrument_key: UUID
    exchange_id: int
    instrument_type: str
    segment: str
    canonical_symbol: str
    display_name: str | None
    currency_code: str
    tick_size: Decimal
    lot_size: int
    trading_status: str
    listed_from: date | None
    listed_to: date | None


@dataclass(frozen=True, slots=True)
class InstrumentIdentifierRow:
    instrument_identifier_id: int
    instrument_id: int
    exchange_id: int | None
    identifier_type: str
    identifier_value: str
    valid_from: date
    valid_to: date | None


@dataclass(frozen=True, slots=True)
class SectorRow:
    sector_id: int
    code: str
    name: str
    active: bool


@dataclass(frozen=True, slots=True)
class SectorMappingRow:
    instrument_id: int
    sector_id: int
    sector_code: str
    sector_name: str
    valid_from: date
    valid_to: date | None
    source: str | None


@dataclass(frozen=True, slots=True)
class BenchmarkRow:
    benchmark_id: int
    benchmark_key: UUID
    exchange_id: int | None
    instrument_id: int | None
    code: str
    name: str
    benchmark_type: str
    active: bool


@dataclass(frozen=True, slots=True)
class BenchmarkMembershipRow:
    benchmark_id: int
    benchmark_code: str
    instrument_id: int
    weight: float | None
    valid_from: date
    valid_to: date | None


@dataclass(frozen=True, slots=True)
class TradingCalendarDayRow:
    trading_calendar_day_id: int
    exchange_id: int
    trading_date: date
    day_type: str
    notes: str | None


@dataclass(frozen=True, slots=True)
class TradingSessionRow:
    trading_session_id: int
    trading_calendar_day_id: int
    trading_date: date
    exchange_id: int
    session_type: str
    opens_at: datetime
    closes_at: datetime


@dataclass(frozen=True, slots=True)
class CorporateActionRow:
    corporate_action_id: int
    instrument_id: int
    action_type: str
    ex_date: date
    record_date: date | None
    effective_date: date | None
    ratio_numerator: Decimal | None
    ratio_denominator: Decimal | None
    cash_amount: Decimal | None
    currency_code: str | None
    source_reference: str | None


@dataclass(frozen=True, slots=True)
class CorporateActionFactorRow:
    corporate_action_factor_id: int
    corporate_action_id: int
    instrument_id: int
    factor_version: int
    price_factor: Decimal
    quantity_factor: Decimal
    definition: str
    available_at: datetime


@dataclass(frozen=True, slots=True)
class UniverseRow:
    universe_id: int
    universe_key: UUID
    code: str
    name: str
    active: bool


@dataclass(frozen=True, slots=True)
class UniverseMembershipRow:
    universe_id: int
    universe_code: str
    instrument_id: int
    valid_from: date
    valid_to: date | None


# --- control -----------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class FeatureDefinitionRow:
    feature_definition_id: int
    feature_key: UUID
    code: str
    name: str
    description: str | None
    value_type: str


@dataclass(frozen=True, slots=True)
class FeatureVersionRow:
    feature_version_id: int
    feature_definition_id: int
    feature_code: str
    version: int
    calculation_version: str
    parameters: dict[str, Any]
    implementation_reference: str | None
    code_version: str | None


@dataclass(frozen=True, slots=True)
class FeatureSchemaVersionRow:
    feature_schema_version_id: int
    feature_schema_id: int
    schema_code: str
    version: int
    schema_hash: str | None


@dataclass(frozen=True, slots=True)
class FeatureSchemaMemberRow:
    feature_schema_version_id: int
    feature_version_id: int
    feature_code: str
    ordinal: int
    alias: str | None


@dataclass(frozen=True, slots=True)
class StrategyRow:
    strategy_id: int
    strategy_key: UUID
    code: str
    name: str
    setup_family: str | None
    status: str


@dataclass(frozen=True, slots=True)
class StrategyVersionRow:
    strategy_version_id: int
    strategy_id: int
    strategy_code: str
    version: int
    lifecycle_state: str
    primary_timeframe_id: int | None
    feature_schema_version_id: int | None
    parameters: dict[str, Any]
    code_version: str | None


@dataclass(frozen=True, slots=True)
class RiskPolicyVersionRow:
    risk_policy_version_id: int
    risk_policy_id: int
    policy_code: str
    version: int
    lifecycle_state: str
    parameters: dict[str, Any]
    code_version: str | None


# --- market ------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class MarketObservationRow:
    market_observation_id: int
    observation_key: UUID
    instrument_id: int
    timeframe_id: int
    timeframe_code: str
    bar_close_timestamp: datetime
    market_data_source_id: int | None
    quality_status: str
    storage_uri: str | None


@dataclass(frozen=True, slots=True)
class MarketObservationRevisionRow:
    market_observation_revision_id: int
    market_observation_id: int
    revision_no: int
    quality_status: str
    is_canonical: bool
    reason: str | None
    corrected_payload: dict[str, Any]
    created_at: datetime


@dataclass(frozen=True, slots=True)
class MarketDataIncidentRow:
    market_data_incident_id: int
    incident_key: UUID
    market_data_source_id: int | None
    instrument_id: int | None
    incident_type: str
    severity: str
    detected_at: datetime
    resolved_at: datetime | None
    details: dict[str, Any]


@dataclass(frozen=True, slots=True)
class QualityCountRow:
    quality_status: str
    row_count: int


@dataclass(frozen=True, slots=True)
class CandleRow:
    candle_id: int
    instrument_id: int
    timeframe_id: int
    open_time: datetime
    close_time: datetime
    open: Decimal | None
    high: Decimal
    low: Decimal
    close: Decimal
    volume: int
    vwap: Decimal | None
    is_complete: bool
    quality_state: str
    candle_definition_version: str
    source_revision: str
    revision_no: int
    is_current: bool


@dataclass(frozen=True, slots=True)
class CandleCoverageRow:
    candle_coverage_id: int
    instrument_id: int
    timeframe_id: int
    market_data_source_id: int
    chunk_start: datetime
    chunk_end: datetime
    status: str
    candle_count: int
    attempts: int
    last_error: str | None
    last_synced_at: datetime | None


@dataclass(frozen=True, slots=True)
class IngestionRunRow:
    ingestion_run_id: int
    run_key: UUID
    instrument_id: int
    timeframe_id: int
    market_data_source_id: int
    requested_from: datetime
    requested_to: datetime
    status: str
    total_chunks: int
    completed_chunks: int
    failed_chunks: int
    candles_written: int
    last_error: str | None
    created_at: datetime
    completed_at: datetime | None


# --- operational (read-only) -------------------------------------------------


@dataclass(frozen=True, slots=True)
class SetupObservationRow:
    setup_observation_id: int
    setup_observation_key: UUID
    tenant_id: int
    broker_account_id: int | None
    market_observation_id: int
    strategy_version_id: int
    instrument_id: int
    observed_at: datetime
    direction: str
    setup_status: str
    entry_pattern: str | None
    setup_quality: float | None
    proposed_entry_low: Decimal | None
    proposed_entry_high: Decimal | None
    structural_invalidation: Decimal | None
    target_reference: Decimal | None
    structural_rr: float | None
    market_regime: str | None
    sector_id: int | None
    correlation_id: UUID | None
    explanation: dict[str, Any]
    setup_instance_id: UUID | None
    initialization_reason: str | None


@dataclass(frozen=True, slots=True)
class TradeRow:
    trade_id: int
    trade_key: UUID
    trade_plan_id: int
    tenant_id: int
    broker_account_id: int
    instrument_id: int
    strategy_version_id: int
    direction: str
    status: str
    opened_at: datetime | None
    closed_at: datetime | None
    planned_quantity: int
    filled_entry_quantity: int
    filled_exit_quantity: int
    average_entry_price: Decimal | None
    average_exit_price: Decimal | None
    realized_pnl: Decimal
    exit_reason: str | None
    correlation_id: UUID | None
    created_at: datetime


@dataclass(frozen=True, slots=True)
class OrderRow:
    order_id: int
    order_key: UUID
    client_order_reference: UUID
    trade_id: int
    trade_plan_id: int
    tenant_id: int
    broker_account_id: int
    broker_id: int
    instrument_id: int
    side: str
    order_role: str
    order_type: str
    product_type: str | None
    time_in_force: str | None
    requested_quantity: int
    filled_quantity: int
    requested_price: Decimal | None
    trigger_price: Decimal | None
    average_fill_price: Decimal | None
    broker_order_id: str | None
    status: str
    submitted_at: datetime | None
    acknowledged_at: datetime | None
    terminal_at: datetime | None
    created_at: datetime


@dataclass(frozen=True, slots=True)
class OrderEventRow:
    order_event_id: int
    order_id: int
    event_type: str
    event_timestamp: datetime
    broker_timestamp: datetime | None
    sequence_no: int | None
    status_after: str | None
    payload: dict[str, Any]


@dataclass(frozen=True, slots=True)
class FillRow:
    fill_id: int
    fill_key: UUID
    order_id: int
    trade_id: int
    tenant_id: int
    broker_account_id: int
    broker_id: int
    instrument_id: int
    side: str
    quantity: int
    price: Decimal
    gross_value: Decimal | None
    fees: Decimal | None
    broker_fill_id: str | None
    broker_trade_id: str | None
    exchange_trade_id: str | None
    exchange_timestamp: datetime | None
    broker_timestamp: datetime | None
    received_timestamp: datetime
    created_at: datetime


@dataclass(frozen=True, slots=True)
class OrderFillSummaryRow:
    order_id: int
    fill_count: int
    filled_quantity: int
    average_price: Decimal | None
    first_received_at: datetime | None
    last_received_at: datetime | None


@dataclass(frozen=True, slots=True)
class TradePlanRow:
    trade_plan_id: int
    trade_plan_key: UUID
    risk_decision_id: int
    setup_observation_id: int
    tenant_id: int
    broker_account_id: int
    strategy_version_id: int
    instrument_id: int
    market_observation_id: int
    direction: str
    entry_pattern: str | None
    entry_method: str | None
    target_method: str | None
    planned_quantity: int
    entry_low: Decimal
    entry_high: Decimal
    structural_invalidation: Decimal
    protective_stop: Decimal
    target_reference: Decimal | None
    expected_reward_risk: float | None
    planned_risk: Decimal
    planned_notional: Decimal
    expected_cost: Decimal | None
    expected_slippage: Decimal | None
    invalidation_reason: str | None
    correlation_id: UUID | None
    created_at: datetime
    valid_from: datetime | None
    expires_at: datetime | None
    entry_cutoff_at: datetime | None
    entry_trigger_price: Decimal | None
    no_chase_price: Decimal | None
    feature_schema_version: str | None
    market_regime: str | None
    plan_policy_reference: str | None


@dataclass(frozen=True, slots=True)
class TradePlanEventRow:
    trade_plan_event_id: int
    event_key: UUID
    trade_plan_id: int
    event_type: str
    occurred_at: datetime
    reason: str | None
    payload: dict[str, Any]


@dataclass(frozen=True, slots=True)
class ModelPredictionRow:
    model_prediction_id: int
    prediction_key: UUID
    setup_observation_id: int
    model_version_id: int
    predicted_at: datetime
    probability_target_before_stop: float | None
    expected_r: float | None
    expected_mfe_r: float | None
    expected_mae_r: float | None
    expected_holding_seconds: int | None
    confidence: float | None
    output_payload: dict[str, Any]


@dataclass(frozen=True, slots=True)
class RiskDecisionRow:
    risk_decision_id: int
    decision_key: UUID
    setup_observation_id: int
    model_prediction_id: int | None
    risk_context_snapshot_id: int
    tenant_id: int
    broker_account_id: int
    strategy_version_id: int
    risk_policy_version_id: int
    instrument_id: int
    decision_at: datetime
    decision: str
    requested_quantity: int
    approved_quantity: int
    requested_risk: Decimal
    approved_risk: Decimal
    approved_notional: Decimal
    effective_loss_per_unit: Decimal | None
    stress_loss_per_unit: Decimal | None
    binding_constraints: list[Any]
    correlation_id: UUID | None


@dataclass(frozen=True, slots=True)
class RiskDecisionReasonRow:
    risk_decision_reason_id: int
    risk_decision_id: int
    ordinal: int
    reason_code: str
    reason_type: str
    before_value: Decimal | None
    after_value: Decimal | None
    details: dict[str, Any]


@dataclass(frozen=True, slots=True)
class RiskContextSnapshotRow:
    risk_context_snapshot_id: int
    risk_context_key: UUID
    tenant_id: int
    broker_account_id: int
    trading_date: date
    captured_at: datetime
    risk_policy_version_id: int
    portfolio_snapshot_id: int | None
    risk_reference_equity: Decimal
    current_equity: Decimal
    session_pnl: Decimal
    session_drawdown: Decimal
    risk_state: str
    broker_health: str
    data_health: str
    reconciliation_state: str


@dataclass(frozen=True, slots=True)
class RiskAccountStateRow:
    risk_account_state_id: int
    broker_account_id: int
    trading_date: date
    risk_reference_equity: Decimal
    current_net_liquidation_value: Decimal
    reserved_risk: Decimal
    open_risk: Decimal
    session_drawdown: Decimal
    risk_state: str
    state_version: int
    updated_at: datetime


@dataclass(frozen=True, slots=True)
class PortfolioSnapshotRow:
    portfolio_snapshot_id: int
    snapshot_key: UUID
    tenant_id: int
    broker_account_id: int
    snapshot_at: datetime
    trading_date: date
    net_liquidation_value: Decimal
    available_cash: Decimal | None
    buying_power: Decimal | None
    margin_used: Decimal | None
    gross_exposure: Decimal
    net_exposure: Decimal
    open_risk: Decimal
    realized_session_pnl: Decimal
    unrealized_pnl: Decimal


@dataclass(frozen=True, slots=True)
class TradeOutcomeRow:
    trade_outcome_id: int
    outcome_key: UUID
    trade_id: int
    broker_account_id: int
    instrument_id: int
    maximum_adverse_excursion: Decimal | None
    maximum_favourable_excursion: Decimal | None
    maximum_adverse_excursion_r: float | None
    maximum_favourable_excursion_r: float | None
    r_multiple: float | None
    planned_loss: Decimal | None
    actual_loss: Decimal | None
    holding_seconds: int | None
    label_state: str


@dataclass(frozen=True, slots=True)
class SetupClassificationRow:
    setup_observation_id: int
    instrument_id: int
    observed_at: datetime
    direction: str
    setup_status: str
    setup_instance_id: UUID | None
    strategy_version_id: int
    risk_decision_id: int | None
    risk_decision: str | None
    risk_primary_reason_code: str | None
    trade_plan_id: int | None
    trade_id: int | None
    trade_status: str | None


# --- research ----------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class DatasetRow:
    dataset_id: int
    dataset_key: UUID
    code: str
    name: str
    dataset_type: str
    description: str | None


@dataclass(frozen=True, slots=True)
class DatasetVersionRow:
    dataset_version_id: int
    dataset_version_key: UUID
    dataset_id: int
    dataset_code: str
    version: int
    status: str
    feature_schema_version_id: int | None
    outcome_schema_version_id: int | None
    point_in_time_cutoff: datetime | None
    universe: str | None
    start_timestamp: datetime | None
    end_timestamp: datetime | None
    storage_uri: str
    partition_manifest_uri: str | None
    row_count: int | None
    checksum: str | None
    code_version: str | None
    build_parameters: dict[str, Any]
    failure: dict[str, Any]
    committed_at: datetime | None
    retired_at: datetime | None
    created_at: datetime


@dataclass(frozen=True, slots=True)
class DatasetVersionInputRow:
    dataset_version_id: int
    input_dataset_version_id: int
    input_role: str


@dataclass(frozen=True, slots=True)
class OutcomeSchemaVersionRow:
    outcome_schema_version_id: int
    outcome_schema_id: int
    schema_code: str
    version: int


@dataclass(frozen=True, slots=True)
class OutcomeDefinitionRow:
    outcome_definition_id: int
    outcome_schema_version_id: int
    code: str
    value_type: str
    horizon_seconds: int | None
    parameters: dict[str, Any]
    ordinal: int


@dataclass(frozen=True, slots=True)
class PatternSchemaVersionRow:
    pattern_schema_version_id: int
    pattern_schema_id: int
    schema_code: str
    version: int
    timeframe_id: int
    window_length: int
    feature_schema_version_id: int
    parameters: dict[str, Any]


@dataclass(frozen=True, slots=True)
class PatternWindowRow:
    pattern_window_metadata_id: int
    pattern_key: UUID
    pattern_schema_version_id: int
    dataset_version_id: int
    market_observation_id: int | None
    instrument_id: int
    sector_id: int | None
    timeframe_id: int
    anchor_timestamp: datetime
    market_regime: str | None
    sector_regime: str | None
    normalization_version: str | None
    minutes_since_open: int | None
    storage_uri: str
    storage_row_reference: str | None


@dataclass(frozen=True, slots=True)
class SimilarityIndexRow:
    similarity_index_metadata_id: int
    index_key: UUID
    pattern_schema_version_id: int
    dataset_version_id: int
    index_type: str
    status: str
    corpus_cutoff: datetime
    storage_uri: str
    checksum: str


@dataclass(frozen=True, slots=True)
class ExperimentRow:
    experiment_id: int
    experiment_key: UUID
    code: str
    hypothesis: str
    created_by: str | None


@dataclass(frozen=True, slots=True)
class ExperimentRunRow:
    experiment_run_id: int
    run_key: UUID
    experiment_id: int
    experiment_code: str
    status: str
    strategy_version_id: int | None
    dataset_version_id: int | None
    date_range_start: date | None
    date_range_end: date | None
    parameters: dict[str, Any]
    cost_model: dict[str, Any]
    result_summary: dict[str, Any]
    code_version: str | None
    started_at: datetime | None
    completed_at: datetime | None


@dataclass(frozen=True, slots=True)
class BacktestRunRow:
    backtest_run_id: int
    run_key: UUID
    experiment_run_id: int | None
    strategy_version_id: int
    risk_policy_version_id: int | None
    dataset_version_id: int
    status: str
    result_uri: str | None
    metrics: dict[str, Any]
    seed: int | None
    starting_capital: Decimal | None
    currency: str | None
    engine_revision: str | None


@dataclass(frozen=True, slots=True)
class TrainingRunRow:
    training_run_id: int
    run_key: UUID
    experiment_run_id: int | None
    dataset_version_id: int
    feature_schema_version_id: int
    status: str
    algorithm: str
    metrics: dict[str, Any]
    artifact_uri: str | None


@dataclass(frozen=True, slots=True)
class ModelCandidateRow:
    model_candidate_id: int
    candidate_key: UUID
    model_id: int | None
    training_run_id: int
    status: str
    artifact_uri: str
    metrics: dict[str, Any]
    promoted_model_version_id: int | None
