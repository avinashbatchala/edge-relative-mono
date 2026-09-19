-- Edge Relative
-- Flyway V007: audit schema, immutability, cross-table lineage validation, and final indexes

CREATE TABLE audit.audit_event (
    audit_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_key UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_reference TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_reference TEXT,
    correlation_id UUID,
    causation_id UUID,
    event_timestamp TIMESTAMPTZ NOT NULL,
    tenant_id BIGINT REFERENCES operational.tenant(tenant_id) ON DELETE RESTRICT,
    broker_account_id BIGINT REFERENCES operational.broker_account(broker_account_id) ON DELETE RESTRICT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_audit_event_key UNIQUE (event_key),
    CONSTRAINT ck_audit_event_type_nonblank CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_audit_event_version CHECK (event_version > 0),
    CONSTRAINT ck_audit_aggregate_type_nonblank CHECK (btrim(aggregate_type) <> ''),
    CONSTRAINT ck_audit_aggregate_reference_nonblank CHECK (btrim(aggregate_reference) <> ''),
    CONSTRAINT ck_audit_actor_type_nonblank CHECK (btrim(actor_type) <> '')
);

CREATE INDEX ix_audit_aggregate
    ON audit.audit_event(aggregate_type, aggregate_reference, event_timestamp);
CREATE INDEX ix_audit_correlation
    ON audit.audit_event(correlation_id, event_timestamp)
    WHERE correlation_id IS NOT NULL;
CREATE INDEX ix_audit_account_time
    ON audit.audit_event(broker_account_id, event_timestamp DESC)
    WHERE broker_account_id IS NOT NULL;

-- Generic immutable-row guard.
CREATE OR REPLACE FUNCTION audit.reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is immutable; % is not allowed', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME, TG_OP
        USING ERRCODE = '55000';
END;
$$;

-- Maintain updated_at (and optimistic record_version where present).
CREATE OR REPLACE FUNCTION audit.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION audit.set_updated_at_and_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    NEW.record_version := OLD.record_version + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tenant_updated_at
    BEFORE UPDATE ON operational.tenant
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON operational.app_user
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_broker_account_updated_at
    BEFORE UPDATE ON operational.broker_account
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_watchlist_updated_at
    BEFORE UPDATE ON operational.watchlist
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_watchlist_item_updated_at
    BEFORE UPDATE ON operational.watchlist_item
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_position_projection_updated_at
    BEFORE UPDATE ON operational.position_projection
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_risk_account_state_updated_at
    BEFORE UPDATE ON operational.risk_account_state
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_trading_control_state_updated_at
    BEFORE UPDATE ON operational.trading_control_state
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_ingestion_checkpoint_updated_at
    BEFORE UPDATE ON market.ingestion_checkpoint
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at();
CREATE TRIGGER trg_order_record_updated_at
    BEFORE UPDATE ON operational.order_record
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at_and_version();
CREATE TRIGGER trg_trade_updated_at
    BEFORE UPDATE ON operational.trade
    FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at_and_version();

-- TRUNCATE bypasses row triggers, so guard append-only tables explicitly.
CREATE OR REPLACE FUNCTION audit.reject_truncate()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; TRUNCATE is not allowed', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

-- Trade plan must be backed by an approving/reducing risk decision and cannot exceed approval.
CREATE OR REPLACE FUNCTION operational.validate_trade_plan_against_risk_decision()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    rd operational.risk_decision%ROWTYPE;
    so operational.setup_observation%ROWTYPE;
