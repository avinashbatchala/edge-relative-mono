-- Deterministic integration fixture for the research data-access tests.
-- Explicit identities are used so tests can assert exact rows. Loaded after the backend
-- Flyway migrations and executed with the simple query protocol.

INSERT INTO reference.exchange (exchange_id, code, name, timezone, currency_code)
OVERRIDING SYSTEM VALUE VALUES (1, 'NSE', 'NSE', 'Asia/Kolkata', 'INR');

INSERT INTO reference.broker (broker_id, code, name)
OVERRIDING SYSTEM VALUE VALUES (1, 'GROWW', 'Groww');

INSERT INTO reference.instrument
    (instrument_id, instrument_key, exchange_id, instrument_type, segment, canonical_symbol,
     display_name, tick_size, lot_size, trading_status)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 'EQUITY', 'EQ', 'RELIANCE', 'Reliance Industries', 0.05, 1, 'ACTIVE'),
    (2, gen_random_uuid(), 1, 'EQUITY', 'EQ', 'TCS', 'Tata Consultancy', 0.05, 1, 'ACTIVE'),
    (3, gen_random_uuid(), 1, 'INDEX', 'IDX', 'NIFTY50', 'NIFTY 50', 0.05, 1, 'ACTIVE');

-- Instrument 1's symbol changes twice over time; instrument 2 is stable.
INSERT INTO reference.instrument_identifier
    (instrument_identifier_id, instrument_id, exchange_id, identifier_type, identifier_value,
     valid_from, valid_to)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 'SYMBOL', 'RELIANCEOLD', DATE '2015-01-01', DATE '2020-01-01'),
    (2, 1, 1, 'SYMBOL', 'RELIANCE', DATE '2020-01-01', DATE '2025-01-01'),
    (3, 1, 1, 'SYMBOL', 'RELIANCE-NEW', DATE '2025-01-01', NULL),
    (4, 2, 1, 'SYMBOL', 'TCS', DATE '2015-01-01', NULL);

INSERT INTO reference.sector (sector_id, code, name)
OVERRIDING SYSTEM VALUE VALUES
    (1, 'ENERGY', 'Energy'),
    (2, 'IT', 'Information Technology'),
    (3, 'FINANCE', 'Financial Services');

-- Instrument 1's sector changes in 2023; instrument 2 is stable.
INSERT INTO reference.instrument_sector_history
    (instrument_sector_history_id, instrument_id, sector_id, valid_from, valid_to, source)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, DATE '2020-01-01', DATE '2023-01-01', 'seed'),
    (2, 1, 3, DATE '2023-01-01', NULL, 'seed'),
    (3, 2, 2, DATE '2020-01-01', NULL, 'seed');

INSERT INTO reference.benchmark
    (benchmark_id, benchmark_key, exchange_id, instrument_id, code, name, benchmark_type)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 3, 'NIFTY50', 'NIFTY 50', 'INDEX');

-- Benchmark membership changes: instrument 1 leaves in 2024, instrument 2 joins.
INSERT INTO reference.benchmark_constituent_history
    (benchmark_constituent_history_id, benchmark_id, instrument_id, weight, valid_from, valid_to)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 0.10, DATE '2020-01-01', DATE '2024-01-01'),
    (2, 1, 2, 0.05, DATE '2024-01-01', NULL);

INSERT INTO reference.sector_benchmark_history
    (sector_benchmark_history_id, sector_id, benchmark_id, valid_from, valid_to)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, DATE '2020-01-01', NULL);

INSERT INTO reference.trading_calendar_day
    (trading_calendar_day_id, exchange_id, trading_date, day_type)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, DATE '2026-09-01', 'TRADING'),
    (2, 1, DATE '2026-09-02', 'TRADING');

INSERT INTO reference.trading_session
    (trading_session_id, trading_calendar_day_id, session_type, opens_at, closes_at)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'NORMAL', TIMESTAMPTZ '2026-09-01 03:45:00+00', TIMESTAMPTZ '2026-09-01 10:00:00+00');

