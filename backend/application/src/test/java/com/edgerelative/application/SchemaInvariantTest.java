package com.edgerelative.application;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the database-level invariants described in DD-04B section 21.
 * Flyway applies the migrations when the Spring context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SchemaInvariantTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("schema_invariant")
            .withUsername("schema_invariant")
            .withPassword("schema_invariant");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
    }

    private static boolean seeded;

    @Autowired
    private DSLContext database;

    @BeforeEach
    void seedOnce() {
        if (seeded) {
            return;
        }
        seed();
        seeded = true;
    }

    private void seed() {
        run("""
                INSERT INTO reference.exchange (exchange_id, code, name, timezone, currency_code)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'NSE', 'National Stock Exchange', 'Asia/Kolkata', 'INR')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO reference.timeframe (timeframe_id, code, duration_seconds, calendar_based)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'M5', 300, FALSE)
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO reference.instrument
                    (instrument_id, instrument_key, exchange_id, instrument_type, segment, canonical_symbol, currency_code, tick_size, lot_size, trading_status)
                OVERRIDING SYSTEM VALUE
                VALUES
                    (1, gen_random_uuid(), 1, 'EQUITY', 'EQ', 'RELIANCE', 'INR', 0.05, 1, 'ACTIVE'),
                    (2, gen_random_uuid(), 1, 'INDEX', 'IDX', 'NIFTY50', 'INR', 0.05, 1, 'ACTIVE')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO reference.sector (sector_id, code, name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'ENERGY', 'Energy'), (2, 'IT', 'Information Technology')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO reference.instrument_sector_history (instrument_id, sector_id, valid_from)
                VALUES (1, 1, DATE '2024-01-01')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO reference.broker (broker_id, code, name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'TESTBROKER', 'Test Broker')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.tenant (tenant_id, tenant_key, name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 'Operator')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.app_user (app_user_id, user_key, tenant_id, display_name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 'Operator')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.broker_account
                    (broker_account_id, broker_account_key, tenant_id, app_user_id, broker_id, external_account_id, secret_reference)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, 1, 'ACC-1', 'secret://test')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO control.strategy (strategy_id, strategy_key, code, name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 'ER_RS_CONTINUATION', 'RS Continuation')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO control.strategy_version (strategy_version_id, strategy_id, version, lifecycle_state)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 1, 1, 'RESEARCH')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO control.risk_policy (risk_policy_id, risk_policy_key, code, name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 'DEFAULT', 'Default')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO control.risk_policy_version
                    (risk_policy_version_id, risk_policy_id, version, lifecycle_state, parameters)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 1, 1, 'VALIDATED', '{}'::jsonb)
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO market.market_observation
                    (market_observation_id, observation_key, instrument_id, timeframe_id, bar_close_timestamp, quality_status)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, TIMESTAMPTZ '2024-06-03 10:20:00+05:30', 'GOOD')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.watchlist (watchlist_id, watchlist_key, tenant_id, app_user_id, name)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, 'Primary')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.setup_observation
                    (setup_observation_id, setup_observation_key, tenant_id, broker_account_id, market_observation_id,
                     strategy_version_id, instrument_id, observed_at, direction, setup_status)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, 1, 1, 1,
                        TIMESTAMPTZ '2024-06-03 10:21:00+05:30', 'LONG', 'VALID')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.risk_context_snapshot
                    (risk_context_snapshot_id, risk_context_key, tenant_id, broker_account_id, trading_date, captured_at,
                     risk_policy_version_id, risk_reference_equity, current_equity,
                     portfolio_open_risk, portfolio_stress_risk, gross_exposure, net_exposure,
                     session_pnl, session_drawdown, risk_state, broker_health, data_health, reconciliation_state)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, DATE '2024-06-03', TIMESTAMPTZ '2024-06-03 10:21:00+05:30',
                        1, 1000000, 1000000, 0, 0, 0, 0, 0, 0, 'NORMAL', 'HEALTHY', 'GOOD', 'MATCHED')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.risk_decision
                    (risk_decision_id, decision_key, setup_observation_id, risk_context_snapshot_id, tenant_id,
                     broker_account_id, strategy_version_id, risk_policy_version_id, instrument_id, decision_at,
                     decision, requested_quantity, approved_quantity, requested_risk, approved_risk, approved_notional)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 1, TIMESTAMPTZ '2024-06-03 10:21:30+05:30',
                        'APPROVE', 10, 10, 100, 100, 1000)
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO operational.trade_plan
                    (trade_plan_id, trade_plan_key, risk_decision_id, setup_observation_id, tenant_id, broker_account_id,
                     strategy_version_id, instrument_id, market_observation_id, direction, planned_quantity,
                     entry_low, entry_high, structural_invalidation, protective_stop, target_reference,
                     planned_risk, planned_notional)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 1, 'LONG', 10,
                        100, 101, 99, 98, 110, 100, 1000)
                ON CONFLICT DO NOTHING""");
    }

    @Test
    void watchlistSlotIsCappedAtTwenty() {
        assertThatThrownBy(() -> run("""
                INSERT INTO operational.watchlist_item (watchlist_item_id, watchlist_id, instrument_id, slot)
                OVERRIDING SYSTEM VALUE
                VALUES (100, 1, 1, 21)"""))
                .hasMessageContaining("ck_watchlist_item_slot");
    }

    @Test
    void oneActiveWatchlistPerOwner() {
        assertThatThrownBy(() -> run("""
                INSERT INTO operational.watchlist (watchlist_id, watchlist_key, tenant_id, app_user_id, name)
                OVERRIDING SYSTEM VALUE
                VALUES (100, gen_random_uuid(), 1, 1, 'Secondary')"""))
                .hasMessageContaining("uq_watchlist_one_active_per_owner");
    }

    @Test
    void overlappingSectorMappingIsRejected() {
        assertThatThrownBy(() -> run("""
                INSERT INTO reference.instrument_sector_history (instrument_id, sector_id, valid_from)
                VALUES (1, 2, DATE '2024-06-01')"""))
                .hasMessageContaining("ex_instrument_sector_no_overlap");
    }

    @Test
    void appendOnlyRiskDecisionCannotBeUpdated() {
        assertThatThrownBy(() -> run("""
                UPDATE operational.risk_decision SET approved_quantity = 5 WHERE risk_decision_id = 1"""))
                .hasMessageContaining("immutable");
    }

    @Test
    void appendOnlyAuditEventCannotBeTruncated() {
        assertThatThrownBy(() -> run("TRUNCATE audit.audit_event"))
                .hasMessageContaining("append-only");
    }

    @Test
    void rejectedRiskDecisionCannotCreateTradePlan() {
        run("""
                INSERT INTO operational.risk_decision
                    (risk_decision_id, decision_key, setup_observation_id, risk_context_snapshot_id, tenant_id,
                     broker_account_id, strategy_version_id, risk_policy_version_id, instrument_id, decision_at,
                     decision, requested_quantity, approved_quantity, requested_risk, approved_risk, approved_notional)
                OVERRIDING SYSTEM VALUE
                VALUES (2, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 1, TIMESTAMPTZ '2024-06-03 10:22:00+05:30',
                        'REJECT', 10, 0, 100, 0, 0)
                ON CONFLICT DO NOTHING""");

        assertThatThrownBy(() -> run("""
                INSERT INTO operational.trade_plan
                    (trade_plan_id, trade_plan_key, risk_decision_id, setup_observation_id, tenant_id, broker_account_id,
                     strategy_version_id, instrument_id, market_observation_id, direction, planned_quantity,
                     entry_low, entry_high, structural_invalidation, protective_stop, target_reference,
                     planned_risk, planned_notional)
                OVERRIDING SYSTEM VALUE
                VALUES (2, gen_random_uuid(), 2, 1, 1, 1, 1, 1, 1, 'LONG', 10,
                        100, 101, 99, 98, 110, 100, 1000)"""))
                .hasMessageContaining("requires APPROVE or REDUCE");
    }

    @Test
    void tradePlanCannotExceedApprovedRisk() {
        run("""
                INSERT INTO operational.risk_decision
                    (risk_decision_id, decision_key, setup_observation_id, risk_context_snapshot_id, tenant_id,
                     broker_account_id, strategy_version_id, risk_policy_version_id, instrument_id, decision_at,
                     decision, requested_quantity, approved_quantity, requested_risk, approved_risk, approved_notional)
                OVERRIDING SYSTEM VALUE
                VALUES (3, gen_random_uuid(), 1, 1, 1, 1, 1, 1, 1, TIMESTAMPTZ '2024-06-03 10:23:00+05:30',
                        'APPROVE', 10, 10, 100, 100, 1000)
                ON CONFLICT DO NOTHING""");

        assertThatThrownBy(() -> run("""
                INSERT INTO operational.trade_plan
                    (trade_plan_id, trade_plan_key, risk_decision_id, setup_observation_id, tenant_id, broker_account_id,
                     strategy_version_id, instrument_id, market_observation_id, direction, planned_quantity,
                     entry_low, entry_high, structural_invalidation, protective_stop, target_reference,
                     planned_risk, planned_notional)
                OVERRIDING SYSTEM VALUE
                VALUES (3, gen_random_uuid(), 3, 1, 1, 1, 1, 1, 1, 'LONG', 11,
                        100, 101, 99, 98, 110, 100, 1000)"""))
                .hasMessageContaining("exceeds approved quantity");
    }

    @Test
    void unknownRiskReasonCodeIsRejected() {
        assertThatThrownBy(() -> run("""
                INSERT INTO operational.risk_decision_reason
                    (risk_decision_id, ordinal, reason_code, reason_type)
                VALUES (1, 1, 'NOT_A_REAL_CODE', 'INFO')"""))
                .hasMessageContaining("violates foreign key constraint");
    }

    @Test
    void setupObservationInstrumentMustMatchMarketObservation() {
        assertThatThrownBy(() -> run("""
                INSERT INTO operational.setup_observation
                    (setup_observation_id, setup_observation_key, tenant_id, broker_account_id, market_observation_id,
                     strategy_version_id, instrument_id, observed_at, direction, setup_status)
                OVERRIDING SYSTEM VALUE
                VALUES (2, gen_random_uuid(), 1, 1, 1, 1, 2,
                        TIMESTAMPTZ '2024-06-03 10:25:00+05:30', 'LONG', 'VALID')"""))
                .hasMessageContaining("does not match market_observation instrument");
    }

    @Test
    void marketObservationQualityEnumMatchesDd05() {
        run("""
                INSERT INTO market.market_observation
                    (market_observation_id, observation_key, instrument_id, timeframe_id, bar_close_timestamp, quality_status)
                OVERRIDING SYSTEM VALUE
                VALUES (10, gen_random_uuid(), 1, 1, TIMESTAMPTZ '2024-06-03 10:25:00+05:30', 'DEGRADED')
                ON CONFLICT DO NOTHING""");

        assertThatThrownBy(() -> run("""
                INSERT INTO market.market_observation
                    (market_observation_id, observation_key, instrument_id, timeframe_id, bar_close_timestamp, quality_status)
                OVERRIDING SYSTEM VALUE
                VALUES (11, gen_random_uuid(), 1, 1, TIMESTAMPTZ '2024-06-03 10:30:00+05:30', 'INVALID')"""))
                .hasMessageContaining("ck_market_observation_quality");
    }

    @Test
    void derivativeContractMustReferenceDerivativeInstrument() {
        assertThatThrownBy(() -> run("""
                INSERT INTO reference.derivative_contract (instrument_id, underlying_instrument_id, expiry_date)
                VALUES (1, 2, DATE '2024-06-27')"""))
                .hasMessageContaining("must be FUTURE or OPTION");
    }

    @Test
    void datasetVersionInputCycleIsRejected() {
        run("""
                INSERT INTO research.dataset (dataset_id, dataset_key, code, name, dataset_type)
                OVERRIDING SYSTEM VALUE
                VALUES (1, gen_random_uuid(), 'FEATURES', 'Features', 'FEATURE')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO research.dataset_version
                    (dataset_version_id, dataset_version_key, dataset_id, version, storage_uri)
                OVERRIDING SYSTEM VALUE
                VALUES
                    (1, gen_random_uuid(), 1, 1, 's3://bucket/a'),
                    (2, gen_random_uuid(), 1, 2, 's3://bucket/b')
                ON CONFLICT DO NOTHING""");

        run("""
                INSERT INTO research.dataset_version_input (dataset_version_id, input_dataset_version_id, input_role)
                VALUES (1, 2, 'INPUT')
                ON CONFLICT DO NOTHING""");

        assertThatThrownBy(() -> run("""
                INSERT INTO research.dataset_version_input (dataset_version_id, input_dataset_version_id, input_role)
                VALUES (2, 1, 'INPUT')"""))
                .hasMessageContaining("would create a cycle");
    }

    private void run(String sql) {
        database.execute(sql);
    }
}
