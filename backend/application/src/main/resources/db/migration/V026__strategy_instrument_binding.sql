-- Effective-dated, versioned per-instrument strategy parameter binding (DD-01 §51 stock-specific
-- intelligence, DD-04 §86 strategy_version association). The research layer writes candidates into
-- this table through a controlled service; the live path resolves the binding effective at the
-- decision date, so point-in-time decisions stay reproducible. Bindings for one instrument may not
-- overlap in time.
CREATE TABLE control.strategy_instrument_binding (
    binding_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    binding_key UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    strategy_version_id BIGINT NOT NULL REFERENCES control.strategy_version(strategy_version_id) ON DELETE RESTRICT,
    parameters JSONB NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    lifecycle_state TEXT NOT NULL DEFAULT 'RESEARCH',
    source TEXT,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_strategy_binding_period CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_strategy_binding_state CHECK (
        lifecycle_state IN ('RESEARCH', 'VALIDATED', 'SHADOW', 'PAPER', 'LIVE_LIMITED', 'PRODUCTION', 'RETIRED')),
    CONSTRAINT ex_strategy_binding_no_overlap EXCLUDE USING gist (
        instrument_id WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&)
);

CREATE INDEX ix_strategy_binding_instrument_period
    ON control.strategy_instrument_binding(instrument_id, effective_from DESC);

COMMENT ON TABLE control.strategy_instrument_binding IS
    'Per-instrument strategy parameter bindings, effective-dated and non-overlapping; resolved point-in-time.';
