-- Edge Relative
-- Flyway V004: sparse market observation catalog and ingestion metadata

CREATE TABLE market.market_observation (
    market_observation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    observation_key UUID NOT NULL,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    timeframe_id BIGINT NOT NULL REFERENCES reference.timeframe(timeframe_id) ON DELETE RESTRICT,
    bar_close_timestamp TIMESTAMPTZ NOT NULL,
    market_data_source_id BIGINT REFERENCES reference.market_data_source(market_data_source_id) ON DELETE RESTRICT,
    source_event_timestamp TIMESTAMPTZ,
    received_timestamp TIMESTAMPTZ,
    processed_timestamp TIMESTAMPTZ,
    quality_status TEXT NOT NULL DEFAULT 'GOOD',
    storage_uri TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_market_observation_key UNIQUE (observation_key),
    CONSTRAINT uq_market_observation_semantic UNIQUE (instrument_id, timeframe_id, bar_close_timestamp),
    CONSTRAINT ck_market_observation_quality CHECK (quality_status IN ('GOOD','DEGRADED','STALE','INCOMPLETE','SUSPECT','CORRECTED','UNAVAILABLE')),
    CONSTRAINT ck_market_observation_time_order CHECK (
        (received_timestamp IS NULL OR source_event_timestamp IS NULL OR received_timestamp >= source_event_timestamp)
        AND (processed_timestamp IS NULL OR received_timestamp IS NULL OR processed_timestamp >= received_timestamp)
    )
);

CREATE TABLE market.market_observation_revision (
    market_observation_revision_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_observation_id BIGINT NOT NULL REFERENCES market.market_observation(market_observation_id) ON DELETE RESTRICT,
    revision_no INTEGER NOT NULL,
    quality_status TEXT NOT NULL,
    is_canonical BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    corrected_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_market_observation_revision UNIQUE (market_observation_id, revision_no),
    CONSTRAINT ck_market_observation_revision_no CHECK (revision_no > 0),
    CONSTRAINT ck_market_observation_revision_quality CHECK (quality_status IN ('GOOD','DEGRADED','STALE','INCOMPLETE','SUSPECT','CORRECTED','UNAVAILABLE'))
);

CREATE UNIQUE INDEX uq_market_observation_one_canonical_revision
    ON market.market_observation_revision(market_observation_id)
    WHERE is_canonical;

CREATE TABLE market.market_data_incident (
    market_data_incident_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_key UUID NOT NULL,
    market_data_source_id BIGINT REFERENCES reference.market_data_source(market_data_source_id) ON DELETE RESTRICT,
    instrument_id BIGINT REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    incident_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_market_data_incident_key UNIQUE (incident_key),
    CONSTRAINT ck_market_data_incident_type CHECK (incident_type IN ('MISSING_DATA','DUPLICATE_DATA','IMPOSSIBLE_PRICE','STALE_DATA','TIMESTAMP_ANOMALY','CORRUPT_DATA','SOURCE_OUTAGE','OTHER')),
    CONSTRAINT ck_market_data_incident_severity CHECK (severity IN ('INFO','WARN','ERROR','CRITICAL')),
    CONSTRAINT ck_market_data_incident_times CHECK (resolved_at IS NULL OR resolved_at >= detected_at)
);

CREATE INDEX ix_market_data_incident_open
    ON market.market_data_incident(severity, detected_at DESC)
    WHERE resolved_at IS NULL;

CREATE TABLE market.ingestion_checkpoint (
    ingestion_checkpoint_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_data_source_id BIGINT NOT NULL REFERENCES reference.market_data_source(market_data_source_id) ON DELETE RESTRICT,
    stream_name TEXT NOT NULL,
    partition_key TEXT NOT NULL DEFAULT '',
    checkpoint_value TEXT NOT NULL,
    checkpoint_timestamp TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ingestion_checkpoint UNIQUE (market_data_source_id, stream_name, partition_key),
    CONSTRAINT ck_ingestion_stream_nonblank CHECK (btrim(stream_name) <> ''),
    CONSTRAINT ck_ingestion_checkpoint_nonblank CHECK (btrim(checkpoint_value) <> '')
);