INSERT INTO reference.corporate_action
    (corporate_action_id, instrument_id, action_type, ex_date, record_date, effective_date,
     ratio_numerator, ratio_denominator, source_reference)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'SPLIT', DATE '2024-06-01', DATE '2024-06-01', DATE '2024-06-01', 2, 1, 'seed');

INSERT INTO reference.corporate_action_factor
    (corporate_action_factor_id, corporate_action_id, instrument_id, factor_version, price_factor,
     quantity_factor, definition, available_at)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 1, 0.5, 2.0, 'SPLIT_2_FOR_1', TIMESTAMPTZ '2024-05-01 00:00:00+00');

INSERT INTO reference.universe (universe_id, universe_key, code, name)
OVERRIDING SYSTEM VALUE VALUES (1, gen_random_uuid(), 'WATCHLIST', 'Watchlist');

INSERT INTO reference.universe_membership_history
    (universe_membership_history_id, universe_id, instrument_id, valid_from)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, DATE '2020-01-01');

INSERT INTO control.feature_definition
    (feature_definition_id, feature_key, code, name, value_type)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 'ATR', 'Average True Range', 'DOUBLE'),
    (2, gen_random_uuid(), 'RRS_RAW', 'Relative Strength Raw', 'DOUBLE');

INSERT INTO control.feature_version
    (feature_version_id, feature_definition_id, version, calculation_version, parameters,
     implementation_reference, code_version)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 'er-feature-calc-v1', '{}'::jsonb, 'AtrFeature', 'er-feature-calc-v1'),
    (2, 2, 1, 'er-feature-calc-v1', '{}'::jsonb, 'RrsFeature', 'er-feature-calc-v1');

INSERT INTO control.feature_schema (feature_schema_id, feature_schema_key, code, name)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 'ER_FEATURE_SET', 'Edge Relative feature set');

INSERT INTO control.feature_schema_version
    (feature_schema_version_id, feature_schema_id, version, schema_hash)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, 'schema-hash-v1');

INSERT INTO control.feature_schema_member
    (feature_schema_version_id, feature_version_id, ordinal, alias)
VALUES (1, 1, 1, 'ATR'), (1, 2, 2, 'RRS_RAW');

-- control.strategy and control.strategy_version are seeded by Flyway V014
-- (ER_RS_CONTINUATION_V1, version 1, RESEARCH); do not duplicate them here.

INSERT INTO control.risk_policy (risk_policy_id, risk_policy_key, code, name)
OVERRIDING SYSTEM VALUE VALUES (1, gen_random_uuid(), 'DEFAULT', 'Default');

INSERT INTO control.risk_policy_version
    (risk_policy_version_id, risk_policy_id, version, lifecycle_state, parameters, code_version)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, 'VALIDATED', '{}'::jsonb, 'er-risk-v1');

INSERT INTO control.model (model_id, model_key, code, name)
OVERRIDING SYSTEM VALUE VALUES (1, gen_random_uuid(), 'ER_TPB_MODEL', 'TPB model');

INSERT INTO control.model_version
    (model_version_id, model_id, version, lifecycle_state, algorithm, feature_schema_version_id,
     artifact_uri, metrics)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 'EXPERIMENT', 'logistic_regression', 1, 'file:///models/1.json', '{}'::jsonb);

INSERT INTO operational.tenant (tenant_id, tenant_key, name)
OVERRIDING SYSTEM VALUE VALUES (1, gen_random_uuid(), 'Default Operator');

INSERT INTO operational.app_user (app_user_id, user_key, tenant_id, display_name)
OVERRIDING SYSTEM VALUE VALUES (1, gen_random_uuid(), 1, 'Operator');

INSERT INTO operational.broker_account
    (broker_account_id, broker_account_key, tenant_id, app_user_id, broker_id,
     external_account_id, secret_reference)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 'ACC-1', 'secret://test');

