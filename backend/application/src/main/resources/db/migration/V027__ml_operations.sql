-- Edge Relative
-- Flyway V027: ML operations.
--
-- Two concerns:
--   1. control.ml_analysis_run  — the training/analysis queue (created by the UI, polled by the
--      Python research runner, results and registered model_version_id written back).
--   2. control.ml_model_binding — append-only, effective-dated per-instrument model bindings,
--      resolved point-in-time exactly like control.strategy_instrument_binding.
--
-- Model artifacts and their metadata reuse control.model / control.model_version (artifact_uri +
-- artifact_checksum + metrics). The frozen JSON tree dump lives on disk (see artifact storage ADR
-- stance); the DB stores only a URI and checksum.

CREATE TABLE control.ml_analysis_run (
    analysis_run_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_run_key UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    status TEXT NOT NULL DEFAULT 'QUEUED',
    requested_by TEXT,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    progress JSONB NOT NULL DEFAULT '{}'::jsonb,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_version_id BIGINT REFERENCES control.model_version(model_version_id) ON DELETE RESTRICT,
    error TEXT,
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_ml_run_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ml_run_times CHECK (
        completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX ix_ml_run_status_created
    ON control.ml_analysis_run(status, created_at);

CREATE INDEX ix_ml_run_key
    ON control.ml_analysis_run(analysis_run_key);

COMMENT ON TABLE control.ml_analysis_run IS
    'ML training/analysis queue. The UI enqueues; the research runner claims, trains and registers a model version.';

CREATE TABLE control.ml_model_binding (
    binding_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    binding_key UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    model_version_id BIGINT NOT NULL REFERENCES control.model_version(model_version_id) ON DELETE RESTRICT,
    authority_level TEXT NOT NULL DEFAULT 'RANKER',
    effective_from DATE NOT NULL,
    effective_to DATE,
    lifecycle_state TEXT NOT NULL DEFAULT 'RESEARCH',
    source TEXT,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ml_binding_period CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_ml_binding_authority CHECK (
        authority_level IN ('OBSERVER', 'RANKER', 'FILTER', 'RISK_REDUCER')),
    CONSTRAINT ck_ml_binding_state CHECK (
        lifecycle_state IN ('RESEARCH', 'VALIDATED', 'SHADOW', 'PAPER', 'LIVE_LIMITED', 'PRODUCTION', 'RETIRED')),
    CONSTRAINT ex_ml_binding_no_overlap EXCLUDE USING gist (
        instrument_id WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&)
);

CREATE INDEX ix_ml_binding_instrument_period
    ON control.ml_model_binding(instrument_id, effective_from DESC);

COMMENT ON TABLE control.ml_model_binding IS
    'Effective-dated, non-overlapping per-instrument ML model bindings; resolved point-in-time.';
