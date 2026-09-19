-- Edge Relative
-- Flyway V008: canonical historical candles, coverage, and backfill runs

CREATE TABLE market.candle (
    candle_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    timeframe_id BIGINT NOT NULL REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    open_time TIMESTAMPTZ NOT NULL,
    open NUMERIC(20,8),
    high NUMERIC(20,8) NOT NULL,
    low NUMERIC(20,8) NOT NULL,
    close NUMERIC(20,8) NOT NULL,
    volume BIGINT NOT NULL DEFAULT 0,
    open_interest NUMERIC(24,8),
    source TEXT,
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_candle UNIQUE (instrument_id, timeframe_id, open_time),
    CONSTRAINT ck_candle_volume CHECK (volume >= 0),
    CONSTRAINT ck_candle_revision CHECK (revision > 0),
    CONSTRAINT ck_candle_prices CHECK (
        high >= low
        AND close <= high
        AND close >= low
        AND (open IS NULL OR (open <= high AND open >= low))
    )
);

CREATE INDEX ix_candle_instrument_time
    ON market.candle(instrument_id, timeframe_id, open_time DESC);

-- Durable per-chunk coverage: the resumable unit of a backfill.
CREATE TABLE market.candle_coverage (
    candle_coverage_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    timeframe_id BIGINT NOT NULL REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    chunk_start TIMESTAMPTZ NOT NULL,
    chunk_end TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    candle_count INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_candle_coverage UNIQUE (instrument_id, timeframe_id, chunk_start, chunk_end),
    CONSTRAINT ck_candle_coverage_range CHECK (chunk_end > chunk_start),
    CONSTRAINT ck_candle_coverage_count CHECK (candle_count >= 0 AND attempts >= 0),
    CONSTRAINT ck_candle_coverage_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);

CREATE INDEX ix_candle_coverage_pending
    ON market.candle_coverage(instrument_id, timeframe_id, status, chunk_start);

CREATE TABLE market.backfill_run (
    backfill_run_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_key UUID NOT NULL,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    timeframe_id BIGINT NOT NULL REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    requested_from TIMESTAMPTZ NOT NULL,
    requested_to TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED',
    total_chunks INTEGER NOT NULL DEFAULT 0,
    completed_chunks INTEGER NOT NULL DEFAULT 0,
    failed_chunks INTEGER NOT NULL DEFAULT 0,
    candles_written BIGINT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_backfill_run_key UNIQUE (run_key),
    CONSTRAINT ck_backfill_run_range CHECK (requested_to >= requested_from),
    CONSTRAINT ck_backfill_run_counts CHECK (total_chunks >= 0 AND completed_chunks >= 0 AND failed_chunks >= 0 AND candles_written >= 0),
    CONSTRAINT ck_backfill_run_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','PARTIAL','FAILED','CANCELLED'))
);

CREATE INDEX ix_backfill_run_instrument
    ON market.backfill_run(instrument_id, created_at DESC);

CREATE TRIGGER trg_candle_coverage_updated_at
    BEFORE UPDATE ON market.candle_coverage
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_backfill_run_updated_at
    BEFORE UPDATE ON market.backfill_run
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