INSERT INTO market.market_observation
    (market_observation_id, observation_key, instrument_id, timeframe_id, bar_close_timestamp,
     market_data_source_id, quality_status)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M5'),
     TIMESTAMPTZ '2026-09-01 04:05:00+00',
     (SELECT market_data_source_id FROM reference.market_data_source WHERE code = 'GROWW'), 'GOOD'),
    (2, gen_random_uuid(), 1, (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M5'),
     TIMESTAMPTZ '2026-09-01 04:10:00+00',
     (SELECT market_data_source_id FROM reference.market_data_source WHERE code = 'GROWW'), 'GOOD'),
    (3, gen_random_uuid(), 1, (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M5'),
     TIMESTAMPTZ '2026-09-01 04:15:00+00',
     (SELECT market_data_source_id FROM reference.market_data_source WHERE code = 'GROWW'), 'DEGRADED');

INSERT INTO market.market_observation_revision
    (market_observation_revision_id, market_observation_id, revision_no, quality_status,
     is_canonical)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 1, 'GOOD', TRUE);

INSERT INTO market.candle
    (candle_id, instrument_id, timeframe_id, open_time, open, high, low, close, volume,
     close_time, quality_state, source_revision, is_current)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M1'),
     TIMESTAMPTZ '2026-09-01 03:45:00+00', 100, 101, 99.5, 100.5, 1000,
     TIMESTAMPTZ '2026-09-01 03:46:00+00', 'GOOD', 'seed', TRUE),
    (2, 1, (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M1'),
     TIMESTAMPTZ '2026-09-01 03:46:00+00', 100.5, 101.5, 100, 101, 1200,
     TIMESTAMPTZ '2026-09-01 03:47:00+00', 'GOOD', 'seed', TRUE);

INSERT INTO operational.setup_observation
    (setup_observation_id, setup_observation_key, tenant_id, broker_account_id,
     market_observation_id, strategy_version_id, instrument_id, observed_at, direction,
     setup_status, entry_pattern)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 1, TIMESTAMPTZ '2026-09-01 04:05:30+00', 'LONG',
     'VALID', 'M5_3_8_CONFIRMATION'),
    (2, gen_random_uuid(), 1, 1, 2, 1, 1, TIMESTAMPTZ '2026-09-01 04:10:30+00', 'LONG',
     'NEAR_TRIGGER', 'M5_3_8_CONFIRMATION'),
    (3, gen_random_uuid(), 1, 1, 3, 1, 1, TIMESTAMPTZ '2026-09-01 04:15:30+00', 'LONG',
     'MISSED', 'M5_3_8_CONFIRMATION');

INSERT INTO operational.risk_context_snapshot
    (risk_context_snapshot_id, risk_context_key, tenant_id, broker_account_id, trading_date,
     captured_at, risk_policy_version_id, risk_reference_equity, current_equity,
     portfolio_open_risk, portfolio_stress_risk, gross_exposure, net_exposure, session_pnl,
     session_drawdown, risk_state, broker_health, data_health, reconciliation_state)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, DATE '2026-09-01', TIMESTAMPTZ '2026-09-01 04:06:00+00', 1,
     1000000, 1000000, 0, 0, 0, 0, 0, 0, 'NORMAL', 'HEALTHY', 'GOOD', 'MATCHED');

INSERT INTO operational.risk_decision
    (risk_decision_id, decision_key, setup_observation_id, risk_context_snapshot_id, tenant_id,
     broker_account_id, strategy_version_id, risk_policy_version_id, instrument_id, decision_at,
     decision, requested_quantity, approved_quantity, requested_risk, approved_risk,
     approved_notional)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 1, TIMESTAMPTZ '2026-09-01 04:06:00+00',
     'APPROVE', 100, 100, 400, 300, 10000),
    (2, gen_random_uuid(), 2, 1, 1, 1, 1, 1, 1, TIMESTAMPTZ '2026-09-01 04:11:00+00',
     'REJECT', 100, 0, 100, 0, 0);

INSERT INTO operational.risk_decision_reason
    (risk_decision_reason_id, risk_decision_id, ordinal, reason_code, reason_type)
OVERRIDING SYSTEM VALUE VALUES (1, 2, 1, 'INVALID_SETUP', 'REJECTION');