BEGIN
    SELECT * INTO rd
    FROM operational.risk_decision
    WHERE risk_decision_id = NEW.risk_decision_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'risk_decision % does not exist', NEW.risk_decision_id;
    END IF;

    IF rd.decision NOT IN ('APPROVE','REDUCE') THEN
        RAISE EXCEPTION 'trade_plan requires APPROVE or REDUCE risk decision; got %', rd.decision;
    END IF;

    IF NEW.planned_quantity > rd.approved_quantity THEN
        RAISE EXCEPTION 'planned quantity % exceeds approved quantity %', NEW.planned_quantity, rd.approved_quantity;
    END IF;

    IF NEW.planned_risk > rd.approved_risk THEN
        RAISE EXCEPTION 'planned risk % exceeds approved risk %', NEW.planned_risk, rd.approved_risk;
    END IF;

    IF NEW.planned_notional > rd.approved_notional THEN
        RAISE EXCEPTION 'planned notional % exceeds approved notional %', NEW.planned_notional, rd.approved_notional;
    END IF;

    IF NEW.setup_observation_id <> rd.setup_observation_id
       OR NEW.tenant_id <> rd.tenant_id
       OR NEW.broker_account_id <> rd.broker_account_id
       OR NEW.strategy_version_id <> rd.strategy_version_id
       OR NEW.instrument_id <> rd.instrument_id THEN
        RAISE EXCEPTION 'trade_plan lineage does not match risk_decision %', rd.risk_decision_id;
    END IF;

    SELECT * INTO so
    FROM operational.setup_observation
    WHERE setup_observation_id = NEW.setup_observation_id;

    IF so.market_observation_id IS DISTINCT FROM NEW.market_observation_id THEN
        RAISE EXCEPTION 'trade_plan market_observation % does not match setup_observation %',
            NEW.market_observation_id, NEW.setup_observation_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_trade_plan_validate_risk_decision
BEFORE INSERT ON operational.trade_plan
FOR EACH ROW EXECUTE FUNCTION operational.validate_trade_plan_against_risk_decision();

-- A trade must preserve the plan's account/instrument/strategy/direction/quantity lineage.
CREATE OR REPLACE FUNCTION operational.validate_trade_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    tp operational.trade_plan%ROWTYPE;
BEGIN
    SELECT * INTO tp
    FROM operational.trade_plan
    WHERE trade_plan_id = NEW.trade_plan_id;

    IF NEW.tenant_id <> tp.tenant_id
       OR NEW.broker_account_id <> tp.broker_account_id
       OR NEW.instrument_id <> tp.instrument_id
       OR NEW.strategy_version_id <> tp.strategy_version_id
       OR NEW.direction <> tp.direction
       OR NEW.planned_quantity <> tp.planned_quantity THEN
        RAISE EXCEPTION 'trade lineage does not match trade_plan %', NEW.trade_plan_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_trade_validate_lineage
BEFORE INSERT ON operational.trade
FOR EACH ROW EXECUTE FUNCTION operational.validate_trade_lineage();

-- An order cannot cross trade/plan/account/instrument lineage.
CREATE OR REPLACE FUNCTION operational.validate_order_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    tr operational.trade%ROWTYPE;
    tp operational.trade_plan%ROWTYPE;
    ba operational.broker_account%ROWTYPE;
BEGIN
    SELECT * INTO tr FROM operational.trade WHERE trade_id = NEW.trade_id;
    SELECT * INTO tp FROM operational.trade_plan WHERE trade_plan_id = NEW.trade_plan_id;
    SELECT * INTO ba FROM operational.broker_account WHERE broker_account_id = NEW.broker_account_id;

    IF tr.trade_plan_id <> NEW.trade_plan_id
       OR tr.tenant_id <> NEW.tenant_id
       OR tr.broker_account_id <> NEW.broker_account_id
       OR tr.instrument_id <> NEW.instrument_id THEN
        RAISE EXCEPTION 'order lineage does not match trade %', NEW.trade_id;
    END IF;

    IF tp.tenant_id <> NEW.tenant_id
       OR tp.broker_account_id <> NEW.broker_account_id
       OR tp.instrument_id <> NEW.instrument_id THEN
        RAISE EXCEPTION 'order lineage does not match trade_plan %', NEW.trade_plan_id;
    END IF;

    IF ba.tenant_id <> NEW.tenant_id OR ba.broker_id <> NEW.broker_id THEN
        RAISE EXCEPTION 'order broker/account/tenant lineage mismatch';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_order_validate_lineage
BEFORE INSERT OR UPDATE OF trade_id, trade_plan_id, tenant_id, broker_account_id, broker_id, instrument_id
ON operational.order_record
FOR EACH ROW EXECUTE FUNCTION operational.validate_order_lineage();

