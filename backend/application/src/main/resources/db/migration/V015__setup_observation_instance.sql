-- Edge Relative
-- Flyway V015: semantic idempotency for setup observations.
--
-- A setup instance begins only when the lifecycle starts (NONE -> WATCH). Its identity is derived
-- deterministically, but rows before that point carry no instance. Re-evaluating the same
-- market observation must not append a duplicate, so uniqueness is enforced on the semantic tuple
-- rather than on a random surrogate key. A zero UUID stands in for a NULL instance so the unique
-- index treats two pre-instance rows for the same bar/state as equal.

ALTER TABLE operational.setup_observation
    ADD COLUMN setup_instance_id UUID;

ALTER TABLE operational.setup_observation
    ADD COLUMN initialization_reason TEXT;

ALTER TABLE operational.setup_observation
    ADD CONSTRAINT ck_setup_observation_initialization CHECK (
        initialization_reason IS NULL
        OR initialization_reason IN ('LIFECYCLE_START', 'COLD_START_RECONSTRUCTION'));

CREATE UNIQUE INDEX uq_setup_observation_semantic
    ON operational.setup_observation (
        strategy_version_id,
        instrument_id,
        market_observation_id,
        direction,
        setup_status,
        COALESCE(setup_instance_id, '00000000-0000-0000-0000-000000000000'::uuid));

CREATE INDEX ix_setup_observation_instance
    ON operational.setup_observation(setup_instance_id)
    WHERE setup_instance_id IS NOT NULL;

COMMENT ON COLUMN operational.setup_observation.setup_instance_id IS
    'Deterministic setup instance identity; NULL until the lifecycle starts (NONE -> WATCH).';
