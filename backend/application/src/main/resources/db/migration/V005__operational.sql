-- Edge Relative
-- Flyway V005: authoritative operational trading schema

CREATE TABLE operational.tenant (
    tenant_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_key UUID NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_key UNIQUE (tenant_key),
    CONSTRAINT ck_tenant_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE TABLE operational.app_user (
    app_user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    external_subject TEXT,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_key UNIQUE (user_key),
    CONSTRAINT uq_app_user_external_subject UNIQUE (tenant_id, external_subject),
    CONSTRAINT ck_app_user_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE TABLE operational.broker_account (
    broker_account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    broker_account_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    app_user_id BIGINT REFERENCES operational.app_user(app_user_id) ON DELETE RESTRICT,
    broker_id BIGINT NOT NULL REFERENCES reference.broker(broker_id) ON DELETE RESTRICT,
    external_account_id TEXT NOT NULL,
    account_label TEXT,
    secret_reference TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_broker_account_key UNIQUE (broker_account_key),
    CONSTRAINT uq_broker_external_account UNIQUE (broker_id, external_account_id),
    CONSTRAINT ck_broker_account_external_nonblank CHECK (btrim(external_account_id) <> ''),
    CONSTRAINT ck_broker_account_secret_nonblank CHECK (btrim(secret_reference) <> ''),
    CONSTRAINT ck_broker_account_status CHECK (status IN ('ACTIVE','DISABLED','AUTH_FAILED','CLOSED'))
);

CREATE INDEX ix_broker_account_tenant
    ON operational.broker_account(tenant_id, status);

CREATE TABLE operational.watchlist (
    watchlist_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    watchlist_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    app_user_id BIGINT REFERENCES operational.app_user(app_user_id) ON DELETE RESTRICT,
    name TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_watchlist_key UNIQUE (watchlist_key),
    CONSTRAINT uq_watchlist_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_watchlist_name_nonblank CHECK (btrim(name) <> '')
);

-- DD-01 section 14: a single active 20-stock watchlist per operator.
CREATE UNIQUE INDEX uq_watchlist_one_active_per_owner
    ON operational.watchlist(tenant_id, COALESCE(app_user_id, 0))
    WHERE active;

CREATE TABLE operational.watchlist_item (
    watchlist_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    watchlist_id BIGINT NOT NULL REFERENCES operational.watchlist(watchlist_id) ON DELETE CASCADE,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    slot SMALLINT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    derivatives_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    strategy_eligibility JSONB NOT NULL DEFAULT '{}'::jsonb,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_watchlist_item_instrument UNIQUE (watchlist_id, instrument_id),
    CONSTRAINT uq_watchlist_item_slot UNIQUE (watchlist_id, slot),
    CONSTRAINT ck_watchlist_item_slot CHECK (slot BETWEEN 1 AND 20)
);

CREATE TABLE operational.strategy_deployment (
    strategy_deployment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deployment_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    trading_mode TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    validity TSTZRANGE GENERATED ALWAYS AS (tstzrange(valid_from, valid_to, '[)')) STORED,
    parameters_override JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_strategy_deployment_key UNIQUE (deployment_key),
    CONSTRAINT ck_strategy_deployment_mode CHECK (trading_mode IN ('RESEARCH','BACKTEST','OBSERVE','PAPER','ASSISTED_LIVE','GUARDED_AUTOPILOT','FULL_AUTOPILOT')),
    CONSTRAINT ck_strategy_deployment_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_strategy_deployment_no_overlap
        EXCLUDE USING gist (tenant_id WITH =, (COALESCE(broker_account_id, 0)) WITH =, strategy_version_id WITH =, validity WITH &&)
);

CREATE TABLE operational.risk_policy_assignment (
    risk_policy_assignment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    risk_policy_version_id BIGINT NOT NULL REFERENCES control.risk_policy_version(risk_policy_version_id) ON DELETE RESTRICT,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    validity TSTZRANGE GENERATED ALWAYS AS (tstzrange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_risk_policy_assignment_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_risk_policy_assignment_no_overlap
        EXCLUDE USING gist (broker_account_id WITH =, (COALESCE(strategy_version_id, 0)) WITH =, validity WITH &&)
);

CREATE INDEX ix_risk_policy_assignment_current
    ON operational.risk_policy_assignment(broker_account_id, strategy_version_id)
    WHERE valid_to IS NULL;

CREATE TABLE operational.model_deployment (
    model_deployment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deployment_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    model_version_id BIGINT NOT NULL REFERENCES control.model_version(model_version_id) ON DELETE RESTRICT,
    authority_level TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    validity TSTZRANGE GENERATED ALWAYS AS (tstzrange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_deployment_key UNIQUE (deployment_key),
    CONSTRAINT ck_model_deployment_authority CHECK (authority_level IN ('OBSERVER','RANKER','FILTER','RISK_REDUCER')),
    CONSTRAINT ck_model_deployment_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_model_deployment_no_overlap
        EXCLUDE USING gist (tenant_id WITH =, (COALESCE(broker_account_id, 0)) WITH =, strategy_version_id WITH =, validity WITH &&)
);

CREATE TABLE operational.portfolio_snapshot (
    portfolio_snapshot_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    snapshot_at TIMESTAMPTZ NOT NULL,
    trading_date DATE NOT NULL,
    net_liquidation_value NUMERIC(24,8) NOT NULL,
    available_cash NUMERIC(24,8),
    buying_power NUMERIC(24,8),
    margin_used NUMERIC(24,8),
    gross_exposure NUMERIC(24,8) NOT NULL DEFAULT 0,
    net_exposure NUMERIC(24,8) NOT NULL DEFAULT 0,
    open_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    stress_open_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    realized_session_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_portfolio_snapshot_key UNIQUE (snapshot_key),
    CONSTRAINT uq_portfolio_snapshot_account_time UNIQUE (broker_account_id, snapshot_at),
    CONSTRAINT ck_portfolio_snapshot_nonnegative CHECK (
        net_liquidation_value >= 0
        AND (available_cash IS NULL OR available_cash >= 0)
        AND (buying_power IS NULL OR buying_power >= 0)
        AND (margin_used IS NULL OR margin_used >= 0)
        AND gross_exposure >= 0
        AND open_risk >= 0
        AND stress_open_risk >= 0
    )
);

CREATE INDEX ix_portfolio_snapshot_account_date
    ON operational.portfolio_snapshot(broker_account_id, trading_date, snapshot_at DESC);

CREATE TABLE operational.portfolio_position_snapshot (
    portfolio_position_snapshot_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    portfolio_snapshot_id BIGINT NOT NULL REFERENCES operational.portfolio_snapshot(portfolio_snapshot_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL,
    average_price NUMERIC(20,8),
    market_price NUMERIC(20,8),
    notional NUMERIC(24,8),
    unrealized_pnl NUMERIC(24,8),
    open_risk NUMERIC(24,8),
    stress_risk NUMERIC(24,8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_portfolio_position_snapshot UNIQUE (portfolio_snapshot_id, instrument_id),
    CONSTRAINT ck_portfolio_position_snapshot_prices CHECK (
        (average_price IS NULL OR average_price >= 0)
        AND (market_price IS NULL OR market_price >= 0)
        AND (open_risk IS NULL OR open_risk >= 0)
        AND (stress_risk IS NULL OR stress_risk >= 0)
    )
);

CREATE TABLE operational.setup_observation (
    setup_observation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setup_observation_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    market_observation_id BIGINT NOT NULL REFERENCES market.market_observation(market_observation_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    observed_at TIMESTAMPTZ NOT NULL,
    direction TEXT NOT NULL,
    setup_status TEXT NOT NULL,
    entry_pattern TEXT,
    setup_quality DOUBLE PRECISION,
    proposed_entry_low NUMERIC(20,8),
    proposed_entry_high NUMERIC(20,8),
    structural_invalidation NUMERIC(20,8),
    target_reference NUMERIC(20,8),
    structural_rr DOUBLE PRECISION,
    market_regime TEXT,
    sector_id BIGINT REFERENCES reference.sector(sector_id) ON DELETE RESTRICT,
    correlation_id UUID,
    explanation JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_setup_observation_key UNIQUE (setup_observation_key),
    CONSTRAINT ck_setup_observation_direction CHECK (direction IN ('LONG','SHORT')),
    CONSTRAINT ck_setup_observation_status CHECK (setup_status IN ('NONE','WATCH','FORMING','NEAR_TRIGGER','VALID','INVALIDATED','EXPIRED','MISSED','REJECTED')),
    CONSTRAINT ck_setup_observation_entry_pattern CHECK (entry_pattern IS NULL OR entry_pattern IN ('M5_COMPRESSION_BREAKOUT','M5_TRENDLINE_BREAK','M5_3_8_CONFIRMATION','M5_HORIZONTAL_LEVEL_BREAK','M5_PULLBACK_RESUMPTION')),
    CONSTRAINT ck_setup_observation_quality CHECK (setup_quality IS NULL OR (setup_quality >= 0 AND setup_quality <= 1)),
    CONSTRAINT ck_setup_observation_entry_range CHECK (proposed_entry_low IS NULL OR proposed_entry_high IS NULL OR proposed_entry_high >= proposed_entry_low),
    CONSTRAINT ck_setup_observation_prices CHECK (
        (proposed_entry_low IS NULL OR proposed_entry_low >= 0)
        AND (proposed_entry_high IS NULL OR proposed_entry_high >= 0)
        AND (structural_invalidation IS NULL OR structural_invalidation >= 0)
        AND (target_reference IS NULL OR target_reference >= 0)
    )
);

CREATE INDEX ix_setup_observation_strategy_instrument_time
    ON operational.setup_observation(strategy_version_id, instrument_id, observed_at DESC);
CREATE INDEX ix_setup_observation_valid
    ON operational.setup_observation(tenant_id, observed_at DESC)
    WHERE setup_status = 'VALID';
CREATE INDEX ix_setup_observation_correlation
    ON operational.setup_observation(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE operational.model_prediction (
    model_prediction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prediction_key UUID NOT NULL,
    setup_observation_id BIGINT NOT NULL REFERENCES operational.setup_observation(setup_observation_id) ON DELETE RESTRICT,
    model_version_id BIGINT NOT NULL REFERENCES control.model_version(model_version_id) ON DELETE RESTRICT,
    predicted_at TIMESTAMPTZ NOT NULL,
    probability_target_before_stop DOUBLE PRECISION,
    expected_r DOUBLE PRECISION,
    expected_mfe_r DOUBLE PRECISION,
    expected_mae_r DOUBLE PRECISION,
    expected_holding_seconds BIGINT,
    confidence DOUBLE PRECISION,
    output_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_prediction_key UNIQUE (prediction_key),
    CONSTRAINT uq_model_prediction_setup_model UNIQUE (setup_observation_id, model_version_id),
    CONSTRAINT ck_model_prediction_probability CHECK (probability_target_before_stop IS NULL OR (probability_target_before_stop >= 0 AND probability_target_before_stop <= 1)),
    CONSTRAINT ck_model_prediction_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_model_prediction_holding CHECK (expected_holding_seconds IS NULL OR expected_holding_seconds >= 0)
);

CREATE TABLE operational.risk_account_state (
    risk_account_state_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    trading_date DATE NOT NULL,
    risk_reference_equity NUMERIC(24,8) NOT NULL,
    current_net_liquidation_value NUMERIC(24,8) NOT NULL,
    reserved_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    reserved_notional NUMERIC(24,8) NOT NULL DEFAULT 0,
    open_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    stress_open_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    gross_exposure NUMERIC(24,8) NOT NULL DEFAULT 0,
    net_exposure NUMERIC(24,8) NOT NULL DEFAULT 0,
    realized_session_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    session_drawdown NUMERIC(24,8) NOT NULL DEFAULT 0,
    risk_state TEXT NOT NULL DEFAULT 'NORMAL',
    state_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_account_state UNIQUE (broker_account_id, trading_date),
    CONSTRAINT ck_risk_account_state_nonnegative CHECK (
        risk_reference_equity >= 0
        AND current_net_liquidation_value >= 0
        AND reserved_risk >= 0
        AND reserved_notional >= 0
        AND open_risk >= 0
        AND stress_open_risk >= 0
        AND gross_exposure >= 0
        AND session_drawdown >= 0
        AND state_version >= 0
    ),
    CONSTRAINT ck_risk_account_state_state CHECK (risk_state IN ('NORMAL','REDUCED_1','REDUCED_2','NO_NEW_RISK','FLATTEN_ONLY','HALTED'))
);

CREATE TABLE operational.risk_context_snapshot (
    risk_context_snapshot_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_context_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    trading_date DATE NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    risk_policy_version_id BIGINT NOT NULL REFERENCES control.risk_policy_version(risk_policy_version_id) ON DELETE RESTRICT,
    portfolio_snapshot_id BIGINT REFERENCES operational.portfolio_snapshot(portfolio_snapshot_id) ON DELETE RESTRICT,
    risk_reference_equity NUMERIC(24,8) NOT NULL,
    current_equity NUMERIC(24,8) NOT NULL,
    available_cash NUMERIC(24,8),
    buying_power NUMERIC(24,8),
    margin_used NUMERIC(24,8),
    portfolio_open_risk NUMERIC(24,8) NOT NULL,
    portfolio_stress_risk NUMERIC(24,8) NOT NULL,
    gross_exposure NUMERIC(24,8) NOT NULL,
    net_exposure NUMERIC(24,8) NOT NULL,
    session_pnl NUMERIC(24,8) NOT NULL,
    session_drawdown NUMERIC(24,8) NOT NULL,
    risk_state TEXT NOT NULL,
    broker_health TEXT NOT NULL,
    data_health TEXT NOT NULL,
    reconciliation_state TEXT NOT NULL,
    limits_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    capacities_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_context_key UNIQUE (risk_context_key),
    CONSTRAINT ck_risk_context_nonnegative CHECK (
        risk_reference_equity >= 0
        AND current_equity >= 0
        AND (available_cash IS NULL OR available_cash >= 0)
        AND (buying_power IS NULL OR buying_power >= 0)
        AND (margin_used IS NULL OR margin_used >= 0)
        AND portfolio_open_risk >= 0
        AND portfolio_stress_risk >= 0
        AND gross_exposure >= 0
        AND session_drawdown >= 0
    ),
    CONSTRAINT ck_risk_context_state CHECK (risk_state IN ('NORMAL','REDUCED_1','REDUCED_2','NO_NEW_RISK','FLATTEN_ONLY','HALTED'))
);

CREATE TABLE operational.risk_decision (
    risk_decision_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    decision_key UUID NOT NULL,
    setup_observation_id BIGINT NOT NULL REFERENCES operational.setup_observation(setup_observation_id) ON DELETE RESTRICT,
    model_prediction_id BIGINT REFERENCES operational.model_prediction(model_prediction_id) ON DELETE RESTRICT,
    risk_context_snapshot_id BIGINT NOT NULL REFERENCES operational.risk_context_snapshot(risk_context_snapshot_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    risk_policy_version_id BIGINT NOT NULL REFERENCES control.risk_policy_version(risk_policy_version_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    decision_at TIMESTAMPTZ NOT NULL,
    decision TEXT NOT NULL,
    requested_quantity BIGINT NOT NULL,
    approved_quantity BIGINT NOT NULL DEFAULT 0,
    requested_risk NUMERIC(24,8) NOT NULL,
    approved_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    approved_notional NUMERIC(24,8) NOT NULL DEFAULT 0,
    effective_loss_per_unit NUMERIC(24,8),
    stress_loss_per_unit NUMERIC(24,8),
    binding_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_decision_key UNIQUE (decision_key),
    CONSTRAINT ck_risk_decision_type CHECK (decision IN ('APPROVE','REDUCE','REJECT','EXIT_REQUIRED','HALT_REQUIRED')),
    CONSTRAINT ck_risk_decision_quantities CHECK (requested_quantity > 0 AND approved_quantity >= 0 AND approved_quantity <= requested_quantity),
    CONSTRAINT ck_risk_decision_risk CHECK (requested_risk >= 0 AND approved_risk >= 0 AND approved_risk <= requested_risk AND approved_notional >= 0),
    CONSTRAINT ck_risk_decision_loss_units CHECK (
        (effective_loss_per_unit IS NULL OR effective_loss_per_unit > 0)
        AND (stress_loss_per_unit IS NULL OR stress_loss_per_unit > 0)
    ),
    -- APPROVE/REDUCE must authorize a positive quantity, risk and notional (the
    -- notional requirement keeps this consistent with the trade_plan trigger).
    -- REJECT/HALT authorize nothing. EXIT_REQUIRED may authorize zero or a
    -- specified safer quantity (DD-03 section 156).
    CONSTRAINT ck_risk_decision_approval_shape CHECK (
        (decision IN ('APPROVE','REDUCE') AND approved_quantity > 0 AND approved_risk > 0 AND approved_notional > 0)
        OR (decision IN ('REJECT','HALT_REQUIRED') AND approved_quantity = 0 AND approved_risk = 0 AND approved_notional = 0)
        OR (decision = 'EXIT_REQUIRED' AND approved_quantity >= 0 AND approved_risk >= 0 AND approved_notional >= 0)
    )
);

CREATE INDEX ix_risk_decision_account_session
    ON operational.risk_decision(broker_account_id, decision_at DESC);
CREATE INDEX ix_risk_decision_correlation
    ON operational.risk_decision(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE operational.risk_decision_reason (
    risk_decision_reason_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_decision_id BIGINT NOT NULL REFERENCES operational.risk_decision(risk_decision_id) ON DELETE RESTRICT,
    ordinal INTEGER NOT NULL,
    reason_code TEXT NOT NULL REFERENCES control.risk_reason_code(code) ON DELETE RESTRICT,
    reason_type TEXT NOT NULL,
    before_value NUMERIC(24,8),
    after_value NUMERIC(24,8),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_decision_reason_ordinal UNIQUE (risk_decision_id, ordinal),
    CONSTRAINT ck_risk_decision_reason_ordinal CHECK (ordinal > 0),
    CONSTRAINT ck_risk_decision_reason_code_nonblank CHECK (btrim(reason_code) <> ''),
    CONSTRAINT ck_risk_decision_reason_type CHECK (reason_type IN ('INFO','REDUCTION','REJECTION','EXIT','HALT'))
);

CREATE TABLE operational.trade_plan (
    trade_plan_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trade_plan_key UUID NOT NULL,
    risk_decision_id BIGINT NOT NULL REFERENCES operational.risk_decision(risk_decision_id) ON DELETE RESTRICT,
    setup_observation_id BIGINT NOT NULL REFERENCES operational.setup_observation(setup_observation_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    market_observation_id BIGINT NOT NULL REFERENCES market.market_observation(market_observation_id) ON DELETE RESTRICT,
    direction TEXT NOT NULL,
    entry_pattern TEXT,
    entry_method TEXT,
    target_method TEXT,
    planned_quantity BIGINT NOT NULL,
    entry_low NUMERIC(20,8) NOT NULL,
    entry_high NUMERIC(20,8) NOT NULL,
    structural_invalidation NUMERIC(20,8) NOT NULL,
    protective_stop NUMERIC(20,8) NOT NULL,
    target_reference NUMERIC(20,8),
    expected_reward_risk DOUBLE PRECISION,
    planned_risk NUMERIC(24,8) NOT NULL,
    planned_notional NUMERIC(24,8) NOT NULL,
    expected_cost NUMERIC(24,8),
    expected_slippage NUMERIC(24,8),
    invalidation_reason TEXT,
    correlation_id UUID,
    activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_plan_key UNIQUE (trade_plan_key),
    CONSTRAINT uq_trade_plan_risk_decision UNIQUE (risk_decision_id),
    CONSTRAINT ck_trade_plan_direction CHECK (direction IN ('LONG','SHORT')),
    CONSTRAINT ck_trade_plan_entry_pattern CHECK (entry_pattern IS NULL OR entry_pattern IN ('M5_COMPRESSION_BREAKOUT','M5_TRENDLINE_BREAK','M5_3_8_CONFIRMATION','M5_HORIZONTAL_LEVEL_BREAK','M5_PULLBACK_RESUMPTION')),
    CONSTRAINT ck_trade_plan_quantity CHECK (planned_quantity > 0),
    CONSTRAINT ck_trade_plan_entry CHECK (entry_low >= 0 AND entry_high >= entry_low),
    CONSTRAINT ck_trade_plan_prices CHECK (structural_invalidation >= 0 AND protective_stop >= 0 AND (target_reference IS NULL OR target_reference >= 0)),
    -- Structural invalidation bounds the protective stop; the stop must be on the
    -- losing side of the entry. Never move invalidation to inflate size (DD-03 section 5).
    CONSTRAINT ck_trade_plan_stop_side CHECK (
        (direction = 'LONG' AND protective_stop <= structural_invalidation AND protective_stop < entry_low)
        OR (direction = 'SHORT' AND protective_stop >= structural_invalidation AND protective_stop > entry_high)
    ),
    CONSTRAINT ck_trade_plan_target_side CHECK (
        target_reference IS NULL
        OR (direction = 'LONG' AND target_reference > entry_high)
        OR (direction = 'SHORT' AND target_reference < entry_low)
    ),
    CONSTRAINT ck_trade_plan_cost CHECK ((expected_cost IS NULL OR expected_cost >= 0) AND (expected_slippage IS NULL OR expected_slippage >= 0)),
    CONSTRAINT ck_trade_plan_risk CHECK (planned_risk > 0 AND planned_notional > 0)
);

CREATE INDEX ix_trade_plan_account_instrument
    ON operational.trade_plan(broker_account_id, instrument_id, created_at DESC);
CREATE INDEX ix_trade_plan_correlation
    ON operational.trade_plan(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE operational.risk_reservation (
    risk_reservation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_key UUID NOT NULL,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    trading_date DATE NOT NULL,
    risk_decision_id BIGINT NOT NULL REFERENCES operational.risk_decision(risk_decision_id) ON DELETE RESTRICT,
    trade_plan_id BIGINT NOT NULL REFERENCES operational.trade_plan(trade_plan_id) ON DELETE RESTRICT,
    status TEXT NOT NULL,
    reserved_quantity BIGINT NOT NULL,
    reserved_risk NUMERIC(24,8) NOT NULL,
    reserved_notional NUMERIC(24,8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    release_reason TEXT,
    CONSTRAINT uq_risk_reservation_key UNIQUE (reservation_key),
    CONSTRAINT uq_risk_reservation_trade_plan UNIQUE (trade_plan_id),
    CONSTRAINT ck_risk_reservation_status CHECK (status IN ('ACTIVE','PARTIALLY_CONSUMED','CONSUMED','RELEASED','CANCEL_PENDING')),
    CONSTRAINT ck_risk_reservation_values CHECK (reserved_quantity > 0 AND reserved_risk > 0 AND reserved_notional > 0),
    CONSTRAINT ck_risk_reservation_release CHECK ((status = 'RELEASED' AND released_at IS NOT NULL) OR status <> 'RELEASED')
);

CREATE INDEX ix_risk_reservation_active
    ON operational.risk_reservation(broker_account_id, trading_date)
    WHERE status IN ('ACTIVE','PARTIALLY_CONSUMED','CANCEL_PENDING');

CREATE TABLE operational.trade (
    trade_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trade_key UUID NOT NULL,
    trade_plan_id BIGINT NOT NULL REFERENCES operational.trade_plan(trade_plan_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    direction TEXT NOT NULL,
    status TEXT NOT NULL,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    planned_quantity BIGINT NOT NULL,
    filled_entry_quantity BIGINT NOT NULL DEFAULT 0,
    filled_exit_quantity BIGINT NOT NULL DEFAULT 0,
    average_entry_price NUMERIC(20,8),
    average_exit_price NUMERIC(20,8),
    realized_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    exit_reason TEXT,
    correlation_id UUID,
    record_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_key UNIQUE (trade_key),
    CONSTRAINT uq_trade_plan_trade UNIQUE (trade_plan_id),
    CONSTRAINT ck_trade_direction CHECK (direction IN ('LONG','SHORT')),
    CONSTRAINT ck_trade_status CHECK (status IN ('PLANNED','OPENING','OPEN','REDUCING','CLOSING','CLOSED','CANCELLED','ERROR')),
    CONSTRAINT ck_trade_quantities CHECK (planned_quantity > 0 AND filled_entry_quantity >= 0 AND filled_exit_quantity >= 0 AND filled_entry_quantity <= planned_quantity AND filled_exit_quantity <= filled_entry_quantity),
    CONSTRAINT ck_trade_prices CHECK ((average_entry_price IS NULL OR average_entry_price >= 0) AND (average_exit_price IS NULL OR average_exit_price >= 0)),
    CONSTRAINT ck_trade_exit_reason CHECK (exit_reason IS NULL OR exit_reason IN ('STOP','TARGET','TRAIL','TIME_STOP','REGIME_CHANGE','STRATEGY_INVALIDATION','RISK_EXIT','MANUAL_EXIT','EMERGENCY_EXIT','SESSION_FLATTEN','OTHER')),
    CONSTRAINT ck_trade_record_version CHECK (record_version >= 0),
    CONSTRAINT ck_trade_times CHECK (closed_at IS NULL OR opened_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX ix_trade_open_by_account
    ON operational.trade(broker_account_id, instrument_id, opened_at)
    WHERE status IN ('OPENING','OPEN','REDUCING','CLOSING');
CREATE INDEX ix_trade_correlation
    ON operational.trade(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE operational.order_record (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_key UUID NOT NULL,
    client_order_reference UUID NOT NULL,
    trade_id BIGINT NOT NULL REFERENCES operational.trade(trade_id) ON DELETE RESTRICT,
    trade_plan_id BIGINT NOT NULL REFERENCES operational.trade_plan(trade_plan_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    broker_id BIGINT NOT NULL REFERENCES reference.broker(broker_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    side TEXT NOT NULL,
    order_role TEXT NOT NULL,
    order_type TEXT NOT NULL,
    product_type TEXT,
    time_in_force TEXT,
    requested_quantity BIGINT NOT NULL,
    filled_quantity BIGINT NOT NULL DEFAULT 0,
    requested_price NUMERIC(20,8),
    trigger_price NUMERIC(20,8),
    average_fill_price NUMERIC(20,8),
    broker_order_id TEXT,
    status TEXT NOT NULL,
    submitted_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    terminal_at TIMESTAMPTZ,
    correlation_id UUID,
    record_version BIGINT NOT NULL DEFAULT 0,
    last_broker_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_order_key UNIQUE (order_key),
    CONSTRAINT uq_client_order_reference UNIQUE (client_order_reference),
    CONSTRAINT ck_order_side CHECK (side IN ('BUY','SELL')),
    CONSTRAINT ck_order_role CHECK (order_role IN ('ENTRY','EXIT','STOP','TARGET','REDUCE','EMERGENCY_EXIT')),
    CONSTRAINT ck_order_type CHECK (order_type IN ('MARKET','LIMIT','STOP_MARKET','STOP_LIMIT','OTHER')),
    CONSTRAINT ck_order_product_type CHECK (product_type IS NULL OR product_type IN ('MIS','CNC','NRML','OTHER')),
    CONSTRAINT ck_order_record_version CHECK (record_version >= 0),
    CONSTRAINT ck_order_tif CHECK (time_in_force IS NULL OR time_in_force IN ('DAY','IOC','GTT','GTC','FOK')),
    CONSTRAINT ck_order_quantity CHECK (requested_quantity > 0 AND filled_quantity >= 0 AND filled_quantity <= requested_quantity),
    CONSTRAINT ck_order_prices CHECK ((requested_price IS NULL OR requested_price >= 0) AND (trigger_price IS NULL OR trigger_price >= 0) AND (average_fill_price IS NULL OR average_fill_price >= 0)),
    CONSTRAINT ck_order_status CHECK (status IN ('CREATED','SUBMITTING','SUBMITTED','ACKNOWLEDGED','PARTIAL','FILLED','REJECTED','CANCEL_PENDING','CANCELLED','EXPIRED','UNKNOWN')),
    CONSTRAINT ck_order_times CHECK (
        (acknowledged_at IS NULL OR submitted_at IS NULL OR acknowledged_at >= submitted_at)
        AND (terminal_at IS NULL OR submitted_at IS NULL OR terminal_at >= submitted_at)
    )
);

CREATE UNIQUE INDEX uq_order_broker_order_id
    ON operational.order_record(broker_account_id, broker_order_id)
    WHERE broker_order_id IS NOT NULL;
CREATE INDEX ix_order_account_status
    ON operational.order_record(broker_account_id, status, created_at DESC);
CREATE INDEX ix_order_trade_plan
    ON operational.order_record(trade_id, trade_plan_id, created_at);
CREATE INDEX ix_order_instrument
    ON operational.order_record(instrument_id, created_at DESC);
CREATE INDEX ix_order_correlation
    ON operational.order_record(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE operational.order_event (
    order_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES operational.order_record(order_id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    broker_timestamp TIMESTAMPTZ,
    sequence_no BIGINT,
    status_after TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_order_event_sequence UNIQUE (order_id, sequence_no),
    CONSTRAINT ck_order_event_type_nonblank CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_order_event_type CHECK (event_type IN ('CREATED','SUBMITTED','ACKNOWLEDGED','PARTIAL_FILL','FILLED','REJECTED','CANCELLED','EXPIRED','MODIFIED','CANCEL_REJECTED','UNKNOWN','OTHER')),
    CONSTRAINT ck_order_event_sequence CHECK (sequence_no IS NULL OR sequence_no >= 0)
);

CREATE INDEX ix_order_event_order_time
    ON operational.order_event(order_id, event_timestamp);

CREATE TABLE operational.fill (
    fill_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fill_key UUID NOT NULL,
    order_id BIGINT NOT NULL REFERENCES operational.order_record(order_id) ON DELETE RESTRICT,
    trade_id BIGINT NOT NULL REFERENCES operational.trade(trade_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    broker_id BIGINT NOT NULL REFERENCES reference.broker(broker_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    side TEXT NOT NULL,
    quantity BIGINT NOT NULL,
    price NUMERIC(20,8) NOT NULL,
    gross_value NUMERIC(24,8),
    fees NUMERIC(24,8),
    broker_fill_id TEXT,
    broker_trade_id TEXT,
    exchange_trade_id TEXT,
    exchange_timestamp TIMESTAMPTZ,
    broker_timestamp TIMESTAMPTZ,
    received_timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fill_key UNIQUE (fill_key),
    CONSTRAINT ck_fill_side CHECK (side IN ('BUY','SELL')),
    CONSTRAINT ck_fill_quantity CHECK (quantity > 0),
    CONSTRAINT ck_fill_price CHECK (price >= 0),
    CONSTRAINT ck_fill_values CHECK ((gross_value IS NULL OR gross_value >= 0) AND (fees IS NULL OR fees >= 0))
);

CREATE UNIQUE INDEX uq_fill_broker_fill_id
    ON operational.fill(broker_account_id, broker_fill_id)
    WHERE broker_fill_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fill_broker_trade_id
    ON operational.fill(broker_account_id, broker_trade_id)
    WHERE broker_trade_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fill_exchange_trade_id
    ON operational.fill(broker_account_id, exchange_trade_id)
    WHERE exchange_trade_id IS NOT NULL;
CREATE INDEX ix_fill_order_trade
    ON operational.fill(order_id, trade_id, received_timestamp);

CREATE TABLE operational.position_projection (
    position_projection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL DEFAULT 0,
    average_price NUMERIC(20,8),
    realized_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    open_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    stress_risk NUMERIC(24,8) NOT NULL DEFAULT 0,
    last_fill_id BIGINT REFERENCES operational.fill(fill_id) ON DELETE RESTRICT,
    projection_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_position_projection UNIQUE (broker_account_id, instrument_id),
    CONSTRAINT ck_position_projection_price CHECK (average_price IS NULL OR average_price >= 0),
    CONSTRAINT ck_position_projection_risk CHECK (open_risk >= 0 AND stress_risk >= 0 AND projection_version >= 0)
);

CREATE INDEX ix_position_projection_non_flat
    ON operational.position_projection(broker_account_id, instrument_id)
    WHERE quantity <> 0;

CREATE TABLE operational.reconciliation_run (
    reconciliation_run_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reconciliation_key UUID NOT NULL,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status TEXT NOT NULL,
    mismatch_count INTEGER NOT NULL DEFAULT 0,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reconciliation_key UNIQUE (reconciliation_key),
    CONSTRAINT ck_reconciliation_status CHECK (status IN ('RUNNING','MATCHED','MISMATCH','FAILED','RESOLVED')),
    CONSTRAINT ck_reconciliation_mismatch_count CHECK (mismatch_count >= 0),
    CONSTRAINT ck_reconciliation_times CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX ix_reconciliation_run_account_status
    ON operational.reconciliation_run(broker_account_id, status, started_at DESC);

CREATE TABLE operational.reconciliation_item (
    reconciliation_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reconciliation_run_id BIGINT NOT NULL REFERENCES operational.reconciliation_run(reconciliation_run_id) ON DELETE CASCADE,
    item_type TEXT NOT NULL,
    instrument_id BIGINT REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    internal_reference TEXT,
    broker_reference TEXT,
    mismatch_type TEXT,
    status TEXT NOT NULL,
    internal_payload JSONB,
    broker_payload JSONB,
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reconciliation_item_type CHECK (item_type IN ('ORDER','FILL','POSITION','BALANCE','MARGIN','OTHER')),
    CONSTRAINT ck_reconciliation_item_status CHECK (status IN ('MATCHED','MISMATCH','RESOLVED','IGNORED')),
    CONSTRAINT ck_reconciliation_item_mismatch CHECK (mismatch_type IS NULL OR mismatch_type IN ('MISSING_INTERNAL','MISSING_BROKER','QUANTITY','PRICE','VALUE','STATUS','CASH','MARGIN','OTHER'))
);

CREATE UNIQUE INDEX uq_reconciliation_item_scope
    ON operational.reconciliation_item(reconciliation_run_id, item_type, COALESCE(instrument_id, 0));
CREATE INDEX ix_reconciliation_item_run_status
    ON operational.reconciliation_item(reconciliation_run_id, status);
CREATE INDEX ix_reconciliation_item_instrument
    ON operational.reconciliation_item(instrument_id)
    WHERE instrument_id IS NOT NULL;

-- Post-trade outcome analytics (DD-03 sections 112/126/193). Small and
-- operational, so it stays relational rather than only in Parquet.
CREATE TABLE operational.trade_outcome (
    trade_outcome_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outcome_key UUID NOT NULL,
    trade_id BIGINT NOT NULL REFERENCES operational.trade(trade_id) ON DELETE RESTRICT,
    broker_account_id BIGINT NOT NULL REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    maximum_adverse_excursion NUMERIC(20,8),
    maximum_favourable_excursion NUMERIC(20,8),
    maximum_adverse_excursion_r DOUBLE PRECISION,
    maximum_favourable_excursion_r DOUBLE PRECISION,
    r_multiple DOUBLE PRECISION,
    planned_loss NUMERIC(24,8),
    actual_loss NUMERIC(24,8),
    loss_error NUMERIC(24,8),
    planned_stop NUMERIC(20,8),
    stop_trigger_price NUMERIC(20,8),
    stop_fill_price NUMERIC(20,8),
    stop_slippage NUMERIC(20,8),
    holding_seconds BIGINT,
    label_state TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_outcome_key UNIQUE (outcome_key),
    CONSTRAINT uq_trade_outcome_trade UNIQUE (trade_id),
    CONSTRAINT ck_trade_outcome_excursion CHECK (
        (maximum_adverse_excursion IS NULL OR maximum_adverse_excursion >= 0)
        AND (maximum_favourable_excursion IS NULL OR maximum_favourable_excursion >= 0)
    ),
    CONSTRAINT ck_trade_outcome_holding CHECK (holding_seconds IS NULL OR holding_seconds >= 0),
    CONSTRAINT ck_trade_outcome_label_state CHECK (label_state IN ('PENDING','COMPLETE','RIGHT_CENSORED','DATA_INVALID','NOT_APPLICABLE'))
);

CREATE INDEX ix_trade_outcome_account_instrument
    ON operational.trade_outcome(broker_account_id, instrument_id, created_at DESC);

-- Opportunity Board / recommendation and non-trade decision record
-- (DD-01 sections 17/59/121, DD-05 section 182).
CREATE TABLE operational.recommendation (
    recommendation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recommendation_key UUID NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    setup_observation_id BIGINT REFERENCES operational.setup_observation(setup_observation_id) ON DELETE RESTRICT,
    model_prediction_id BIGINT REFERENCES operational.model_prediction(model_prediction_id) ON DELETE RESTRICT,
    market_observation_id BIGINT REFERENCES market.market_observation(market_observation_id) ON DELETE RESTRICT,
    as_of TIMESTAMPTZ NOT NULL,
    rank INTEGER,
    score DOUBLE PRECISION,
    recommendation_state TEXT NOT NULL,
    non_trade_reason TEXT,
    correlation_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recommendation_key UNIQUE (recommendation_key),
    CONSTRAINT ck_recommendation_rank CHECK (rank IS NULL OR rank > 0),
    CONSTRAINT ck_recommendation_state CHECK (recommendation_state IN ('NO_TRADE','WATCH','SETUP_FORMING','READY','ENTER','ADD','HOLD','REDUCE','EXIT','EMERGENCY_EXIT')),
    CONSTRAINT ck_recommendation_non_trade_reason CHECK (non_trade_reason IS NULL OR non_trade_reason IN ('NO_SETUP','RISK_REJECTED','LOW_RANK','MANUAL_REJECT','DATA_QUALITY_BLOCKED','ML_REJECTED','OUTSIDE_TRADING_WINDOW'))
);

CREATE INDEX ix_recommendation_board
    ON operational.recommendation(tenant_id, as_of DESC, rank);
CREATE INDEX ix_recommendation_instrument_time
    ON operational.recommendation(instrument_id, as_of DESC);
CREATE INDEX ix_recommendation_correlation
    ON operational.recommendation(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE operational.trading_control_state (
    trading_control_state_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    stop_new_trades BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_pending_entries BOOLEAN NOT NULL DEFAULT FALSE,
    flatten_only BOOLEAN NOT NULL DEFAULT FALSE,
    automation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    execution_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    control_reason TEXT,
    state_version BIGINT NOT NULL DEFAULT 0,
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_trading_control_version CHECK (state_version >= 0),
    CONSTRAINT ck_trading_control_consistency CHECK (NOT flatten_only OR stop_new_trades)
);

CREATE UNIQUE INDEX uq_trading_control_scope
    ON operational.trading_control_state(tenant_id, COALESCE(broker_account_id, 0));