-- Fill evidence must exactly match the owning order/trade/account/instrument/side.
CREATE OR REPLACE FUNCTION operational.validate_fill_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    o operational.order_record%ROWTYPE;
BEGIN
    SELECT * INTO o
    FROM operational.order_record
    WHERE order_id = NEW.order_id;

    IF o.trade_id <> NEW.trade_id
       OR o.tenant_id <> NEW.tenant_id
       OR o.broker_account_id <> NEW.broker_account_id
       OR o.broker_id <> NEW.broker_id
       OR o.instrument_id <> NEW.instrument_id
       OR o.side <> NEW.side THEN
        RAISE EXCEPTION 'fill lineage does not match order %', NEW.order_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_fill_validate_lineage
BEFORE INSERT ON operational.fill
FOR EACH ROW EXECUTE FUNCTION operational.validate_fill_lineage();

-- Broker-account tenant consistency for risk context/decisions and deployment tables.
CREATE OR REPLACE FUNCTION operational.validate_broker_account_tenant()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    account_tenant BIGINT;
BEGIN
    IF NEW.broker_account_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT tenant_id INTO account_tenant
    FROM operational.broker_account
    WHERE broker_account_id = NEW.broker_account_id;

    IF account_tenant IS DISTINCT FROM NEW.tenant_id THEN
        RAISE EXCEPTION 'broker_account % does not belong to tenant %', NEW.broker_account_id, NEW.tenant_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_strategy_deployment_tenant
BEFORE INSERT OR UPDATE OF tenant_id, broker_account_id ON operational.strategy_deployment
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_risk_policy_assignment_tenant
BEFORE INSERT OR UPDATE OF tenant_id, broker_account_id ON operational.risk_policy_assignment
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_model_deployment_tenant
BEFORE INSERT OR UPDATE OF tenant_id, broker_account_id ON operational.model_deployment
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_risk_context_tenant
BEFORE INSERT ON operational.risk_context_snapshot
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_risk_decision_tenant
BEFORE INSERT ON operational.risk_decision
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_trade_plan_tenant
BEFORE INSERT ON operational.trade_plan
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_trade_tenant
BEFORE INSERT OR UPDATE OF tenant_id, broker_account_id ON operational.trade
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_order_tenant
BEFORE INSERT OR UPDATE OF tenant_id, broker_account_id ON operational.order_record
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_fill_tenant
BEFORE INSERT ON operational.fill
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();
CREATE TRIGGER trg_trading_control_tenant
BEFORE INSERT OR UPDATE OF tenant_id, broker_account_id ON operational.trading_control_state
FOR EACH ROW EXECUTE FUNCTION operational.validate_broker_account_tenant();

-- Committed dataset versions are content-immutable. They may only transition COMMITTED -> RETIRED.
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
           OR NEW.point_in_time_cutoff IS DISTINCT FROM OLD.point_in_time_cutoff
           OR NEW.storage_uri <> OLD.storage_uri
           OR NEW.partition_manifest_uri IS DISTINCT FROM OLD.partition_manifest_uri
           OR NEW.row_count IS DISTINCT FROM OLD.row_count
           OR NEW.checksum IS DISTINCT FROM OLD.checksum
           OR NEW.code_version IS DISTINCT FROM OLD.code_version
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

CREATE TRIGGER trg_dataset_version_protect
BEFORE UPDATE ON research.dataset_version
FOR EACH ROW EXECUTE FUNCTION research.protect_dataset_version();

