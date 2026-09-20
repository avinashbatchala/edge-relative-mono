package com.edgerelative.application.catalog;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.application.backtest.application.BacktestPresets;
import com.edgerelative.application.catalog.application.CatalogValidationException;
import com.edgerelative.application.catalog.application.RiskPolicyCatalogService;
import com.edgerelative.application.catalog.application.StrategyCatalogService;
import com.edgerelative.application.risk.RiskFixtures;
import com.edgerelative.application.strategy.application.StrategyParametersProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CatalogIntegrationTest {

    private static final WireMockServer WIREMOCK = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("catalog_test")
            .withUsername("catalog_test")
            .withPassword("catalog_test");

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
    private StrategyCatalogService strategies;
    @Autowired
    private RiskPolicyCatalogService riskPolicies;
    @Autowired
    private JsonMapper json;

    @SuppressWarnings("unchecked")
    private Map<String, Object> strategyParams() {
        return json.convertValue(
                BacktestPresets.strategy(BacktestPresets.STRATEGY_RS_RESEARCH).orElseThrow(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> riskParams() {
        return json.convertValue(RiskFixtures.policy(), Map.class);
    }

    @Test
    void strategyVersionsAreImmutableAndRetireKeepsHistoryResolvable() {
        String code = "CAT_" + UUID.randomUUID().toString().substring(0, 8);
        var created = strategies.create(new StrategyCatalogService.CreateStrategyRequest(
                code, "Catalog Test", "test", "M5_3_8_CONFIRMATION", "RESEARCH", "M5", strategyParams()));
        assertThat(created.versions()).hasSize(1);
        long v1 = created.versions().get(0).strategyVersionId();

        Map<String, Object> modified = new java.util.LinkedHashMap<>(strategyParams());
        modified.put("minRvolInterval", 1.75);
        var versioned = strategies.addVersion(code,
                new StrategyCatalogService.AddStrategyVersionRequest("VALIDATED", "M5", modified));
        assertThat(versioned.versions()).hasSize(2);
        long v2 = versioned.versions().get(1).strategyVersionId();

        // Immutability: adding a version did not change the earlier version.
        assertThat(strategies.resolveVersion(v1).parameters().minRvolInterval()).isEqualTo(1.0);
        assertThat(strategies.resolveVersion(v2).parameters().minRvolInterval()).isEqualTo(1.75);

        strategies.setStatus(code, "RETIRED", "superseded");
        assertThat(strategies.list(false)).noneMatch(strategy -> strategy.code().equals(code));
        // Retired history is still resolvable for reproducible reruns.
        assertThat(strategies.resolveVersion(v1).code()).isEqualTo(code);
    }

    @Test
    void invalidParametersAreRejected() {
        Map<String, Object> bad = new java.util.LinkedHashMap<>(strategyParams());
        bad.put("enabledFamilies", java.util.List.of("NOT_A_FAMILY"));
        assertThatThrownBy(() -> strategies.create(new StrategyCatalogService.CreateStrategyRequest(
                "CAT_BAD_" + UUID.randomUUID().toString().substring(0, 6), "Bad", null, null, "RESEARCH", "M5", bad)))
                .isInstanceOf(CatalogValidationException.class);
    }

    @Test
    void riskPolicyCatalogVersionsAndRetire() {
        String code = "CAT_RISK_" + UUID.randomUUID().toString().substring(0, 8);
        var created = riskPolicies.create(new RiskPolicyCatalogService.CreateRiskPolicyRequest(
                code, "Catalog Risk", "test", "EXPERIMENTAL", riskParams()));
        assertThat(created.versions()).hasSize(1);
        assertThat(created.versions().get(0).parameters().code()).isEqualTo(code);

        Map<String, Object> modified = new java.util.LinkedHashMap<>(riskParams());
        ((java.util.Map<String, Object>) modified.get("trade")).put("baseRiskFraction", 0.004);
        var versioned = riskPolicies.addVersion(code,
                new RiskPolicyCatalogService.AddRiskPolicyVersionRequest("VALIDATED", modified));
        assertThat(versioned.versions()).hasSize(2);
        long v1 = versioned.versions().get(0).riskPolicyVersionId();
        long v2 = versioned.versions().get(1).riskPolicyVersionId();

        assertThat(created.versions().get(0).parameters().trade().baseRiskFraction())
                .isEqualByComparingTo("0.01");
        var resolvedV2 = riskPolicies.get(code).versions().stream()
                .filter(version -> version.riskPolicyVersionId() == v2).findFirst().orElseThrow();
        assertThat(resolvedV2.parameters().trade().baseRiskFraction()).isEqualByComparingTo("0.004");

        riskPolicies.setStatus(code, "RETIRED", null);
        assertThat(riskPolicies.list(false)).noneMatch(policy -> policy.code().equals(code));
        assertThat(riskPolicies.get(code).versions().stream()
                .anyMatch(version -> version.riskPolicyVersionId() == v1)).isTrue();
    }

    @Test
    void researchStrategyPresetIsNotProductionAuthority() {
        assertThat(StrategyParametersProvider.class).isNotNull();
        assertThat(BacktestPresets.risk(BacktestPresets.RISK_RESEARCH_PERMISSIVE).orElseThrow()
                .lifecycleState().productionCalibrated()).isFalse();
    }
}
