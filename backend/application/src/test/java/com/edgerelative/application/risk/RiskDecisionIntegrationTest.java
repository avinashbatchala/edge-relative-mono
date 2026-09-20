package com.edgerelative.application.risk;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.risk.api.RiskCandidateRequest;
import com.edgerelative.application.risk.application.RiskApplicationService;
import com.edgerelative.application.risk.application.port.RiskContextProvider;
import com.edgerelative.application.risk.domain.RiskContext;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.risk.domain.RiskDecisionType;
import com.edgerelative.application.risk.domain.RiskState;
import com.edgerelative.application.risk.domain.TradingMode;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL evidence for idempotent decisions, atomic reservations, release, and concurrent
 * approvals. It proves no trade, order, fill, or position is created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RiskDecisionIntegrationTest {

    private static final Instant T = Instant.parse("2026-09-18T04:30:00Z");
    private static final LocalDate SESSION = LocalDate.of(2026, 9, 18);
    private static final String POLICY_JSON = """
            {
              "allowedModes": ["RESEARCH","BACKTEST","PAPER","ASSISTED_LIVE","GUARDED_AUTOPILOT"],
              "minimumStateForMode": {},
              "trade": {"baseRiskFraction":0.01,"maxPositionNotionalFraction":0.20},
              "portfolio": {"maxOpenRiskFraction":0.05,"maxStressRiskFraction":0.10,
                            "maxGrossExposureFraction":1.0,"maxNetExposureFraction":0.5,
                            "sessionRiskBudgetFraction":0.03,"portfolioRiskBudgetFraction":0.05,"maxPositions":5},
              "symbol": {"maxNotionalFraction":0.20,"maxOpenRiskFraction":0.02,"riskBudgetFraction":0.02,"maxSessionLossFraction":0.01},
              "sector": {"maxNotionalFraction":0.40,"maxOpenRiskFraction":0.03,"maxDirectionalRiskFraction":0.30,
                         "riskBudgetFraction":0.03,"sectorMappingMandatory":true},
              "margin": {"safetyBufferFraction":0.10},
              "liquidity": {"maxParticipationFraction":0.02,"maxSpreadBps":25,"liquidityMandatory":true,
                            "brokerCapMandatory":false,"spreadMandatory":true},
              "execution": {"adverseSlippageTicks":1,"exitCostTicks":1,"exitCostBps":5},
              "stress": {"adverseMoveFraction":0.03,"costPerUnit":0,"costBps":5,"enabled":true},
              "drawdown": {"dailyReduce1Fraction":0.01,"dailyReduce2Fraction":0.02,"dailyStopNewFraction":0.03,
                           "weeklyLimitFraction":0.05,"monthlyLimitFraction":0.08,"accountLimitFraction":0.10,
                           "maxConsecutiveLosses":4},
              "stateModifiers": {"REDUCED_1":0.5,"REDUCED_2":0.25},
              "strategyRiskFractions": {},
              "strategyRiskBudgetFractions": {"ER_RS_CONTINUATION_V1/v1":0.03},
              "symbolRiskBudgetFractions": {},
              "sectorRiskBudgetFractions": {"IT":0.03},
              "correlationEnabled": false,
              "correlationModifiers": {},
              "averagingDownEnabled": false,
              "pyramidingEnabled": false,
              "qualitySizingEnabled": false,
              "mlRiskModifier": 1.0
            }
            """;

    private static final WireMockServer WIREMOCK = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("risk_test")
            .withUsername("risk_test")
            .withPassword("risk_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
        registry.add("broker.groww.base-url", WIREMOCK::baseUrl);
        registry.add("broker.groww.credentials.mode", () -> "ACCESS_TOKEN");
        registry.add("broker.groww.credentials.access-token", () -> "test-token");
        registry.add("broker.groww.retry.max-attempts", () -> "1");
    }

    @TestConfiguration
    static class ContextProvider {
        @Bean
        @Primary
        RiskContextProvider testRiskContextProvider() {
            return (brokerAccountId, at) -> java.util.Optional.of(RiskContext.builder(
                            UUID.nameUUIDFromBytes(("ctx:" + brokerAccountId).getBytes()).toString(), 1, T, SESSION)
                    .available()
                    .equity(new BigDecimal("1000000"), new BigDecimal("1000000"))
                    .funding(new BigDecimal("500000"), new BigDecimal("500000"), BigDecimal.ZERO)
                    .exposures(BigDecimal.ZERO, BigDecimal.ZERO)
                    .risk(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    .losses(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    .state(RiskState.NORMAL)
                    .counters(0, 0)
                    .health("HEALTHY", "HEALTHY", "MATCHED", true)
                    .session(new RiskContext.SessionWindow(T, true, true, false, false, false, "nse-session-v1"))
                    .build());
        }
    }

    @Autowired
    private RiskApplicationService service;
    @Autowired
    private CanonicalInstrumentService canonical;
    @Autowired
    private DSLContext dsl;

    @Test
    void idempotentApprovalReservesOnceAndReleaseReturnsCapacity() throws Exception {
        Fixture f = seed();
        RiskCandidateRequest request = request(f, "cand-1");

        RiskDecisionProposal first = service.approve(request.toCandidate());
        RiskDecisionProposal retry = service.approve(request.toCandidate());
        assertThat(first.decision()).isEqualTo(RiskDecisionType.APPROVE);
        assertThat(first.approvedQuantity()).isEqualTo(2000);
        assertThat(retry.decisionKey()).isEqualTo(first.decisionKey());

        assertThat(count("SELECT count(*) c FROM operational.risk_decision WHERE decision_key = ?", UUID.fromString(first.decisionKey()))).isEqualTo(1L);
        assertThat(count("SELECT count(*) c FROM operational.risk_reservation WHERE risk_decision_id = "
                + "(SELECT risk_decision_id FROM operational.risk_decision WHERE decision_key = ?)", UUID.fromString(first.decisionKey()))).isEqualTo(1L);
        assertThat(money("SELECT reserved_risk FROM operational.risk_account_state WHERE broker_account_id = ? AND trading_date = ?", f.accountId, SESSION))
                .isEqualByComparingTo("4400");
        assertThat(money("SELECT reserved_notional FROM operational.risk_account_state WHERE broker_account_id = ? AND trading_date = ?", f.accountId, SESSION))
                .isEqualByComparingTo("200000");

        assertThat(service.release(f.accountId, first.decisionKey(), "CANCELLED")).isTrue();
        assertThat(service.release(f.accountId, first.decisionKey(), "CANCELLED")).isFalse();
        assertThat(money("SELECT reserved_risk FROM operational.risk_account_state WHERE broker_account_id = ? AND trading_date = ?", f.accountId, SESSION))
                .isEqualByComparingTo("0");
        // The approved decision created exactly one immutable plan with matching ceilings.
        assertThat(count("SELECT count(*) c FROM operational.trade_plan tp JOIN operational.risk_decision rd "
                + "ON rd.risk_decision_id = tp.risk_decision_id WHERE rd.decision_key = ?", UUID.fromString(first.decisionKey())))
                .isEqualTo(1L);
        assertThat(money("SELECT tp.planned_quantity FROM operational.trade_plan tp JOIN operational.risk_decision rd "
                + "ON rd.risk_decision_id = tp.risk_decision_id WHERE rd.decision_key = ?", UUID.fromString(first.decisionKey())))
                .isEqualByComparingTo("2000");
        assertThat(money("SELECT tp.planned_risk FROM operational.trade_plan tp JOIN operational.risk_decision rd "
                + "ON rd.risk_decision_id = tp.risk_decision_id WHERE rd.decision_key = ?", UUID.fromString(first.decisionKey())))
                .isEqualByComparingTo("4400");
        // A repeated approval returns the same plan; creation is idempotent (scoped to this account).
        assertThat(count("SELECT count(*) c FROM operational.trade_plan tp JOIN operational.risk_decision rd "
                + "ON rd.risk_decision_id = tp.risk_decision_id WHERE rd.broker_account_id = ?", f.accountId))
                .isEqualTo(1L);
        // Immutable original intent: the row cannot be rewritten.
        assertThatThrownBy(() -> dsl.execute("UPDATE operational.trade_plan SET planned_quantity = 1"))
                .isInstanceOf(org.jooq.exception.DataAccessException.class);

        assertThat(count("SELECT count(*) c FROM operational.trade")).isZero();
        assertThat(count("SELECT count(*) c FROM operational.order_record")).isZero();
        assertThat(count("SELECT count(*) c FROM operational.position_projection")).isZero();
    }

    @Test
    void concurrentApprovalsNeverOverspendCapacity() throws Exception {
        Fixture f = seed();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<RiskDecisionProposal> a = () -> service.approve(request(f, "cand-a").toCandidate());
            Callable<RiskDecisionProposal> b = () -> service.approve(request(f, "cand-b").toCandidate());
            Future<RiskDecisionProposal> fa = pool.submit(a);
            Future<RiskDecisionProposal> fb = pool.submit(b);
            RiskDecisionProposal pa = fa.get();
            RiskDecisionProposal pb = fb.get();
            assertThat(pa.decision()).isEqualTo(RiskDecisionType.APPROVE);
            assertThat(pb.decision()).isEqualTo(RiskDecisionType.APPROVE);
        } finally {
            pool.shutdownNow();
        }
        BigDecimal reserved = money("SELECT reserved_risk FROM operational.risk_account_state WHERE broker_account_id = ? AND trading_date = ?", f.accountId, SESSION);
        // Two approvals of 4400 each; the session budget is 30,000 and the invariant holds.
        assertThat(reserved).isEqualByComparingTo("8800");
        assertThat(reserved).isLessThanOrEqualTo(new BigDecimal("30000"));
        assertThat(count("SELECT count(*) c FROM operational.trade_plan tp JOIN operational.risk_decision rd "
                + "ON rd.risk_decision_id = tp.risk_decision_id WHERE rd.broker_account_id = ?", f.accountId))
                .isEqualTo(2L);
    }

    private long count(String sql, Object... args) {
        return dsl.fetchOne(sql, args).get("c", Long.class);
    }

    private BigDecimal money(String sql, Object... args) {
        return dsl.fetchOne(sql, args).get(0, BigDecimal.class);
    }

    private RiskCandidateRequest request(Fixture f, String candidateKey) {
        return new RiskCandidateRequest(
                candidateKey, "cand", f.tenantId, f.accountId, TradingMode.ASSISTED_LIVE.name(),
                "11111111-1111-1111-1111-111111111111", f.setupObservationId, "ER_RS_CONTINUATION_V1",
                "ER_RS_CONTINUATION_V1/v1", Math.toIntExact(f.strategyVersionId), f.instrumentId, "RISKTEST", "LONG",
                new BigDecimal("0.05"), 1L, new BigDecimal("100"), new BigDecimal("98"), "M5 swing low", T,
                "BULLISH", true, true, false, f.sectorId, "IT", SESSION, 5.0, 100000.0, 5_000_000.0, null, null,
                true, "VALID", "er-feature-schema-v1", "ER_RISK_SYNTHETIC_TEST");
    }

    private record Fixture(long tenantId, long accountId, long instrumentId, long strategyVersionId,
                           long setupObservationId, Long sectorId) {
    }

    private Fixture seed() {
        long tenantId = dsl.fetchOne(
                        "INSERT INTO operational.tenant (tenant_key, name) VALUES (gen_random_uuid(), 'Risk Test') RETURNING tenant_id")
                .get("tenant_id", Long.class);
        long brokerId = dsl.fetchOne(
                        "INSERT INTO reference.broker (code, name) VALUES ('GROWW', 'Groww') "
                                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING broker_id")
                .get("broker_id", Long.class);
        long accountId = dsl.fetchOne(
                        "INSERT INTO operational.broker_account (broker_account_key, tenant_id, broker_id, "
                                + "external_account_id, secret_reference, status) "
                                + "VALUES (gen_random_uuid(), ?, ?, ?, 'secret', 'ACTIVE') RETURNING broker_account_id",
                        tenantId, brokerId, "acc-" + UUID.randomUUID())
                .get("broker_account_id", Long.class);
        long instrumentId = canonical.ensureInstrument(
                "NSE", "CASH", "EQUITY", "RISKTEST" + Math.abs(UUID.randomUUID().hashCode()), "Risk Test",
                new BigDecimal("0.05"), 1L);
        long timeframeId = canonical.ensureTimeframe("M5");
        long marketObservationId = dsl.fetchOne(
                        "INSERT INTO market.market_observation (observation_key, instrument_id, timeframe_id, "
                                + "bar_close_timestamp, quality_status) VALUES (gen_random_uuid(), ?, ?, ?::timestamptz, 'GOOD') "
                                + "RETURNING market_observation_id",
                        instrumentId, timeframeId, T)
                .get("market_observation_id", Long.class);
        long strategyVersionId = dsl.fetchOne(
                        "SELECT sv.strategy_version_id FROM control.strategy_version sv "
                                + "JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
                                + "WHERE s.code = 'ER_RS_CONTINUATION_V1' ORDER BY sv.version DESC LIMIT 1")
                .get("strategy_version_id", Long.class);
        long sectorId = dsl.fetchOne(
                        "INSERT INTO reference.sector (code, name) VALUES ('IT', 'IT') "
                                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING sector_id")
                .get("sector_id", Long.class);
        long setupObservationId = dsl.fetchOne(
                        "INSERT INTO operational.setup_observation (setup_observation_key, tenant_id, broker_account_id, "
                                + "market_observation_id, strategy_version_id, instrument_id, observed_at, direction, "
                                + "setup_status, structural_invalidation, sector_id) "
                                + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?::timestamptz, 'LONG', 'VALID', 98, ?) "
                                + "RETURNING setup_observation_id",
                        tenantId, accountId, marketObservationId, strategyVersionId, instrumentId, T, sectorId)
                .get("setup_observation_id", Long.class);
        long policyId = dsl.fetchOne(
                        "INSERT INTO control.risk_policy (risk_policy_key, code, name) "
                                + "VALUES (gen_random_uuid(), 'ER_RISK_SYNTHETIC_TEST', 'Synthetic Risk Policy') "
                                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING risk_policy_id")
                .get("risk_policy_id", Long.class);
        dsl.execute(
                "INSERT INTO control.risk_policy_version (risk_policy_id, version, lifecycle_state, parameters, code_version) "
                        + "VALUES (?, 1, 'VALIDATED', ?::jsonb, 'test') ON CONFLICT (risk_policy_id, version) DO NOTHING",
                policyId, POLICY_JSON);
        return new Fixture(tenantId, accountId, instrumentId, strategyVersionId, setupObservationId, sectorId);
    }
}