-- Immutable/append-only evidence and version rows.
CREATE TRIGGER trg_feature_version_immutable
BEFORE UPDATE OR DELETE ON control.feature_version
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_feature_schema_version_immutable
BEFORE UPDATE OR DELETE ON control.feature_schema_version
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_feature_schema_member_immutable
BEFORE UPDATE OR DELETE ON control.feature_schema_member
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_strategy_version_immutable
BEFORE UPDATE OR DELETE ON control.strategy_version
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_risk_policy_version_immutable
BEFORE UPDATE OR DELETE ON control.risk_policy_version
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_model_version_immutable
BEFORE UPDATE OR DELETE ON control.model_version
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_market_observation_immutable
BEFORE UPDATE OR DELETE ON market.market_observation
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_market_observation_revision_append_only
BEFORE UPDATE OR DELETE ON market.market_observation_revision
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_portfolio_snapshot_immutable
BEFORE UPDATE OR DELETE ON operational.portfolio_snapshot
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_portfolio_position_snapshot_immutable
BEFORE UPDATE OR DELETE ON operational.portfolio_position_snapshot
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_setup_observation_append_only
BEFORE UPDATE OR DELETE ON operational.setup_observation
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_model_prediction_immutable
BEFORE UPDATE OR DELETE ON operational.model_prediction
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_risk_context_snapshot_immutable
BEFORE UPDATE OR DELETE ON operational.risk_context_snapshot
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_risk_decision_immutable
BEFORE UPDATE OR DELETE ON operational.risk_decision
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_risk_decision_reason_immutable
BEFORE UPDATE OR DELETE ON operational.risk_decision_reason
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_trade_plan_immutable
BEFORE UPDATE OR DELETE ON operational.trade_plan
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_order_event_append_only
BEFORE UPDATE OR DELETE ON operational.order_event
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_fill_append_only
BEFORE UPDATE OR DELETE ON operational.fill
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_audit_event_append_only
BEFORE UPDATE OR DELETE ON audit.audit_event
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_trade_outcome_immutable
BEFORE UPDATE OR DELETE ON operational.trade_outcome
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();
CREATE TRIGGER trg_recommendation_append_only
BEFORE UPDATE OR DELETE ON operational.recommendation
FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

-- Deleting a dataset version after publication is never allowed.
CREATE OR REPLACE FUNCTION research.reject_dataset_version_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN ('COMMITTED','RETIRED') THEN
        RAISE EXCEPTION 'published dataset_version % cannot be deleted', OLD.dataset_version_id;
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER trg_dataset_version_no_published_delete
BEFORE DELETE ON research.dataset_version
FOR EACH ROW EXECUTE FUNCTION research.reject_dataset_version_delete();

-- Remaining hot-path indexes justified by DD-04B query inventory.
CREATE INDEX ix_strategy_deployment_active
    ON operational.strategy_deployment(tenant_id, broker_account_id, strategy_version_id)
    WHERE enabled AND valid_to IS NULL;
CREATE INDEX ix_model_deployment_active
    ON operational.model_deployment(tenant_id, broker_account_id, strategy_version_id)
    WHERE enabled AND valid_to IS NULL;
CREATE INDEX ix_risk_account_state_date
    ON operational.risk_account_state(trading_date, broker_account_id);
CREATE INDEX ix_order_unknown
    ON operational.order_record(broker_account_id, created_at DESC)
    WHERE status = 'UNKNOWN';

-- RiskDecision duplicated lineage columns are intentional for explainability/query speed,
-- but they must agree with the immutable setup and risk-context evidence.
CREATE OR REPLACE FUNCTION operational.validate_risk_decision_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    so operational.setup_observation%ROWTYPE;
    rc operational.risk_context_snapshot%ROWTYPE;
    mp operational.model_prediction%ROWTYPE;
BEGIN
    SELECT * INTO so
    FROM operational.setup_observation
    WHERE setup_observation_id = NEW.setup_observation_id;

    SELECT * INTO rc
    FROM operational.risk_context_snapshot
    WHERE risk_context_snapshot_id = NEW.risk_context_snapshot_id;

    IF NEW.tenant_id <> so.tenant_id
       OR NEW.broker_account_id IS DISTINCT FROM so.broker_account_id
       OR NEW.strategy_version_id <> so.strategy_version_id
       OR NEW.instrument_id <> so.instrument_id THEN
        RAISE EXCEPTION 'risk_decision lineage does not match setup_observation %', NEW.setup_observation_id;
    END IF;

    IF NEW.tenant_id <> rc.tenant_id
       OR NEW.broker_account_id <> rc.broker_account_id
       OR NEW.risk_policy_version_id <> rc.risk_policy_version_id THEN
        RAISE EXCEPTION 'risk_decision lineage does not match risk_context_snapshot %', NEW.risk_context_snapshot_id;
    END IF;

    IF NEW.model_prediction_id IS NOT NULL THEN
        SELECT * INTO mp
        FROM operational.model_prediction
        WHERE model_prediction_id = NEW.model_prediction_id;

        IF mp.setup_observation_id <> NEW.setup_observation_id THEN
            RAISE EXCEPTION 'model_prediction % does not belong to setup_observation %', NEW.model_prediction_id, NEW.setup_observation_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_risk_decision_validate_lineage
