package com.edgerelative.application.strategy;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.strategy.application.SetupObservationView;
import com.edgerelative.application.strategy.domain.DependencyStatus;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.StrategyEngine;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.persistence.SetupObservationRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Append-only, idempotent setup observation persistence against a real database. The engine is
 * driven with a fail-closed dependency set so no {@code VALID} opportunity is produced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SetupObservationIntegrationTest {

    private static final Instant T = Instant.parse("2026-09-18T04:30:00Z");
    private static final LocalDate SESSION = LocalDate.of(2026, 9, 18);
    private static final WireMockServer WIREMOCK = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("setup_test")
            .withUsername("setup_test")
            .withPassword("setup_test");

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

    @Autowired
    private SetupObservationRepository repository;

    @Autowired
    private CanonicalInstrumentService canonical;

    @Autowired
    private StrategyEngine engine;

    @Test
    void seedsStrategyVersionAndPersistsIdempotentFailClosedObservation() {
        assertThat(repository.strategyVersionId()).isPresent();

        long instrumentId = canonical.ensureInstrument(
                "NSE", "CASH", "EQUITY", "TEST_SETUP", "Test Setup", new BigDecimal("0.05"), 1L);
        long timeframeId = canonical.ensureTimeframe("M5");

        long first = repository.ensureMarketObservation(instrumentId, timeframeId, T, null);
        long second = repository.ensureMarketObservation(instrumentId, timeframeId, T, null);
        assertThat(second).isEqualTo(first);

        StrategyEvaluationResult result = engine.evaluate(input(instrumentId), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.setupState().name()).isEqualTo("NONE");
        assertThat(result.setupInstanceId()).isNull();

        long strategyVersionId = repository.strategyVersionId().orElseThrow();
        assertThat(repository.append(result, strategyVersionId, first, null)).isTrue();
        assertThat(repository.append(result, strategyVersionId, first, null)).isFalse();

        List<SetupObservationView> observations = repository.latestForInstrument(instrumentId);
        assertThat(observations).hasSize(1);
        assertThat(observations.get(0).setupStatus()).isEqualTo("NONE");
        assertThat(observations.get(0).direction()).isEqualTo("LONG");
        assertThat(observations.get(0).strategyVersion()).isEqualTo(1);
        assertThat(observations.get(0).explanationJson()).contains("DATA_INVALID");
    }

    private static StrategyEvaluationInput input(long instrumentId) {
        return new StrategyEvaluationInput(
                "eval-1",
                T,
                SESSION,
                "ER_RS_CONTINUATION_V1/v1",
                "TEST_PARAMS",
                instrumentId,
                0L,
                "mo:test",
                new StrategyEvaluationInput.SessionContext(
                        T, true, true, false, false, "nse-session-v1"),
                new StrategyEvaluationInput.MarketContext(null, null, null, T, false),
                null,
                null,
                null,
                List.of(new DependencyStatus(
                        "market.bias", true, DependencyStatus.DependencyState.MISSING)),
                StrategyEvaluationInput.PriorSetup.none());
    }

    private static StrategyParameters parameters() {
        return new StrategyParameters(
                "TEST_PARAMS",
                1,
                Set.of(SetupFamily.M5_3_8_CONFIRMATION),
                0.5, -0.5,
                1.2, 1.5, 1.2,
                5_000_000,
                0.5,
                0.8,
                0.25,
                0.05,
                1,
                12,
                12,
                15,
                15,
                300.0,
                StrategyParameters.NeutralMarketPolicy.BLOCK,
                0.0,
                null, null, null, null, null);
    }
}
