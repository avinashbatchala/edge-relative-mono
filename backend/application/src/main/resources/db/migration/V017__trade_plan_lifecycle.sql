-- Edge Relative
-- Flyway V017: TradePlan validity and lifecycle.
--
-- The V005 trade_plan row is the immutable intent payload (constraints + approval triggers preserved).
-- DD01/DD02 require a bounded validity window and event-based lifecycle, but the original schema has
-- no validity columns and is immutable by trigger. This migration adds the validity inputs as
-- immutable columns and a separate append-only event stream, so expiration/invalidation/cancellation/
-- supersession never rewrite the original intent.
--
-- No universal TTL is seeded: valid_from/expires_at/entry_cutoff_at are supplied by versioned
-- strategy/risk policy and remain NULL when that policy is not configured.

ALTER TABLE operational.trade_plan
    ADD COLUMN valid_from TIMESTAMPTZ,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN entry_cutoff_at TIMESTAMPTZ,
    ADD COLUMN entry_trigger_price NUMERIC(20,8),
    ADD COLUMN no_chase_price NUMERIC(20,8),
    ADD COLUMN no_chase_basis TEXT,
    ADD COLUMN stop_buffer_method TEXT,
    ADD COLUMN target_rationale TEXT,
    ADD COLUMN explanation TEXT,
    ADD COLUMN feature_schema_version TEXT,
    ADD COLUMN plan_policy_reference TEXT,
    ADD COLUMN market_regime TEXT,
    ADD COLUMN sector_code TEXT,
    ADD COLUMN tick_size NUMERIC(20,8),
    ADD COLUMN quantity_increment BIGINT,

    ADD CONSTRAINT ck_trade_plan_quantity_increment CHECK (quantity_increment IS NULL OR quantity_increment > 0);

ALTER TABLE operational.trade_plan
    ADD CONSTRAINT ck_trade_plan_validity CHECK (
        (valid_from IS NULL OR expires_at IS NULL OR expires_at > valid_from)
        AND (entry_cutoff_at IS NULL OR valid_from IS NULL OR entry_cutoff_at >= valid_from)
    );

ALTER TABLE operational.trade_plan
    ADD CONSTRAINT ck_trade_plan_no_chase CHECK (no_chase_price IS NULL OR no_chase_price >= 0);

COMMENT ON COLUMN operational.trade_plan.structural_invalidation IS
    'Thesis invalidation level (DD02 section 86). Distinct from protective_stop.';
COMMENT ON COLUMN operational.trade_plan.valid_from IS
    'Start of plan validity; set from the approved decision as-of time.';
COMMENT ON COLUMN operational.trade_plan.expires_at IS
    'Trigger lifetime bound from versioned policy; NULL when no policy is configured.';

CREATE TABLE operational.trade_plan_event (
    trade_plan_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_key UUID NOT NULL,
    trade_plan_id BIGINT NOT NULL REFERENCES operational.trade_plan(trade_plan_id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_plan_event_key UNIQUE (event_key),
    CONSTRAINT ck_trade_plan_event_type CHECK (event_type IN (
        'CREATED','ELIGIBLE','EXPIRED','INVALIDATED','CANCELLED','SUPERSEDED','EXECUTION_PROGRESS'))
);

CREATE INDEX ix_trade_plan_event_plan
    ON operational.trade_plan_event(trade_plan_id, occurred_at DESC, trade_plan_event_id DESC);

CREATE TRIGGER trg_trade_plan_event_immutable
    BEFORE UPDATE OR DELETE ON operational.trade_plan_event
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

CREATE TRIGGER trg_trade_plan_event_no_truncate
    BEFORE TRUNCATE ON operational.trade_plan_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();

CREATE INDEX ix_trade_plan_expiry
    ON operational.trade_plan(expires_at)
    WHERE expires_at IS NOT NULL;
