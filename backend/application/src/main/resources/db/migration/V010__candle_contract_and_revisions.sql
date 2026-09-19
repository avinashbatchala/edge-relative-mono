-- Edge Relative
-- Flyway V010: canonical candle contract, identity and revision lineage
--
-- DD-05 §91/§92: a canonical candle exposes definition version, close time,
-- completeness and quality state; identity is (instrument, timeframe, open_time,
-- candle_definition_version). DD-05 §105/§106: a corrected candle is a revision of
-- the same logical interval, never a replacement. Corrections are append-only rows
-- with exactly one current row per logical identity.

ALTER TABLE market.candle
    ADD COLUMN candle_definition_version TEXT NOT NULL DEFAULT 'er-m1-base-v1',
    ADD COLUMN close_time TIMESTAMPTZ,
    ADD COLUMN trade_count INTEGER,
    ADD COLUMN vwap NUMERIC(20,8),
    ADD COLUMN is_complete BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN quality_state TEXT NOT NULL DEFAULT 'GOOD',
    ADD COLUMN source_revision TEXT,
    ADD COLUMN revision_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN previous_revision_no INTEGER,
    ADD COLUMN revision_reason TEXT,
    ADD COLUMN recomputed_at TIMESTAMPTZ,
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill the existing M1 rows so the contract columns are populated.
UPDATE market.candle
SET close_time = open_time + INTERVAL '1 minute',
    source_revision = 'groww-m1-backfill-v1',
    recomputed_at = created_at
WHERE close_time IS NULL;

UPDATE market.candle
SET source_revision = 'groww-m1-backfill-v1'
WHERE source_revision IS NULL;

ALTER TABLE market.candle
    ALTER COLUMN close_time SET NOT NULL,
    ALTER COLUMN source_revision SET NOT NULL;

ALTER TABLE market.candle DROP CONSTRAINT uq_candle;
ALTER TABLE market.candle DROP CONSTRAINT ck_candle_revision;
ALTER TABLE market.candle DROP COLUMN revision;

ALTER TABLE market.candle
    ADD CONSTRAINT ck_candle_definition_version_nonblank
        CHECK (btrim(candle_definition_version) <> ''),
    ADD CONSTRAINT ck_candle_source_revision_nonblank
        CHECK (btrim(source_revision) <> ''),
    ADD CONSTRAINT ck_candle_quality_state
        CHECK (quality_state IN ('GOOD','DEGRADED','STALE','INCOMPLETE','SUSPECT','CORRECTED','UNAVAILABLE')),
    ADD CONSTRAINT ck_candle_revision_no CHECK (revision_no > 0),
    ADD CONSTRAINT ck_candle_previous_revision
        CHECK (previous_revision_no IS NULL OR previous_revision_no < revision_no),
    ADD CONSTRAINT ck_candle_close_after_open CHECK (close_time > open_time),
    ADD CONSTRAINT ck_candle_trade_count CHECK (trade_count IS NULL OR trade_count >= 0),
    ADD CONSTRAINT ck_candle_vwap CHECK (vwap IS NULL OR (vwap >= low AND vwap <= high)),
    ADD CONSTRAINT uq_candle_revision
        UNIQUE (instrument_id, timeframe_id, open_time, candle_definition_version, revision_no);

-- At most one current revision per logical candle.
CREATE UNIQUE INDEX uq_candle_one_current
    ON market.candle(instrument_id, timeframe_id, open_time, candle_definition_version)
    WHERE is_current;
