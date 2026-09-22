"""Read-only adapters for the ``operational`` schema.

Python may read production observations but never mutates orders, fills, trade plans,
risk decisions, positions or deployment state. These queries are read-only; the
connection enforces it at the server.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

from .base import RepositoryBase, where_clause
from .models import (
    FillRow,
    ModelPredictionRow,
    OrderEventRow,
    OrderFillSummaryRow,
    OrderRow,
    PortfolioSnapshotRow,
    RiskAccountStateRow,
    RiskContextSnapshotRow,
    RiskDecisionReasonRow,
    RiskDecisionRow,
    SetupClassificationRow,
    SetupObservationRow,
    TradeOutcomeRow,
    TradePlanEventRow,
    TradePlanRow,
    TradeRow,
)

_SETUP_COLUMNS = (
    "setup_observation_id, setup_observation_key, tenant_id, broker_account_id, "
    "market_observation_id, strategy_version_id, instrument_id, observed_at, direction, "
    "setup_status, entry_pattern, setup_quality, proposed_entry_low, proposed_entry_high, "
    "structural_invalidation, target_reference, structural_rr, market_regime, sector_id, "
    "correlation_id, explanation, setup_instance_id, initialization_reason"
)
_TRADE_COLUMNS = (
    "trade_id, trade_key, trade_plan_id, tenant_id, broker_account_id, instrument_id, "
    "strategy_version_id, direction, status, opened_at, closed_at, planned_quantity, "
    "filled_entry_quantity, filled_exit_quantity, average_entry_price, average_exit_price, "
    "realized_pnl, exit_reason, correlation_id, created_at"
)
_ORDER_COLUMNS = (
    "order_id, order_key, client_order_reference, trade_id, trade_plan_id, tenant_id, "
    "broker_account_id, broker_id, instrument_id, side, order_role, order_type, "
    "product_type, time_in_force, requested_quantity, filled_quantity, requested_price, "
    "trigger_price, average_fill_price, broker_order_id, status, submitted_at, "
    "acknowledged_at, terminal_at, created_at"
)
_FILL_COLUMNS = (
    "fill_id, fill_key, order_id, trade_id, tenant_id, broker_account_id, broker_id, "
    "instrument_id, side, quantity, price, gross_value, fees, broker_fill_id, "
    "broker_trade_id, exchange_trade_id, exchange_timestamp, broker_timestamp, "
    "received_timestamp, created_at"
)
_TRADE_PLAN_COLUMNS = (
    "trade_plan_id, trade_plan_key, risk_decision_id, setup_observation_id, tenant_id, "
    "broker_account_id, strategy_version_id, instrument_id, market_observation_id, "
    "direction, entry_pattern, entry_method, target_method, planned_quantity, entry_low, "
    "entry_high, structural_invalidation, protective_stop, target_reference, "
    "expected_reward_risk, planned_risk, planned_notional, expected_cost, "
    "expected_slippage, invalidation_reason, correlation_id, created_at, valid_from, "
    "expires_at, entry_cutoff_at, entry_trigger_price, no_chase_price, "
    "feature_schema_version, market_regime, plan_policy_reference"
)
_RISK_DECISION_COLUMNS = (
    "risk_decision_id, decision_key, setup_observation_id, model_prediction_id, "
    "risk_context_snapshot_id, tenant_id, broker_account_id, strategy_version_id, "
    "risk_policy_version_id, instrument_id, decision_at, decision, requested_quantity, "
    "approved_quantity, requested_risk, approved_risk, approved_notional, "
    "effective_loss_per_unit, stress_loss_per_unit, binding_constraints, correlation_id"
)
_CLASSIFICATION_LATERAL = (
    "SELECT so.setup_observation_id, so.instrument_id, so.observed_at, so.direction, "
    "so.setup_status, so.setup_instance_id, so.strategy_version_id, "
    "rd.risk_decision_id, rd.decision AS risk_decision, "
    "rdr.reason_code AS risk_primary_reason_code, "
    "tp.trade_plan_id, t.trade_id, t.status AS trade_status "
    "FROM operational.setup_observation so "
    "LEFT JOIN LATERAL ("
    "SELECT risk_decision_id, decision FROM operational.risk_decision "
    "WHERE setup_observation_id = so.setup_observation_id "
    "ORDER BY decision_at DESC, risk_decision_id DESC LIMIT 1"
    ") rd ON TRUE "
    "LEFT JOIN LATERAL ("
    "SELECT reason_code FROM operational.risk_decision_reason r "
    "WHERE r.risk_decision_id = rd.risk_decision_id ORDER BY r.ordinal LIMIT 1"
    ") rdr ON TRUE "
    "LEFT JOIN LATERAL ("
    "SELECT trade_plan_id FROM operational.trade_plan "
    "WHERE setup_observation_id = so.setup_observation_id "
    "ORDER BY created_at DESC, trade_plan_id DESC LIMIT 1"
    ") tp ON TRUE "
    "LEFT JOIN LATERAL ("
    "SELECT trade_id, status FROM operational.trade "
    "WHERE trade_plan_id = tp.trade_plan_id "
    "ORDER BY created_at DESC, trade_id DESC LIMIT 1"
    ") t ON TRUE"
)


class OperationalRepository(RepositoryBase):
    """Read-only setup, execution, plan and risk reads."""

    # --- setup observations --------------------------------------------------

    def setup_observations(
        self,
        *,
        instrument_id: int | None = None,
        strategy_version_id: int | None = None,
        from_timestamp: datetime | None = None,
        to_timestamp: datetime | None = None,
        states: list[str] | None = None,
        limit: int | None = None,
    ) -> list[SetupObservationRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if strategy_version_id is not None:
            conditions.append("strategy_version_id = %s")
            params.append(strategy_version_id)
        if from_timestamp is not None:
            conditions.append("observed_at >= %s")
            params.append(from_timestamp)
        if to_timestamp is not None:
            conditions.append("observed_at < %s")
            params.append(to_timestamp)
        if states:
            conditions.append("setup_status = ANY(%s)")
            params.append(list(states))
        sql = (
            f"SELECT {_SETUP_COLUMNS} FROM operational.setup_observation"
            + where_clause(conditions)
            + " ORDER BY observed_at, setup_observation_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(SetupObservationRow, "operational.setup_observations", sql, params)

    def setup_observation(self, setup_observation_id: int) -> SetupObservationRow | None:
        return self._one(
            SetupObservationRow,
            "operational.setup_observation",
            f"SELECT {_SETUP_COLUMNS} FROM operational.setup_observation "
            "WHERE setup_observation_id = %s",
            [setup_observation_id],
        )

    def setup_classifications(
        self,
        *,
        instrument_id: int | None = None,
        states: list[str] | None = None,
        limit: int | None = None,
    ) -> list[SetupClassificationRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_id is not None:
            conditions.append("so.instrument_id = %s")
            params.append(instrument_id)
        if states:
            conditions.append("so.setup_status = ANY(%s)")
            params.append(list(states))
        sql = (
            _CLASSIFICATION_LATERAL
            + where_clause(conditions)
            + " ORDER BY so.observed_at, so.setup_observation_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(
            SetupClassificationRow,
            "operational.setup_classifications",
            sql,
            params,
        )

    def classify_setup(self, setup_observation_id: int) -> SetupClassificationRow | None:
        sql = _CLASSIFICATION_LATERAL + " WHERE so.setup_observation_id = %s"
        return self._one(
            SetupClassificationRow,
            "operational.classify_setup",
            sql,
            [setup_observation_id],
        )

    # --- trades --------------------------------------------------------------

    def trades(
        self,
        *,
        instrument_id: int | None = None,
        trade_plan_id: int | None = None,
        statuses: list[str] | None = None,
        limit: int | None = None,
    ) -> list[TradeRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if trade_plan_id is not None:
            conditions.append("trade_plan_id = %s")
            params.append(trade_plan_id)
        if statuses:
            conditions.append("status = ANY(%s)")
            params.append(list(statuses))
        sql = (
            f"SELECT {_TRADE_COLUMNS} FROM operational.trade"
            + where_clause(conditions)
            + " ORDER BY created_at, trade_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(TradeRow, "operational.trades", sql, params)

    def trade(self, trade_id: int) -> TradeRow | None:
        return self._one(
            TradeRow,
            "operational.trade",
            f"SELECT {_TRADE_COLUMNS} FROM operational.trade WHERE trade_id = %s",
            [trade_id],
        )

    # --- orders and fills ----------------------------------------------------

    def orders(
        self,
        *,
        trade_id: int | None = None,
        instrument_id: int | None = None,
        statuses: list[str] | None = None,
        limit: int | None = None,
    ) -> list[OrderRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if trade_id is not None:
            conditions.append("trade_id = %s")
            params.append(trade_id)
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if statuses:
            conditions.append("status = ANY(%s)")
            params.append(list(statuses))
        sql = (
            f"SELECT {_ORDER_COLUMNS} FROM operational.order_record"
            + where_clause(conditions)
            + " ORDER BY created_at, order_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(OrderRow, "operational.orders", sql, params)

    def order(self, order_id: int) -> OrderRow | None:
        return self._one(
            OrderRow,
            "operational.order",
            f"SELECT {_ORDER_COLUMNS} FROM operational.order_record WHERE order_id = %s",
            [order_id],
        )

    def order_events(self, order_id: int) -> list[OrderEventRow]:
        sql = (
            "SELECT order_event_id, order_id, event_type, event_timestamp, "
            "broker_timestamp, sequence_no, status_after, payload "
            "FROM operational.order_event WHERE order_id = %s "
            "ORDER BY event_timestamp, order_event_id"
        )
        return self._many(OrderEventRow, "operational.order_events", sql, [order_id])

    def fills(
        self,
        *,
        order_id: int | None = None,
        trade_id: int | None = None,
        from_timestamp: datetime | None = None,
        to_timestamp: datetime | None = None,
    ) -> list[FillRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if order_id is not None:
            conditions.append("order_id = %s")
            params.append(order_id)
        if trade_id is not None:
            conditions.append("trade_id = %s")
            params.append(trade_id)
        if from_timestamp is not None:
            conditions.append("received_timestamp >= %s")
            params.append(from_timestamp)
        if to_timestamp is not None:
            conditions.append("received_timestamp < %s")
            params.append(to_timestamp)
        sql = (
            f"SELECT {_FILL_COLUMNS} FROM operational.fill"
            + where_clause(conditions)
            + " ORDER BY received_timestamp, fill_id"
        )
        return self._many(FillRow, "operational.fills", sql, params)

    def order_fill_summary(self, order_id: int) -> OrderFillSummaryRow | None:
        sql = (
            "SELECT order_id, count(*)::int AS fill_count, sum(quantity)::bigint "
            "AS filled_quantity, "
            "CASE WHEN sum(quantity) > 0 THEN sum(quantity * price) / sum(quantity) END "
            "AS average_price, "
            "min(received_timestamp) AS first_received_at, "
            "max(received_timestamp) AS last_received_at "
            "FROM operational.fill WHERE order_id = %s GROUP BY order_id"
        )
        return self._one(OrderFillSummaryRow, "operational.order_fill_summary", sql, [order_id])

    # --- trade plans ---------------------------------------------------------

    def trade_plans(
        self,
        *,
        setup_observation_id: int | None = None,
        instrument_id: int | None = None,
        limit: int | None = None,
    ) -> list[TradePlanRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if setup_observation_id is not None:
            conditions.append("setup_observation_id = %s")
            params.append(setup_observation_id)
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        sql = (
            f"SELECT {_TRADE_PLAN_COLUMNS} FROM operational.trade_plan"
            + where_clause(conditions)
            + " ORDER BY created_at, trade_plan_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(TradePlanRow, "operational.trade_plans", sql, params)

    def trade_plan(self, trade_plan_id: int) -> TradePlanRow | None:
        return self._one(
            TradePlanRow,
            "operational.trade_plan",
            f"SELECT {_TRADE_PLAN_COLUMNS} FROM operational.trade_plan WHERE trade_plan_id = %s",
            [trade_plan_id],
        )

    def trade_plan_events(self, trade_plan_id: int) -> list[TradePlanEventRow]:
        sql = (
            "SELECT trade_plan_event_id, event_key, trade_plan_id, event_type, "
            "occurred_at, reason, payload FROM operational.trade_plan_event "
            "WHERE trade_plan_id = %s "
            "ORDER BY occurred_at, trade_plan_event_id"
        )
        return self._many(TradePlanEventRow, "operational.trade_plan_events", sql, [trade_plan_id])

    # --- model predictions ---------------------------------------------------

    def model_predictions(
        self,
        *,
        setup_observation_id: int | None = None,
        model_version_id: int | None = None,
    ) -> list[ModelPredictionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if setup_observation_id is not None:
            conditions.append("setup_observation_id = %s")
            params.append(setup_observation_id)
        if model_version_id is not None:
            conditions.append("model_version_id = %s")
            params.append(model_version_id)
        sql = (
            "SELECT model_prediction_id, prediction_key, setup_observation_id, "
            "model_version_id, predicted_at, probability_target_before_stop, expected_r, "
            "expected_mfe_r, expected_mae_r, expected_holding_seconds, confidence, "
            "output_payload FROM operational.model_prediction"
            + where_clause(conditions)
            + " ORDER BY predicted_at, model_prediction_id"
        )
        return self._many(ModelPredictionRow, "operational.model_predictions", sql, params)

    # --- risk ----------------------------------------------------------------

    def risk_decisions(
        self,
        *,
        setup_observation_id: int | None = None,
        instrument_id: int | None = None,
        decisions: list[str] | None = None,
        from_timestamp: datetime | None = None,
        to_timestamp: datetime | None = None,
    ) -> list[RiskDecisionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if setup_observation_id is not None:
            conditions.append("setup_observation_id = %s")
            params.append(setup_observation_id)
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if decisions:
            conditions.append("decision = ANY(%s)")
            params.append(list(decisions))
        if from_timestamp is not None:
            conditions.append("decision_at >= %s")
            params.append(from_timestamp)
        if to_timestamp is not None:
            conditions.append("decision_at < %s")
            params.append(to_timestamp)
        sql = (
            f"SELECT {_RISK_DECISION_COLUMNS} FROM operational.risk_decision"
            + where_clause(conditions)
            + " ORDER BY decision_at, risk_decision_id"
        )
        return self._many(RiskDecisionRow, "operational.risk_decisions", sql, params)

    def risk_decision(self, risk_decision_id: int) -> RiskDecisionRow | None:
        return self._one(
            RiskDecisionRow,
            "operational.risk_decision",
            f"SELECT {_RISK_DECISION_COLUMNS} FROM operational.risk_decision "
            "WHERE risk_decision_id = %s",
            [risk_decision_id],
        )

    def risk_decision_reasons(self, risk_decision_id: int) -> list[RiskDecisionReasonRow]:
        sql = (
            "SELECT risk_decision_reason_id, risk_decision_id, ordinal, reason_code, "
            "reason_type, before_value, after_value, details "
            "FROM operational.risk_decision_reason WHERE risk_decision_id = %s "
            "ORDER BY ordinal"
        )
        return self._many(
            RiskDecisionReasonRow,
            "operational.risk_decision_reasons",
            sql,
            [risk_decision_id],
        )

    def risk_context_snapshots(
        self,
        *,
        broker_account_id: int | None = None,
        from_date: date | None = None,
        to_date: date | None = None,
    ) -> list[RiskContextSnapshotRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if broker_account_id is not None:
            conditions.append("broker_account_id = %s")
            params.append(broker_account_id)
        if from_date is not None:
            conditions.append("trading_date >= %s")
            params.append(from_date)
        if to_date is not None:
            conditions.append("trading_date <= %s")
            params.append(to_date)
        sql = (
            "SELECT risk_context_snapshot_id, risk_context_key, tenant_id, "
            "broker_account_id, trading_date, captured_at, risk_policy_version_id, "
            "portfolio_snapshot_id, risk_reference_equity, current_equity, session_pnl, "
            "session_drawdown, risk_state, broker_health, data_health, "
            "reconciliation_state FROM operational.risk_context_snapshot"
            + where_clause(conditions)
            + " ORDER BY captured_at, risk_context_snapshot_id"
        )
        return self._many(RiskContextSnapshotRow, "operational.risk_context_snapshots", sql, params)

    def risk_account_states(
        self,
        *,
        broker_account_id: int | None = None,
        from_date: date | None = None,
        to_date: date | None = None,
    ) -> list[RiskAccountStateRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if broker_account_id is not None:
            conditions.append("broker_account_id = %s")
            params.append(broker_account_id)
        if from_date is not None:
            conditions.append("trading_date >= %s")
            params.append(from_date)
        if to_date is not None:
            conditions.append("trading_date <= %s")
            params.append(to_date)
        sql = (
            "SELECT risk_account_state_id, broker_account_id, trading_date, "
            "risk_reference_equity, current_net_liquidation_value, reserved_risk, "
            "open_risk, session_drawdown, risk_state, state_version, updated_at "
            "FROM operational.risk_account_state"
            + where_clause(conditions)
            + " ORDER BY trading_date, risk_account_state_id"
        )
        return self._many(RiskAccountStateRow, "operational.risk_account_states", sql, params)

    # --- portfolio / outcomes ------------------------------------------------

    def portfolio_snapshots(
        self,
        *,
        broker_account_id: int | None = None,
        from_timestamp: datetime | None = None,
        to_timestamp: datetime | None = None,
    ) -> list[PortfolioSnapshotRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if broker_account_id is not None:
            conditions.append("broker_account_id = %s")
            params.append(broker_account_id)
        if from_timestamp is not None:
            conditions.append("snapshot_at >= %s")
            params.append(from_timestamp)
        if to_timestamp is not None:
            conditions.append("snapshot_at < %s")
            params.append(to_timestamp)
        sql = (
            "SELECT portfolio_snapshot_id, snapshot_key, tenant_id, broker_account_id, "
            "snapshot_at, trading_date, net_liquidation_value, available_cash, "
            "buying_power, margin_used, gross_exposure, net_exposure, open_risk, "
            "realized_session_pnl, unrealized_pnl FROM operational.portfolio_snapshot"
            + where_clause(conditions)
            + " ORDER BY snapshot_at, portfolio_snapshot_id"
        )
        return self._many(PortfolioSnapshotRow, "operational.portfolio_snapshots", sql, params)

    def trade_outcomes(
        self, *, trade_id: int | None = None, instrument_id: int | None = None
    ) -> list[TradeOutcomeRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if trade_id is not None:
            conditions.append("trade_id = %s")
            params.append(trade_id)
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        sql = (
            "SELECT trade_outcome_id, outcome_key, trade_id, broker_account_id, "
            "instrument_id, maximum_adverse_excursion, maximum_favourable_excursion, "
            "maximum_adverse_excursion_r, maximum_favourable_excursion_r, r_multiple, "
            "planned_loss, actual_loss, holding_seconds, label_state "
            "FROM operational.trade_outcome"
            + where_clause(conditions)
            + " ORDER BY created_at, trade_outcome_id"
        )
        return self._many(TradeOutcomeRow, "operational.trade_outcomes", sql, params)
