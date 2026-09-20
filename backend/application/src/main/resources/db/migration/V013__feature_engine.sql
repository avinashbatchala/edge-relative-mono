-- Edge Relative
-- Flyway V013: deterministic feature snapshots (DD-05 §120-§158).
--
-- Scoped exception to DD04B §19 ("no one row per feature per observation"): for the current
-- single-operator, <50-instrument scope the feature snapshot header/value tables are kept in
-- PostgreSQL so live, replay and research share one reconstructable store. See ADR-002. Only derived,
-- rebuildable data is stored; rows are append-only and immutable.

CREATE TABLE market.feature_snapshot
(
    feature_snapshot_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_key           UUID        NOT NULL,
    instrument_id          BIGINT      NOT NULL REFERENCES reference.instrument (instrument_id) ON DELETE RESTRICT,
    timeframe_id           BIGINT      NOT NULL REFERENCES reference.timeframe (timeframe_id) ON DELETE RESTRICT,
    anchor_timestamp       TIMESTAMPTZ NOT NULL,
    feature_schema_version TEXT        NOT NULL,
    calculation_version    TEXT        NOT NULL,
    snapshot_quality       TEXT        NOT NULL,
    snapshot_availability  TEXT        NOT NULL,
    market_instrument_id   BIGINT REFERENCES reference.instrument (instrument_id) ON DELETE RESTRICT,
    sector_id              BIGINT REFERENCES reference.sector (sector_id) ON DELETE RESTRICT,
    sector_instrument_id   BIGINT REFERENCES reference.instrument (instrument_id) ON DELETE RESTRICT,
    source_data_revision   TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_snapshot_key UNIQUE (snapshot_key),
    CONSTRAINT uq_feature_snapshot_semantic UNIQUE (
                                                    instrument_id, timeframe_id, anchor_timestamp,
                                                    feature_schema_version, calculation_version),
    CONSTRAINT ck_feature_snapshot_quality CHECK (
        snapshot_quality IN ('GOOD', 'DEGRADED', 'STALE', 'INCOMPLETE', 'SUSPECT', 'CORRECTED', 'UNAVAILABLE')),
    CONSTRAINT ck_feature_snapshot_availability CHECK (
        snapshot_availability IN
        ('VALID', 'WARMING_UP', 'INSUFFICIENT_HISTORY', 'MISSING_INPUT', 'STALE', 'INCOMPLETE', 'INVALID',
         'NOT_APPLICABLE')),
    CONSTRAINT ck_feature_snapshot_schema_nonblank CHECK (btrim(feature_schema_version) <> ''),
    CONSTRAINT ck_feature_snapshot_calculation_nonblank CHECK (btrim(calculation_version) <> '')
);

CREATE INDEX ix_feature_snapshot_series
    ON market.feature_snapshot (instrument_id, timeframe_id, anchor_timestamp DESC);

CREATE TABLE market.feature_snapshot_value
(
    feature_snapshot_value_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_snapshot_id       BIGINT  NOT NULL REFERENCES market.feature_snapshot (feature_snapshot_id) ON DELETE RESTRICT,
    feature_code              TEXT    NOT NULL,
    feature_version           TEXT    NOT NULL,
    parameter_hash            TEXT    NOT NULL,
    ordinal                   INTEGER NOT NULL,
    value                     DOUBLE PRECISION,
    label                     TEXT,
    quality                   TEXT    NOT NULL,
    availability              TEXT    NOT NULL,
    lineage                   JSONB   NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_feature_snapshot_value UNIQUE (feature_snapshot_id, feature_code, feature_version, parameter_hash),
    CONSTRAINT uq_feature_snapshot_value_ordinal UNIQUE (feature_snapshot_id, ordinal),
    CONSTRAINT ck_feature_snapshot_value_ordinal CHECK (ordinal > 0),
    CONSTRAINT ck_feature_snapshot_value_code_nonblank CHECK (btrim(feature_code) <> ''),
    CONSTRAINT ck_feature_snapshot_value_quality CHECK (
        quality IN ('GOOD', 'DEGRADED', 'STALE', 'INCOMPLETE', 'SUSPECT', 'CORRECTED', 'UNAVAILABLE')),
    CONSTRAINT ck_feature_snapshot_value_availability CHECK (
        availability IN
        ('VALID', 'WARMING_UP', 'INSUFFICIENT_HISTORY', 'MISSING_INPUT', 'STALE', 'INCOMPLETE', 'INVALID',
         'NOT_APPLICABLE')),
    CONSTRAINT ck_feature_snapshot_value_shape CHECK (
        (availability = 'VALID' AND (value IS NOT NULL OR label IS NOT NULL))
            OR (availability <> 'VALID' AND value IS NULL AND label IS NULL))
);

CREATE INDEX ix_feature_snapshot_value_code
    ON market.feature_snapshot_value (feature_code, feature_snapshot_id);

-- Feature snapshots are derived and rebuildable, never mutated in place (DD-05 §39/§105).
CREATE FUNCTION market.reject_feature_snapshot_mutation() RETURNS trigger AS $$
BEGIN
    RAISE
EXCEPTION 'feature snapshots are immutable';
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_feature_snapshot_immutable
    BEFORE UPDATE OR
DELETE
ON market.feature_snapshot
    FOR EACH ROW EXECUTE FUNCTION market.reject_feature_snapshot_mutation();

CREATE TRIGGER trg_feature_snapshot_value_immutable
    BEFORE UPDATE OR
DELETE
ON market.feature_snapshot_value
    FOR EACH ROW EXECUTE FUNCTION market.reject_feature_snapshot_mutation();
