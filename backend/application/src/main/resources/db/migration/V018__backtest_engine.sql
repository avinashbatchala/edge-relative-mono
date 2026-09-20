-- Edge Relative
-- Flyway V018: backtest engine support.
--
-- research.experiment_run / research.backtest_run already exist (V006). This adds the immutable run
-- specification and the append-only simulated ledger (trades, equity points, rejections) plus run
-- progress/cancellation columns. Rejected candidates are retained for analysis; simulated activity
-- never touches operational (production) tables.

ALTER TABLE research.experiment_run
    ADD COLUMN progress_events BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN progress_total BIGINT,
    ADD COLUMN progress_through TIMESTAMPTZ,
    ADD COLUMN failure JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN requested_by TEXT;

ALTER TABLE research.experiment_run
    ADD CONSTRAINT ck_experiment_run_progress CHECK (progress_events >= 0 AND (progress_total IS NULL OR progress_total >= 0));

ALTER TABLE research.backtest_run
    ADD COLUMN seed BIGINT,
    ADD COLUMN universe_size INTEGER,
    ADD COLUMN starting_capital NUMERIC(24,8),
    ADD COLUMN currency TEXT,
    ADD COLUMN engine_revision TEXT;

-- Immutable resolved specification captured before execution begins.
CREATE TABLE research.backtest_run_spec (
    backtest_run_spec_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spec_key UUID NOT NULL,
    experiment_run_id BIGINT NOT NULL REFERENCES research.experiment_run(experiment_run_id) ON DELETE RESTRICT,
    spec JSONB NOT NULL,
    dataset_manifest JSONB NOT NULL DEFAULT '{}'::jsonb,
    checksum TEXT,
    code_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_backtest_run_spec_key UNIQUE (spec_key),
    CONSTRAINT uq_backtest_run_spec_run UNIQUE (experiment_run_id)
);

CREATE TRIGGER trg_backtest_run_spec_immutable
    BEFORE UPDATE OR DELETE ON research.backtest_run_spec
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

CREATE TABLE research.backtest_trade (
    backtest_trade_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trade_key UUID NOT NULL,
    backtest_run_id BIGINT NOT NULL REFERENCES research.backtest_run(backtest_run_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL,
    symbol TEXT NOT NULL,
    direction TEXT NOT NULL,
    entry_pattern TEXT,
    entry_at TIMESTAMPTZ NOT NULL,
    entry_price NUMERIC(20,8) NOT NULL,
    exit_at TIMESTAMPTZ,
    exit_price NUMERIC(20,8),
    quantity BIGINT NOT NULL,
    gross_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    explicit_costs NUMERIC(24,8) NOT NULL DEFAULT 0,
    net_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
    realized_r NUMERIC(18,8),
    holding_seconds BIGINT,
    exit_reason TEXT,
    ambiguous_bars INTEGER NOT NULL DEFAULT 0,
    cost_breakdown JSONB NOT NULL DEFAULT '{}'::jsonb,
    plan_key TEXT,
    decision_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_backtest_trade_key UNIQUE (trade_key),
    CONSTRAINT ck_backtest_trade_direction CHECK (direction IN ('LONG','SHORT')),
    CONSTRAINT ck_backtest_trade_quantity CHECK (quantity > 0),
    CONSTRAINT ck_backtest_trade_prices CHECK (entry_price >= 0 AND (exit_price IS NULL OR exit_price >= 0)),
    CONSTRAINT ck_backtest_trade_open CHECK (
        (exit_at IS NULL AND exit_price IS NULL) OR (exit_at IS NOT NULL AND exit_price IS NOT NULL))
);

CREATE INDEX ix_backtest_trade_run
    ON research.backtest_trade(backtest_run_id, entry_at, backtest_trade_id);

CREATE TRIGGER trg_backtest_trade_immutable
    BEFORE UPDATE OR DELETE ON research.backtest_trade
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

CREATE TABLE research.backtest_equity_point (
    backtest_equity_point_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    backtest_run_id BIGINT NOT NULL REFERENCES research.backtest_run(backtest_run_id) ON DELETE RESTRICT,
    at TIMESTAMPTZ NOT NULL,
    equity NUMERIC(24,8) NOT NULL,
    cash NUMERIC(24,8) NOT NULL,
    gross_exposure NUMERIC(24,8) NOT NULL DEFAULT 0,
    net_exposure NUMERIC(24,8) NOT NULL DEFAULT 0,
    high_water NUMERIC(24,8) NOT NULL,
    drawdown NUMERIC(24,8) NOT NULL DEFAULT 0,
    drawdown_pct DOUBLE PRECISION,
    open_positions INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_backtest_equity_point UNIQUE (backtest_run_id, at)
);

CREATE INDEX ix_backtest_equity_run ON research.backtest_equity_point(backtest_run_id, at);

CREATE TRIGGER trg_backtest_equity_point_immutable
    BEFORE UPDATE OR DELETE ON research.backtest_equity_point
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

CREATE TABLE research.backtest_rejection (
    backtest_rejection_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    backtest_run_id BIGINT NOT NULL REFERENCES research.backtest_run(backtest_run_id) ON DELETE RESTRICT,
    at TIMESTAMPTZ NOT NULL,
    instrument_id BIGINT NOT NULL,
    direction TEXT NOT NULL,
    reason_code TEXT NOT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_backtest_rejection_direction CHECK (direction IN ('LONG','SHORT'))
);

CREATE INDEX ix_backtest_rejection_run ON research.backtest_rejection(backtest_run_id, at);

CREATE TRIGGER trg_backtest_rejection_immutable
    BEFORE UPDATE OR DELETE ON research.backtest_rejection
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
