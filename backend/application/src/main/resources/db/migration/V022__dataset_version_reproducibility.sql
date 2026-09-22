-- Edge Relative
-- Flyway V022: dataset_version build/reproducibility fields.
--
-- A committed dataset version must record everything needed to reproduce it and to stop
-- a later experiment from pointing at an unidentified mutable folder. The original
-- research.dataset_version row carried identity, schemas, cutoff, storage URI, manifest
-- URI, row count, checksum and code version. This migration adds the remaining required
-- build metadata: outcome schema version, universe, the covered time range and the exact
-- build parameters, plus a failure payload for abandoned builds.
--
-- The V007 immutability guard is replaced so the new columns are also content-immutable
-- once a version is COMMITTED.

ALTER TABLE research.dataset_version
    ADD COLUMN outcome_schema_version_id BIGINT
        REFERENCES research.outcome_schema_version(outcome_schema_version_id) ON DELETE RESTRICT,
    ADD COLUMN universe TEXT,
    ADD COLUMN start_timestamp TIMESTAMPTZ,
    ADD COLUMN end_timestamp TIMESTAMPTZ,
    ADD COLUMN build_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN failure JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE research.dataset_version
    ADD CONSTRAINT ck_dataset_version_range CHECK (
        end_timestamp IS NULL OR start_timestamp IS NULL OR end_timestamp >= start_timestamp
    ),
    ADD CONSTRAINT ck_dataset_version_universe_nonblank CHECK (
        universe IS NULL OR btrim(universe) <> ''
    );

CREATE OR REPLACE FUNCTION research.protect_dataset_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'COMMITTED' THEN
        IF NEW.dataset_version_id <> OLD.dataset_version_id
           OR NEW.dataset_version_key <> OLD.dataset_version_key
           OR NEW.dataset_id <> OLD.dataset_id
           OR NEW.version <> OLD.version
           OR NEW.feature_schema_version_id IS DISTINCT FROM OLD.feature_schema_version_id
           OR NEW.outcome_schema_version_id IS DISTINCT FROM OLD.outcome_schema_version_id
           OR NEW.point_in_time_cutoff IS DISTINCT FROM OLD.point_in_time_cutoff
           OR NEW.universe IS DISTINCT FROM OLD.universe
           OR NEW.start_timestamp IS DISTINCT FROM OLD.start_timestamp
           OR NEW.end_timestamp IS DISTINCT FROM OLD.end_timestamp
           OR NEW.storage_uri <> OLD.storage_uri
           OR NEW.partition_manifest_uri IS DISTINCT FROM OLD.partition_manifest_uri
           OR NEW.row_count IS DISTINCT FROM OLD.row_count
           OR NEW.checksum IS DISTINCT FROM OLD.checksum
           OR NEW.code_version IS DISTINCT FROM OLD.code_version
           OR NEW.build_parameters IS DISTINCT FROM OLD.build_parameters
           OR NEW.committed_at IS DISTINCT FROM OLD.committed_at
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'committed dataset_version % content is immutable', OLD.dataset_version_id;
        END IF;

        IF NEW.status NOT IN ('COMMITTED','RETIRED') THEN
            RAISE EXCEPTION 'committed dataset_version % may only remain COMMITTED or become RETIRED', OLD.dataset_version_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

ALTER FUNCTION research.protect_dataset_version() SET search_path = pg_catalog;