BEFORE INSERT ON operational.risk_decision
FOR EACH ROW EXECUTE FUNCTION operational.validate_risk_decision_lineage();

CREATE OR REPLACE FUNCTION operational.validate_risk_reservation_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    rd operational.risk_decision%ROWTYPE;
    tp operational.trade_plan%ROWTYPE;
BEGIN
    SELECT * INTO rd FROM operational.risk_decision WHERE risk_decision_id = NEW.risk_decision_id;
    SELECT * INTO tp FROM operational.trade_plan WHERE trade_plan_id = NEW.trade_plan_id;

    IF tp.risk_decision_id <> NEW.risk_decision_id
       OR tp.broker_account_id <> NEW.broker_account_id
       OR rd.broker_account_id <> NEW.broker_account_id THEN
        RAISE EXCEPTION 'risk_reservation lineage mismatch';
    END IF;

    IF NEW.reserved_quantity > tp.planned_quantity
       OR NEW.reserved_risk > tp.planned_risk
       OR NEW.reserved_notional > tp.planned_notional THEN
        RAISE EXCEPTION 'risk_reservation exceeds trade_plan authority';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_risk_reservation_validate_lineage
BEFORE INSERT OR UPDATE OF broker_account_id, risk_decision_id, trade_plan_id, reserved_quantity, reserved_risk, reserved_notional
ON operational.risk_reservation
FOR EACH ROW EXECUTE FUNCTION operational.validate_risk_reservation_lineage();

-- A setup observation must reference a consistent market observation.
CREATE OR REPLACE FUNCTION operational.validate_setup_observation_observation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    mo market.market_observation%ROWTYPE;
BEGIN
    SELECT * INTO mo
    FROM market.market_observation
    WHERE market_observation_id = NEW.market_observation_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'market_observation % does not exist', NEW.market_observation_id;
    END IF;

    IF mo.instrument_id <> NEW.instrument_id THEN
        RAISE EXCEPTION 'setup_observation instrument % does not match market_observation instrument %',
            NEW.instrument_id, mo.instrument_id;
    END IF;

    IF NEW.observed_at < mo.bar_close_timestamp THEN
        RAISE EXCEPTION 'setup_observation observed_at % precedes market_observation bar close %',
            NEW.observed_at, mo.bar_close_timestamp;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_setup_observation_observation
BEFORE INSERT ON operational.setup_observation
FOR EACH ROW EXECUTE FUNCTION operational.validate_setup_observation_observation();

-- Derivative-only attributes must belong to derivative instruments with a sound underlying.
CREATE OR REPLACE FUNCTION reference.validate_derivative_contract()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    contract_type TEXT;
    underlying_type TEXT;
BEGIN
    SELECT instrument_type INTO contract_type
    FROM reference.instrument
    WHERE instrument_id = NEW.instrument_id;

    SELECT instrument_type INTO underlying_type
    FROM reference.instrument
    WHERE instrument_id = NEW.underlying_instrument_id;

    IF contract_type NOT IN ('FUTURE','OPTION') THEN
        RAISE EXCEPTION 'derivative_contract instrument % must be FUTURE or OPTION; got %',
            NEW.instrument_id, contract_type;
    END IF;

    IF underlying_type NOT IN ('EQUITY','INDEX','ETF') THEN
        RAISE EXCEPTION 'derivative_contract underlying % must be EQUITY, INDEX or ETF; got %',
            NEW.underlying_instrument_id, underlying_type;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_derivative_contract_type
BEFORE INSERT OR UPDATE OF instrument_id, underlying_instrument_id ON reference.derivative_contract
FOR EACH ROW EXECUTE FUNCTION reference.validate_derivative_contract();

