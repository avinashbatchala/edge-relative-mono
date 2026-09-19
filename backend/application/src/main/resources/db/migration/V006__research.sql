-- Edge Relative
-- Flyway V006: research and dataset catalog

CREATE TABLE research.dataset (
    dataset_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    dataset_type TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dataset_key UNIQUE (dataset_key),
    CONSTRAINT uq_dataset_code UNIQUE (code),
    CONSTRAINT ck_dataset_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_dataset_type CHECK (dataset_type IN ('MARKET','FEATURE','PATTERN','OUTCOME','BACKTEST','TRAINING','OTHER'))
);

CREATE TABLE research.dataset_version (
    dataset_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_version_key UUID NOT NULL,
    dataset_id BIGINT NOT NULL REFERENCES research.dataset(dataset_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'BUILDING',
    feature_schema_version_id BIGINT REFERENCES control.feature_schema_version(feature_schema_version_id) ON DELETE RESTRICT,
    point_in_time_cutoff TIMESTAMPTZ,
    storage_uri TEXT NOT NULL,
    partition_manifest_uri TEXT,
    row_count BIGINT,
    checksum TEXT,
    code_version TEXT,
    committed_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dataset_version_key UNIQUE (dataset_version_key),
    CONSTRAINT uq_dataset_version UNIQUE (dataset_id, version),
    CONSTRAINT ck_dataset_version_positive CHECK (version > 0),
    CONSTRAINT ck_dataset_version_status CHECK (status IN ('BUILDING','COMMITTED','RETIRED','FAILED')),
    CONSTRAINT ck_dataset_version_storage_uri CHECK (btrim(storage_uri) <> ''),
    CONSTRAINT ck_dataset_version_row_count CHECK (row_count IS NULL OR row_count >= 0),
    CONSTRAINT ck_dataset_version_commit_shape CHECK (
        (status = 'COMMITTED' AND committed_at IS NOT NULL AND checksum IS NOT NULL)
        OR status <> 'COMMITTED'
    ),
    CONSTRAINT ck_dataset_version_retired_shape CHECK ((status = 'RETIRED' AND retired_at IS NOT NULL) OR status <> 'RETIRED')
);

CREATE TABLE research.dataset_version_input (
    dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    input_dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    input_role TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dataset_version_id, input_dataset_version_id, input_role),
    CONSTRAINT ck_dataset_version_input_not_self CHECK (dataset_version_id <> input_dataset_version_id),
    CONSTRAINT ck_dataset_version_input_role_nonblank CHECK (btrim(input_role) <> '')
);

CREATE TABLE research.pattern_schema (
    pattern_schema_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pattern_schema_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pattern_schema_key UNIQUE (pattern_schema_key),
    CONSTRAINT uq_pattern_schema_code UNIQUE (code)
);

