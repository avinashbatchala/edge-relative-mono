-- Edge Relative
-- Flyway V021: versioned corporate-action adjustment factors
--
-- DD-05 §113: a corporate-action adjustment dataset must expose versioned factors sufficient to
-- create consistent adjusted series, while raw prices are never overwritten (§112). The factor is
-- explicit here rather than inferred from the ambiguous "a:b" ratio notation: the reference source
-- states the price and quantity transform for the action.
--
-- Only SPLIT and BONUS are supported for the analytical back-adjusted series today. An action of any
-- other type, or a supported action without an available factor, is treated as unsupported by the
-- adjustment read path and fails closed instead of silently crossing the action (DD-05 §43/§260).
--
-- available_at is the point-in-time coordinate: a factor is applied to an as-of read only if it was
-- known at that instant, so a later announcement cannot leak into a contemporaneous view (§260).

CREATE TABLE reference.corporate_action_factor (
    corporate_action_factor_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    corporate_action_id BIGINT NOT NULL REFERENCES reference.corporate_action(corporate_action_id) ON DELETE CASCADE,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    factor_version INTEGER NOT NULL DEFAULT 1,
    price_factor NUMERIC(24,8) NOT NULL,
    quantity_factor NUMERIC(24,8) NOT NULL,
    definition TEXT NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ca_factor_action_version UNIQUE (corporate_action_id, factor_version),
    CONSTRAINT ck_ca_factor_price CHECK (price_factor > 0),
    CONSTRAINT ck_ca_factor_quantity CHECK (quantity_factor > 0),
    CONSTRAINT ck_ca_factor_version CHECK (factor_version > 0),
    CONSTRAINT ck_ca_factor_definition CHECK (btrim(definition) <> '')
);

CREATE INDEX ix_ca_factor_instrument
    ON reference.corporate_action_factor(instrument_id, available_at);