-- Dataset lineage must remain an acyclic graph.
CREATE OR REPLACE FUNCTION research.validate_dataset_version_input()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    creates_cycle BOOLEAN;
BEGIN
    WITH RECURSIVE chain(dataset_version_id) AS (
        SELECT NEW.input_dataset_version_id
        UNION
        SELECT dvi.input_dataset_version_id
        FROM research.dataset_version_input dvi
        JOIN chain c ON dvi.dataset_version_id = c.dataset_version_id
    )
    SELECT EXISTS (SELECT 1 FROM chain WHERE dataset_version_id = NEW.dataset_version_id)
    INTO creates_cycle;

    IF creates_cycle THEN
        RAISE EXCEPTION 'dataset_version_input would create a cycle between % and %',
            NEW.dataset_version_id, NEW.input_dataset_version_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dataset_version_input_acyclic
BEFORE INSERT OR UPDATE ON research.dataset_version_input
FOR EACH ROW EXECUTE FUNCTION research.validate_dataset_version_input();

-- TRUNCATE guards for append-only / immutable evidence.
CREATE TRIGGER trg_feature_version_no_truncate
    BEFORE TRUNCATE ON control.feature_version FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_feature_schema_version_no_truncate
    BEFORE TRUNCATE ON control.feature_schema_version FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_feature_schema_member_no_truncate
    BEFORE TRUNCATE ON control.feature_schema_member FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_strategy_version_no_truncate
    BEFORE TRUNCATE ON control.strategy_version FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_risk_policy_version_no_truncate
    BEFORE TRUNCATE ON control.risk_policy_version FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_model_version_no_truncate
    BEFORE TRUNCATE ON control.model_version FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_market_observation_no_truncate
    BEFORE TRUNCATE ON market.market_observation FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_market_observation_revision_no_truncate
    BEFORE TRUNCATE ON market.market_observation_revision FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_portfolio_snapshot_no_truncate
    BEFORE TRUNCATE ON operational.portfolio_snapshot FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_portfolio_position_snapshot_no_truncate
    BEFORE TRUNCATE ON operational.portfolio_position_snapshot FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_setup_observation_no_truncate
    BEFORE TRUNCATE ON operational.setup_observation FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_model_prediction_no_truncate
    BEFORE TRUNCATE ON operational.model_prediction FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_risk_context_snapshot_no_truncate
    BEFORE TRUNCATE ON operational.risk_context_snapshot FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_risk_decision_no_truncate
    BEFORE TRUNCATE ON operational.risk_decision FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_risk_decision_reason_no_truncate
    BEFORE TRUNCATE ON operational.risk_decision_reason FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_trade_plan_no_truncate
    BEFORE TRUNCATE ON operational.trade_plan FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_order_event_no_truncate
    BEFORE TRUNCATE ON operational.order_event FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_fill_no_truncate
    BEFORE TRUNCATE ON operational.fill FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_trade_outcome_no_truncate
    BEFORE TRUNCATE ON operational.trade_outcome FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_recommendation_no_truncate
    BEFORE TRUNCATE ON operational.recommendation FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();
CREATE TRIGGER trg_audit_event_no_truncate
    BEFORE TRUNCATE ON audit.audit_event FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_truncate();

-- Foreign-key supporting indexes on hot paths (PostgreSQL does not create these automatically).
CREATE INDEX ix_model_prediction_model
    ON operational.model_prediction(model_version_id);
CREATE INDEX ix_risk_decision_prediction
    ON operational.risk_decision(model_prediction_id)
    WHERE model_prediction_id IS NOT NULL;
CREATE INDEX ix_fill_broker
    ON operational.fill(broker_id);
CREATE INDEX ix_portfolio_position_snapshot_instrument
    ON operational.portfolio_position_snapshot(instrument_id);

-- Harden trigger/guard functions against search_path hijacking. All bodies are
-- schema-qualified, so restricting to pg_catalog is safe.
DO $$
DECLARE
    routine RECORD;
BEGIN
    FOR routine IN
        SELECT n.nspname, p.proname, pg_get_function_identity_arguments(p.oid) AS args
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname IN ('reference', 'operational', 'research', 'audit')
          AND p.prokind = 'f'
    LOOP
        EXECUTE format(
            'ALTER FUNCTION %I.%I(%s) SET search_path = pg_catalog',
            routine.nspname, routine.proname, routine.args);
    END LOOP;
END
$$;
