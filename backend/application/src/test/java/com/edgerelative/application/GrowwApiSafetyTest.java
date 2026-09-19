package com.edgerelative.application;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves the mutation endpoints are structurally incapable of reaching Groww, and that read-only
 * paths do reach the broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GrowwApiSafetyTest {

    private static final WireMockServer WIREMOCK = startWireMock();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("groww_api_test")
            .withUsername("groww_api_test")
            .withPassword("groww_api_test");

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
        registry.add("broker.groww.base-url", WIREMOCK::baseUrl);
        registry.add("broker.groww.instrument-master-url", () -> WIREMOCK.baseUrl() + "/instruments/instrument.csv");
        registry.add("broker.groww.credentials.mode", () -> "ACCESS_TOKEN");
        registry.add("broker.groww.credentials.access-token", () -> "test-token");
    }

    @LocalServerPort
    private int port;

    @Test
    void placingAnOrderReturns501AndNeverCallsGroww() throws Exception {
        WIREMOCK.resetRequests();

        var response = httpPost("/api/v1/brokers/groww/orders", """
                {"tradingSymbol":"RELIANCE","quantity":1,"validity":"DAY","exchange":"NSE",
                 "segment":"CASH","product":"CNC","orderType":"LIMIT","transactionType":"BUY",
                 "orderReferenceId":"ref-12345"}""");

        assertThat(response.statusCode()).isEqualTo(501);
        assertThat(JSON.readTree(response.body()).path("code").asString())
                .isEqualTo("BROKER_OPERATION_NOT_ENABLED");
        WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void cancellingAnOrderReturns501AndNeverCallsGroww() throws Exception {
        WIREMOCK.resetRequests();

        var response = httpPost("/api/v1/brokers/groww/orders/cancel", """
                {"brokerOrderId":"GMK1","segment":"CASH"}""");

        assertThat(response.statusCode()).isEqualTo(501);
        WIREMOCK.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void capabilitiesDoNotAdvertiseExecution() throws Exception {
        var response = httpGet("/api/v1/brokers/groww/capabilities");
        JsonNode body = JSON.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.path("broker").asString()).isEqualTo("groww");
        var capabilities = body.path("capabilities");
        assertThat(capabilities.toString()).contains("LIVE_QUOTE", "HISTORICAL_CANDLES", "POSITIONS");
        assertThat(capabilities.toString()).doesNotContain("ORDER_EXECUTION", "SMART_ORDER_EXECUTION");
    }

    @Test
    void readOnlyQuoteReachesTheBroker() throws Exception {
        WIREMOCK.resetRequests();
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/live-data/quote"))
                .willReturn(okJson("""
                        {"status":"SUCCESS","payload":{"last_price":2500.55}}""")));

        var response = httpGet(
                "/api/v1/brokers/groww/market-data/quote?exchange=NSE&segment=CASH&tradingSymbol=RELIANCE");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("lastPrice").asDouble()).isEqualTo(2500.55);
        WIREMOCK.verify(1, getRequestedFor(urlPathEqualTo("/v1/live-data/quote")));
    }

    @Test
    void openApiSpecDocumentsTheGrowwReadApis() throws Exception {
        var response = httpGet("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("info").path("title").asString()).isEqualTo("Edge Relative API");
        assertThat(body.path("paths").has("/api/v1/brokers/groww/market-data/quote"))
                .isTrue();
        assertThat(body.path("paths").has("/api/v1/brokers/groww/orders")).isTrue();
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        var response = httpGet("/swagger-ui/index.html");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> httpPost(String path, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(base() + path))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(base() + path))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }
}
