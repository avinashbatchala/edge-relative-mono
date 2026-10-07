-- Edge Relative
-- Repeatable grants template.
-- Intentionally guarded so migrations do not fail before infrastructure provisions roles.
-- Production IAM should own role creation and credential management.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'edge_java') THEN
        GRANT USAGE ON SCHEMA reference, control, market, operational, research, audit, fundamental TO edge_java;
        GRANT SELECT ON ALL TABLES IN SCHEMA reference, control, research TO edge_java;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA operational TO edge_java;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA market TO edge_java;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA fundamental TO edge_java;
        GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA audit TO edge_java;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA market, operational, audit, fundamental TO edge_java;

        -- Append-only/immutable evidence: the runtime role may append but never
        -- mutate or delete. Row triggers also enforce this; these grants make it
        -- explicit at the privilege layer.
        REVOKE UPDATE, DELETE ON
            operational.fill,
            operational.order_event,
            operational.trade_plan,
            operational.risk_decision,
            operational.risk_decision_reason,
            operational.risk_context_snapshot,
            operational.portfolio_snapshot,
            operational.portfolio_position_snapshot,
            operational.setup_observation,
            operational.model_prediction,
            operational.trade_outcome,
            operational.recommendation,
            market.market_observation,
            market.market_observation_revision,
            audit.audit_event
        FROM edge_java;

        ALTER DEFAULT PRIVILEGES IN SCHEMA operational GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO edge_java;
        ALTER DEFAULT PRIVILEGES IN SCHEMA market GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO edge_java;
        ALTER DEFAULT PRIVILEGES IN SCHEMA fundamental GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO edge_java;
        ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT, INSERT ON TABLES TO edge_java;
        ALTER DEFAULT PRIVILEGES IN SCHEMA market, operational, audit, fundamental GRANT USAGE, SELECT ON SEQUENCES TO edge_java;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'edge_python') THEN
        GRANT USAGE ON SCHEMA reference, control, market, operational, research, fundamental TO edge_python;
        GRANT SELECT ON ALL TABLES IN SCHEMA reference, control, market, operational, fundamental TO edge_python;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA research TO edge_python;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA research TO edge_python;

        ALTER DEFAULT PRIVILEGES IN SCHEMA research GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO edge_python;
        ALTER DEFAULT PRIVILEGES IN SCHEMA research GRANT USAGE, SELECT ON SEQUENCES TO edge_python;
    END IF;
END
$$;