INSERT INTO operational.trade_plan
    (trade_plan_id, trade_plan_key, risk_decision_id, setup_observation_id, tenant_id,
     broker_account_id, strategy_version_id, instrument_id, market_observation_id, direction,
     entry_pattern, planned_quantity, entry_low, entry_high, structural_invalidation,
     protective_stop, target_reference, expected_reward_risk, planned_risk, planned_notional,
     feature_schema_version)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 1, 'LONG', 'M5_3_8_CONFIRMATION', 100, 100, 101,
     99, 98, 110, 3.0, 300, 10000, 'er-feature-schema-v1');

INSERT INTO operational.trade
    (trade_id, trade_key, trade_plan_id, tenant_id, broker_account_id, instrument_id,
     strategy_version_id, direction, status, opened_at, planned_quantity, filled_entry_quantity,
     filled_exit_quantity, average_entry_price, realized_pnl)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 1, 'LONG', 'OPEN', TIMESTAMPTZ '2026-09-01 04:06:00+00',
     100, 40, 0, 100.5, 0);

INSERT INTO operational.order_record
    (order_id, order_key, client_order_reference, trade_id, trade_plan_id, tenant_id,
     broker_account_id, broker_id, instrument_id, side, order_role, order_type,
     requested_quantity, filled_quantity, average_fill_price, status, submitted_at)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), gen_random_uuid(), 1, 1, 1, 1, 1, 1, 'BUY', 'ENTRY', 'LIMIT', 100, 40,
     100.5, 'PARTIAL', TIMESTAMPTZ '2026-09-01 04:06:00+00');

INSERT INTO operational.order_event
    (order_event_id, order_id, event_type, event_timestamp, sequence_no, status_after)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'PARTIAL_FILL', TIMESTAMPTZ '2026-09-01 04:06:10+00', 1, 'PARTIAL');

INSERT INTO operational.fill
    (fill_id, fill_key, order_id, trade_id, tenant_id, broker_account_id, broker_id,
     instrument_id, side, quantity, price, received_timestamp)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 'BUY', 15, 100.40,
     TIMESTAMPTZ '2026-09-01 04:06:05+00'),
    (2, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 'BUY', 25, 100.56,
     TIMESTAMPTZ '2026-09-01 04:06:45+00');

INSERT INTO operational.model_prediction
    (model_prediction_id, prediction_key, setup_observation_id, model_version_id, predicted_at,
     probability_target_before_stop, expected_r, confidence)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, TIMESTAMPTZ '2026-09-01 04:05:45+00', 0.62, 1.4, 0.7);

INSERT INTO operational.portfolio_snapshot
    (portfolio_snapshot_id, snapshot_key, tenant_id, broker_account_id, snapshot_at,
     trading_date, net_liquidation_value)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, TIMESTAMPTZ '2026-09-01 04:05:00+00', DATE '2026-09-01',
     1000000);

INSERT INTO research.dataset (dataset_id, dataset_key, code, name, dataset_type)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 'CANONICAL_M5', 'Canonical M5', 'MARKET');

INSERT INTO research.dataset_version
    (dataset_version_id, dataset_version_key, dataset_id, version, status,
     feature_schema_version_id, point_in_time_cutoff, storage_uri, row_count, checksum,
     code_version, committed_at)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 'COMMITTED', 1, TIMESTAMPTZ '2026-09-01 10:00:00+00',
     'postgres://canonical', 100, 'checksum-v1', 'er-backtest-engine-v1',
     TIMESTAMPTZ '2026-09-02 00:00:00+00'),
    (2, gen_random_uuid(), 1, 2, 'BUILDING', 1, TIMESTAMPTZ '2026-09-02 10:00:00+00',
     'postgres://canonical', NULL, NULL, 'er-backtest-engine-v1', NULL);

INSERT INTO research.dataset_version_input
    (dataset_version_id, input_dataset_version_id, input_role)
VALUES (2, 1, 'INPUT');

INSERT INTO research.outcome_schema (outcome_schema_id, outcome_schema_key, code, name)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 'ER_OUTCOME', 'ER outcome schema');

