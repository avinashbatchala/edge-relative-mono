-- Edge Relative
-- Flyway V014: deterministic setup engine support.
--
-- The strategy domain models BLOCKED as a distinct terminal state (an explicit policy/safety
-- prohibition, distinct from a rejected setup), so the persisted vocabulary must match it rather
-- than mapping BLOCKED to REJECTED.

ALTER TABLE operational.setup_observation
    DROP CONSTRAINT ck_setup_observation_status;

ALTER TABLE operational.setup_observation
    ADD CONSTRAINT ck_setup_observation_status CHECK (setup_status IN (
        'NONE','WATCH','FORMING','NEAR_TRIGGER','VALID',
        'INVALIDATED','EXPIRED','MISSED','BLOCKED','REJECTED'));

-- Strategy registry for ER_RS_CONTINUATION_V1. The version row is the lineage anchor every setup
-- observation references; it starts in RESEARCH until calibrated.
INSERT INTO control.strategy (strategy_key, code, name, description, setup_family)
VALUES (
    gen_random_uuid(),
    'ER_RS_CONTINUATION_V1',
    'ER RS Continuation V1',
    'Deterministic relative-strength continuation setups on NSE cash equities (intraday).',
    'M5_COMPRESSION_BREAKOUT')
ON CONFLICT (code) DO NOTHING;

INSERT INTO control.strategy_version (
        strategy_id, version, lifecycle_state, primary_timeframe_id,
        feature_schema_version_id, parameters, code_version)
SELECT s.strategy_id,
       1,
       'RESEARCH',
       (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M5'),
       (SELECT fsv.feature_schema_version_id
          FROM control.feature_schema fs
          JOIN control.feature_schema_version fsv ON fsv.feature_schema_id = fs.feature_schema_id
         WHERE fs.code = 'ER_FEATURE_SET'
         ORDER BY fsv.version DESC LIMIT 1),
       '{}'::jsonb,
       'er-strategy-er-rs-continuation-v1'
FROM control.strategy s
WHERE s.code = 'ER_RS_CONTINUATION_V1'
ON CONFLICT (strategy_id, version) DO NOTHING;