CREATE TABLE research.pattern_schema_version (
    pattern_schema_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pattern_schema_id BIGINT NOT NULL REFERENCES research.pattern_schema(pattern_schema_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    timeframe_id BIGINT NOT NULL REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    window_length INTEGER NOT NULL,
    feature_schema_version_id BIGINT NOT NULL REFERENCES control.feature_schema_version(feature_schema_version_id) ON DELETE RESTRICT,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pattern_schema_version UNIQUE (pattern_schema_id, version),
    CONSTRAINT ck_pattern_schema_version_positive CHECK (version > 0 AND window_length > 0)
);

CREATE TABLE research.pattern_window_metadata (
    pattern_window_metadata_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pattern_key UUID NOT NULL,
    pattern_schema_version_id BIGINT NOT NULL REFERENCES research.pattern_schema_version(pattern_schema_version_id) ON DELETE RESTRICT,
    dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    market_observation_id BIGINT REFERENCES market.market_observation(market_observation_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    sector_id BIGINT REFERENCES reference.sector(sector_id) ON DELETE RESTRICT,
    timeframe_id BIGINT NOT NULL REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    anchor_timestamp TIMESTAMPTZ NOT NULL,
    market_regime TEXT,
    sector_regime TEXT,
    normalization_version TEXT,
    minutes_since_open INTEGER,
    storage_uri TEXT NOT NULL,
    storage_row_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pattern_key UNIQUE (pattern_key),
    CONSTRAINT uq_pattern_semantic UNIQUE (pattern_schema_version_id, instrument_id, anchor_timestamp),
    CONSTRAINT ck_pattern_minutes_since_open CHECK (minutes_since_open IS NULL OR minutes_since_open >= 0),
    CONSTRAINT ck_pattern_storage_uri_nonblank CHECK (btrim(storage_uri) <> '')
);

CREATE INDEX ix_pattern_lookup
    ON research.pattern_window_metadata(instrument_id, timeframe_id, sector_id, market_regime, minutes_since_open, anchor_timestamp DESC);

CREATE TABLE research.similarity_index_metadata (
    similarity_index_metadata_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    index_key UUID NOT NULL,
    pattern_schema_version_id BIGINT NOT NULL REFERENCES research.pattern_schema_version(pattern_schema_version_id) ON DELETE RESTRICT,
    dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    index_type TEXT NOT NULL,
    status TEXT NOT NULL,
    corpus_cutoff TIMESTAMPTZ NOT NULL,
    storage_uri TEXT NOT NULL,
    checksum TEXT NOT NULL,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    built_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_similarity_index_key UNIQUE (index_key),
    CONSTRAINT ck_similarity_index_status CHECK (status IN ('BUILDING','READY','FAILED','RETIRED')),
    CONSTRAINT ck_similarity_index_uri_nonblank CHECK (btrim(storage_uri) <> ''),
    CONSTRAINT ck_similarity_index_checksum_nonblank CHECK (btrim(checksum) <> '')
);

CREATE INDEX ix_similarity_index_ready
    ON research.similarity_index_metadata(pattern_schema_version_id, corpus_cutoff DESC)
    WHERE status = 'READY';

CREATE TABLE research.outcome_schema (
    outcome_schema_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outcome_schema_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_outcome_schema_key UNIQUE (outcome_schema_key),
    CONSTRAINT uq_outcome_schema_code UNIQUE (code)
);

CREATE TABLE research.outcome_schema_version (
    outcome_schema_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outcome_schema_id BIGINT NOT NULL REFERENCES research.outcome_schema(outcome_schema_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_outcome_schema_version UNIQUE (outcome_schema_id, version),
    CONSTRAINT ck_outcome_schema_version_positive CHECK (version > 0)
);

CREATE TABLE research.outcome_definition (
    outcome_definition_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outcome_schema_version_id BIGINT NOT NULL REFERENCES research.outcome_schema_version(outcome_schema_version_id) ON DELETE RESTRICT,
    code TEXT NOT NULL,
    value_type TEXT NOT NULL,
    horizon_seconds BIGINT,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    ordinal INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_outcome_definition_code UNIQUE (outcome_schema_version_id, code),
    CONSTRAINT uq_outcome_definition_ordinal UNIQUE (outcome_schema_version_id, ordinal),
    CONSTRAINT ck_outcome_definition_type CHECK (value_type IN ('DOUBLE','INTEGER','BOOLEAN','TEXT')),
    CONSTRAINT ck_outcome_definition_horizon CHECK (horizon_seconds IS NULL OR horizon_seconds >= 0),
    CONSTRAINT ck_outcome_definition_ordinal CHECK (ordinal > 0)
);

CREATE TABLE research.outcome_dataset (
    outcome_dataset_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    outcome_schema_version_id BIGINT NOT NULL REFERENCES research.outcome_schema_version(outcome_schema_version_id) ON DELETE RESTRICT,
    source_feature_dataset_version_id BIGINT REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_outcome_dataset_version UNIQUE (dataset_version_id)
);

CREATE TABLE research.experiment (
    experiment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    experiment_key UUID NOT NULL,
    code TEXT NOT NULL,
    hypothesis TEXT NOT NULL,
    created_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_experiment_key UNIQUE (experiment_key),
    CONSTRAINT uq_experiment_code UNIQUE (code),
    CONSTRAINT ck_experiment_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_experiment_hypothesis_nonblank CHECK (btrim(hypothesis) <> '')
);

CREATE TABLE research.experiment_run (
    experiment_run_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_key UUID NOT NULL,
    experiment_id BIGINT NOT NULL REFERENCES research.experiment(experiment_id) ON DELETE RESTRICT,
    status TEXT NOT NULL,
    strategy_version_id BIGINT REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    dataset_version_id BIGINT REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    date_range_start DATE,
    date_range_end DATE,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    cost_model JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    code_version TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_experiment_run_key UNIQUE (run_key),
    CONSTRAINT ck_experiment_run_status CHECK (status IN ('CREATED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT ck_experiment_run_dates CHECK (date_range_end IS NULL OR date_range_start IS NULL OR date_range_end >= date_range_start),
    CONSTRAINT ck_experiment_run_times CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX ix_experiment_run_experiment_status
    ON research.experiment_run(experiment_id, status, created_at DESC);

CREATE TABLE research.backtest_run (
    backtest_run_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_key UUID NOT NULL,
    experiment_run_id BIGINT REFERENCES research.experiment_run(experiment_run_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    risk_policy_version_id BIGINT REFERENCES control.risk_policy_version(risk_policy_version_id) ON DELETE RESTRICT,
    dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    status TEXT NOT NULL,
    result_uri TEXT,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_backtest_run_key UNIQUE (run_key),
    CONSTRAINT ck_backtest_run_status CHECK (status IN ('CREATED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT ck_backtest_run_times CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE TABLE research.training_run (
    training_run_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_key UUID NOT NULL,
    experiment_run_id BIGINT REFERENCES research.experiment_run(experiment_run_id) ON DELETE RESTRICT,
    dataset_version_id BIGINT NOT NULL REFERENCES research.dataset_version(dataset_version_id) ON DELETE RESTRICT,
    feature_schema_version_id BIGINT NOT NULL REFERENCES control.feature_schema_version(feature_schema_version_id) ON DELETE RESTRICT,
    status TEXT NOT NULL,
    algorithm TEXT NOT NULL,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    artifact_uri TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_training_run_key UNIQUE (run_key),
    CONSTRAINT ck_training_run_status CHECK (status IN ('CREATED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT ck_training_run_times CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE TABLE research.model_candidate (
    model_candidate_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_key UUID NOT NULL,
    model_id BIGINT REFERENCES control.model(model_id) ON DELETE RESTRICT,
    training_run_id BIGINT NOT NULL REFERENCES research.training_run(training_run_id) ON DELETE RESTRICT,
    status TEXT NOT NULL,
    artifact_uri TEXT NOT NULL,
    artifact_checksum TEXT,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    promoted_model_version_id BIGINT REFERENCES control.model_version(model_version_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_candidate_key UNIQUE (candidate_key),
    CONSTRAINT ck_model_candidate_status CHECK (status IN ('CANDIDATE','VALIDATED','REJECTED','PROMOTED','RETIRED')),
    CONSTRAINT ck_model_candidate_uri_nonblank CHECK (btrim(artifact_uri) <> '')
);

CREATE INDEX ix_model_candidate_status
    ON research.model_candidate(status, created_at DESC);