INSERT INTO research.outcome_schema_version
    (outcome_schema_version_id, outcome_schema_id, version)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 1);

INSERT INTO research.outcome_definition
    (outcome_definition_id, outcome_schema_version_id, code, value_type, horizon_seconds, ordinal)
OVERRIDING SYSTEM VALUE VALUES (1, 1, 'return_5m', 'DOUBLE', 300, 1);

INSERT INTO research.pattern_schema (pattern_schema_id, pattern_schema_key, code, name)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 'ER_PATTERN', 'ER pattern schema');

INSERT INTO research.pattern_schema_version
    (pattern_schema_version_id, pattern_schema_id, version, timeframe_id, window_length,
     feature_schema_version_id)
OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M5'), 12, 1);

INSERT INTO research.pattern_window_metadata
    (pattern_window_metadata_id, pattern_key, pattern_schema_version_id, dataset_version_id,
     market_observation_id, instrument_id, sector_id, timeframe_id, anchor_timestamp,
     market_regime, sector_regime, normalization_version, minutes_since_open, storage_uri)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 1,
     (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M5'),
     TIMESTAMPTZ '2026-09-01 04:05:00+00', 'BULL', 'STRONG', 'er-norm-v1', 20,
     'file:///patterns/1');

INSERT INTO research.similarity_index_metadata
    (similarity_index_metadata_id, index_key, pattern_schema_version_id, dataset_version_id,
     index_type, status, corpus_cutoff, storage_uri, checksum)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 'EXACT', 'READY', TIMESTAMPTZ '2026-09-01 04:05:00+00',
     'file:///index/1', 'index-checksum');

INSERT INTO research.experiment (experiment_id, experiment_key, code, hypothesis, created_by)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 'EXP_TEST', 'Feature conditioning improves expectancy', 'tester');

INSERT INTO research.experiment_run
    (experiment_run_id, run_key, experiment_id, status, strategy_version_id, dataset_version_id,
     date_range_start, date_range_end, parameters, code_version)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 'SUCCEEDED', 1, 1, DATE '2026-09-01', DATE '2026-09-02',
     '{}'::jsonb, 'code-v1');

INSERT INTO research.backtest_run
    (backtest_run_id, run_key, experiment_run_id, strategy_version_id, risk_policy_version_id,
     dataset_version_id, status, result_uri, metrics, seed, starting_capital, currency,
     engine_revision)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 1, 'SUCCEEDED', 'file:///results/1', '{}'::jsonb, 42,
     1000000, 'INR', 'er-backtest-engine-v1');

INSERT INTO research.training_run
    (training_run_id, run_key, experiment_run_id, dataset_version_id, feature_schema_version_id,
     status, algorithm, metrics, artifact_uri)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 1, 'SUCCEEDED', 'logistic_regression', '{}'::jsonb,
     'file:///artifacts/1');

INSERT INTO research.model_candidate
    (model_candidate_id, candidate_key, model_id, training_run_id, status, artifact_uri, metrics)
OVERRIDING SYSTEM VALUE VALUES
    (1, gen_random_uuid(), 1, 1, 'CANDIDATE', 'file:///candidates/1', '{}'::jsonb);

-- Explicit-id seeding uses OVERRIDING SYSTEM VALUE, which does not advance identity
-- sequences. Realign every identity sequence in the research schema with its table so
-- later inserts by the dataset builder do not collide.
DO $$
DECLARE
    target RECORD;
BEGIN
    FOR target IN
        SELECT c.relname AS table_name, a.attname AS column_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0
        WHERE n.nspname = 'research'
          AND c.relkind = 'r'
          AND a.attidentity IN ('a', 'd')
    LOOP
        EXECUTE format(
            'SELECT setval(pg_get_serial_sequence(%L, %L), '
            'GREATEST((SELECT COALESCE(MAX(%I), 1) FROM research.%I), 1))',
            'research.' || target.table_name,
            target.column_name,
            target.column_name,
            target.table_name
        );
    END LOOP;
END
$$;
