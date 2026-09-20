-- Edge Relative
-- Flyway V019: strategy and risk-policy catalog status.
--
-- Strategy/risk version rows are immutable (V007 triggers), so "retire" cannot rewrite a version.
-- The mutable parent rows carry lifecycle status instead; versions remain immutable and are still
-- resolvable by id for reproducibility. No version-table change is made here.

ALTER TABLE control.strategy
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN retired_at TIMESTAMPTZ,
    ADD COLUMN retired_reason TEXT;

ALTER TABLE control.strategy
    ADD CONSTRAINT ck_strategy_status CHECK (status IN ('ACTIVE', 'RETIRED'));

ALTER TABLE control.risk_policy
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN retired_at TIMESTAMPTZ,
    ADD COLUMN retired_reason TEXT;

ALTER TABLE control.risk_policy
    ADD CONSTRAINT ck_risk_policy_status CHECK (status IN ('ACTIVE', 'RETIRED'));

CREATE INDEX ix_strategy_status ON control.strategy(status, code);
CREATE INDEX ix_risk_policy_status ON control.risk_policy(status, code);
