-- Edge Relative
-- Flyway V003: control/configuration schema

CREATE TABLE control.feature_definition (
    feature_definition_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    value_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_definition_key UNIQUE (feature_key),
    CONSTRAINT uq_feature_definition_code UNIQUE (code),
    CONSTRAINT ck_feature_definition_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_feature_value_type CHECK (value_type IN ('DOUBLE','INTEGER','BOOLEAN','TEXT','JSON'))
);

CREATE TABLE control.feature_version (
    feature_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_definition_id BIGINT NOT NULL REFERENCES control.feature_definition(feature_definition_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    calculation_version TEXT NOT NULL,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    implementation_reference TEXT,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_version UNIQUE (feature_definition_id, version),
    CONSTRAINT ck_feature_version_positive CHECK (version > 0),
    CONSTRAINT ck_feature_calculation_version_nonblank CHECK (btrim(calculation_version) <> '')
);

CREATE TABLE control.feature_version_state (
    feature_version_state_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_version_id BIGINT NOT NULL REFERENCES control.feature_version(feature_version_id) ON DELETE RESTRICT,
    state TEXT NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_version_state_time UNIQUE (feature_version_id, effective_at),
    CONSTRAINT ck_feature_version_state CHECK (state IN ('EXPERIMENTAL','VALIDATED','PAPER','LIVE_LIMITED','PRODUCTION','RETIRED'))
);

CREATE INDEX ix_feature_version_state_history
    ON control.feature_version_state(feature_version_id, effective_at DESC);

CREATE TABLE control.feature_schema (
    feature_schema_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_schema_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_schema_key UNIQUE (feature_schema_key),
    CONSTRAINT uq_feature_schema_code UNIQUE (code),
    CONSTRAINT ck_feature_schema_code_nonblank CHECK (btrim(code) <> '')
);

CREATE TABLE control.feature_schema_version (
    feature_schema_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_schema_id BIGINT NOT NULL REFERENCES control.feature_schema(feature_schema_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    schema_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_schema_version UNIQUE (feature_schema_id, version),
    CONSTRAINT ck_feature_schema_version_positive CHECK (version > 0)
);

CREATE TABLE control.feature_schema_member (
    feature_schema_version_id BIGINT NOT NULL REFERENCES control.feature_schema_version(feature_schema_version_id) ON DELETE RESTRICT,
    feature_version_id BIGINT NOT NULL REFERENCES control.feature_version(feature_version_id) ON DELETE RESTRICT,
    ordinal INTEGER NOT NULL,
    alias TEXT,
    PRIMARY KEY (feature_schema_version_id, feature_version_id),
    CONSTRAINT uq_feature_schema_member_ordinal UNIQUE (feature_schema_version_id, ordinal),
    CONSTRAINT ck_feature_schema_member_ordinal CHECK (ordinal > 0)
);

CREATE TABLE control.strategy (
    strategy_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    setup_family TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_strategy_key UNIQUE (strategy_key),
    CONSTRAINT uq_strategy_code UNIQUE (code),
    CONSTRAINT ck_strategy_code_nonblank CHECK (btrim(code) <> '')
);

CREATE TABLE control.strategy_version (
    strategy_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES control.strategy(strategy_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    lifecycle_state TEXT NOT NULL,
    primary_timeframe_id BIGINT REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    feature_schema_version_id BIGINT REFERENCES control.feature_schema_version(feature_schema_version_id) ON DELETE RESTRICT,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    entry_rules JSONB NOT NULL DEFAULT '{}'::jsonb,
    exit_rules JSONB NOT NULL DEFAULT '{}'::jsonb,
    risk_requirements JSONB NOT NULL DEFAULT '{}'::jsonb,
    allowed_regimes JSONB NOT NULL DEFAULT '[]'::jsonb,
    supported_instruments JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_data_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_strategy_version UNIQUE (strategy_id, version),
    CONSTRAINT ck_strategy_version_positive CHECK (version > 0),
    CONSTRAINT ck_strategy_lifecycle CHECK (lifecycle_state IN ('RESEARCH','EXPERIMENTAL','BACKTESTED','VALIDATED','SHADOW','PAPER','LIVE_LIMITED','PRODUCTION','RETIRED'))
);

CREATE TABLE control.risk_policy (
    risk_policy_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_policy_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_policy_key UNIQUE (risk_policy_key),
    CONSTRAINT uq_risk_policy_code UNIQUE (code),
    CONSTRAINT ck_risk_policy_code_nonblank CHECK (btrim(code) <> '')
);

CREATE TABLE control.risk_policy_version (
    risk_policy_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_policy_id BIGINT NOT NULL REFERENCES control.risk_policy(risk_policy_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    lifecycle_state TEXT NOT NULL,
    parameters JSONB NOT NULL,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_risk_policy_version UNIQUE (risk_policy_id, version),
    CONSTRAINT ck_risk_policy_version_positive CHECK (version > 0),
    CONSTRAINT ck_risk_policy_lifecycle CHECK (lifecycle_state IN ('EXPERIMENTAL','VALIDATED','PAPER','LIVE_LIMITED','PRODUCTION','RETIRED'))
);

-- Controlled vocabulary for risk decision reason codes (DD-03 sections 158/159).
-- A foreign key from operational.risk_decision_reason keeps the business taxonomy
-- in the database rather than free text. 'OTHER' is an explicit escape hatch.
CREATE TABLE control.risk_reason_code (
    code TEXT PRIMARY KEY,
    reason_type TEXT NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_risk_reason_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_risk_reason_code_type CHECK (reason_type IN ('INFO','REDUCTION','REJECTION','EXIT','HALT'))
);

INSERT INTO control.risk_reason_code (code, reason_type) VALUES
    ('RISK_STATE_BLOCKED', 'REJECTION'),
    ('DAILY_DRAWDOWN_LIMIT', 'REJECTION'),
    ('WEEKLY_DRAWDOWN_LIMIT', 'REJECTION'),
    ('MONTHLY_DRAWDOWN_LIMIT', 'REJECTION'),
    ('ACCOUNT_DRAWDOWN_LIMIT', 'REJECTION'),
    ('STRATEGY_DRAWDOWN_LIMIT', 'REJECTION'),
    ('CONSECUTIVE_LOSS_LIMIT', 'REJECTION'),
    ('TRADE_RISK_LIMIT', 'REJECTION'),
    ('PORTFOLIO_OPEN_RISK_LIMIT', 'REJECTION'),
    ('PORTFOLIO_STRESS_RISK_LIMIT', 'REJECTION'),
    ('GROSS_EXPOSURE_LIMIT', 'REJECTION'),
    ('NET_EXPOSURE_LIMIT', 'REJECTION'),
    ('SYMBOL_CONCENTRATION_LIMIT', 'REJECTION'),
    ('SECTOR_CONCENTRATION_LIMIT', 'REJECTION'),
    ('CORRELATION_LIMIT', 'REJECTION'),
    ('MAX_POSITIONS_LIMIT', 'REJECTION'),
    ('LIQUIDITY_LIMIT', 'REJECTION'),
    ('SPREAD_LIMIT', 'REJECTION'),
    ('MARGIN_LIMIT', 'REJECTION'),
    ('BUYING_POWER_LIMIT', 'REJECTION'),
    ('EVENT_RISK_BLOCKED', 'REJECTION'),
    ('DATA_QUALITY_BLOCKED', 'REJECTION'),
    ('BROKER_HEALTH_BLOCKED', 'REJECTION'),
    ('RECONCILIATION_MISMATCH', 'REJECTION'),
    ('ORDER_STATE_UNKNOWN', 'REJECTION'),
    ('INVALID_STOP_DISTANCE', 'REJECTION'),
    ('INSUFFICIENT_RISK_CAPACITY', 'REJECTION'),
    ('SESSION_FLATTEN_WINDOW', 'REJECTION'),
    ('COMPLIANCE_BLOCKED', 'REJECTION'),
    ('RISK_REDUCED_DRAWDOWN', 'REDUCTION'),
    ('RISK_REDUCED_MARKET_REGIME', 'REDUCTION'),
    ('RISK_REDUCED_SECTOR_CONCENTRATION', 'REDUCTION'),
    ('RISK_REDUCED_CORRELATION', 'REDUCTION'),
    ('RISK_REDUCED_LIQUIDITY', 'REDUCTION'),
    ('RISK_REDUCED_MARGIN', 'REDUCTION'),
    ('RISK_REDUCED_DEPLOYMENT_STAGE', 'REDUCTION'),
    ('RISK_REDUCED_ML', 'REDUCTION'),
    ('RISK_REDUCED_MANUAL', 'REDUCTION'),
    ('EXIT_REQUIRED', 'EXIT'),
    ('HALT_REQUIRED', 'HALT'),
    ('OTHER', 'INFO');

CREATE TABLE control.model (
    model_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_key UNIQUE (model_key),
    CONSTRAINT uq_model_code UNIQUE (code),
    CONSTRAINT ck_model_code_nonblank CHECK (btrim(code) <> '')
);

CREATE TABLE control.model_version (
    model_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id BIGINT NOT NULL REFERENCES control.model(model_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    lifecycle_state TEXT NOT NULL,
    algorithm TEXT NOT NULL,
    feature_schema_version_id BIGINT REFERENCES control.feature_schema_version(feature_schema_version_id) ON DELETE RESTRICT,
    artifact_uri TEXT NOT NULL,
    artifact_checksum TEXT,
    training_period_start DATE,
    training_period_end DATE,
    validation_period_start DATE,
    validation_period_end DATE,
    test_period_start DATE,
    test_period_end DATE,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    strategy_compatibility JSONB NOT NULL DEFAULT '[]'::jsonb,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_version UNIQUE (model_id, version),
    CONSTRAINT ck_model_version_positive CHECK (version > 0),
    CONSTRAINT ck_model_lifecycle CHECK (lifecycle_state IN ('EXPERIMENT','VALIDATED','SHADOW','PAPER','LIVE_LIMITED','PRODUCTION','RETIRED')),
    CONSTRAINT ck_model_artifact_uri_nonblank CHECK (btrim(artifact_uri) <> ''),
    CONSTRAINT ck_model_training_period CHECK (training_period_end IS NULL OR training_period_start IS NULL OR training_period_end >= training_period_start),
    CONSTRAINT ck_model_validation_period CHECK (validation_period_end IS NULL OR validation_period_start IS NULL OR validation_period_end >= validation_period_start),
    CONSTRAINT ck_model_test_period CHECK (test_period_end IS NULL OR test_period_start IS NULL OR test_period_end >= test_period_start)
);
