-- Edge Relative
-- Flyway V016: risk engine support.
--
-- 1. Narrowly scoped reason codes the DD-03 taxonomy does not already name. The candidate taxonomy
--    is expressed in control.risk_reason_code; every RiskDecisionReason FK must resolve there.
-- 2. Candidate-linked reservations. A reservation belongs to a RiskDecision, not to a TradePlan:
--    TradePlan generation and execution are a later milestone. The original schema forced a real
--    trade_plan row, which would require a placeholder plan. Make trade_plan_id nullable and scope
--    uniqueness to the decision so retries remain idempotent.

INSERT INTO control.risk_reason_code (code, reason_type, description) VALUES
    ('INVALID_SETUP', 'REJECTION', 'Setup is not in an authoritative VALID state, or invalidation is missing.'),
    ('MISSING_POLICY', 'REJECTION', 'No applicable and complete risk policy version is available.'),
    ('MISSING_REQUIRED_INPUT', 'REJECTION', 'A required authoritative risk input or producer is unavailable.'),
    ('PROHIBITED_ADDITION', 'REJECTION', 'Adds to existing same-symbol exposure (no pyramiding / no averaging down).'),
    ('MAX_POSITION_NOTIONAL_LIMIT', 'REJECTION', 'Candidate notional exceeds the maximum position notional.'),
    ('BROKER_QUANTITY_LIMIT', 'REJECTION', 'Broker-permitted quantity is unavailable or exceeded.'),
    ('RISK_REDUCED_CONSTRAINT_LIMIT', 'REDUCTION', 'Quantity reduced below the risk-sized quantity by a constraint.')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE operational.risk_reservation
    DROP CONSTRAINT uq_risk_reservation_trade_plan;

ALTER TABLE operational.risk_reservation
    ALTER COLUMN trade_plan_id DROP NOT NULL;

ALTER TABLE operational.risk_reservation
    ADD CONSTRAINT uq_risk_reservation_decision UNIQUE (risk_decision_id);

-- Candidate-linked reservation lineage. When trade_plan_id is present the original plan checks
-- still apply. When it is absent, the decision itself is the authority and the reservation may not
-- exceed the decision's approved quantity/risk/notional.
CREATE OR REPLACE FUNCTION operational.validate_risk_reservation_lineage()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    rd operational.risk_decision%ROWTYPE;
    tp operational.trade_plan%ROWTYPE;
BEGIN
    SELECT * INTO rd FROM operational.risk_decision WHERE risk_decision_id = NEW.risk_decision_id;

    IF rd.broker_account_id <> NEW.broker_account_id THEN
        RAISE EXCEPTION 'risk_reservation lineage mismatch';
    END IF;

    IF NEW.trade_plan_id IS NULL THEN
        IF NEW.reserved_quantity > rd.approved_quantity
           OR NEW.reserved_risk > rd.approved_risk
           OR NEW.reserved_notional > rd.approved_notional THEN
            RAISE EXCEPTION 'risk_reservation exceeds risk_decision authority';
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO tp FROM operational.trade_plan WHERE trade_plan_id = NEW.trade_plan_id;

    IF tp.risk_decision_id <> NEW.risk_decision_id
       OR tp.broker_account_id <> NEW.broker_account_id THEN
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
