package com.edgerelative.application.history;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.reference.NseTradingCalendar;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end coverage of the historical backfill pipeline against a mock Groww server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HistoryBackfillIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CANDLES = """
            {"status":"SUCCESS","payload":{"candles":[
              ["2026-09-01T00:00:00", 100, 101, 99, 100.5, 1000, null]
            ]}}""";

    /** Five M1 bars spanning 09:15–09:19 IST (03:45–03:49 UTC). */
    private static final String MINUTES = """
            {"status":"SUCCESS","payload":{"candles":[
              ["2026-09-01T03:45:00", 100, 100.5, 99.5, 100.25, 10, null],
              ["2026-09-01T03:46:00", 101, 101.5, 100.5, 101.25, 10, null],
              ["2026-09-01T03:47:00", 102, 102.5, 101.5, 102.25, 10, null],
              ["2026-09-01T03:48:00", 103, 103.5, 102.5, 103.25, 10, null],
              ["2026-09-01T03:49:00", 104, 104.5, 103.5, 104.25, 10, null]
            ]}}""";

    private static final WireMockServer WIREMOCK = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("history_test")
            .withUsername("history_test")
            .withPassword("history_test");

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
        registry.add("history.backfill.workers", () -> "1");
        registry.add("history.backfill.sweep-interval", () -> "100ms");
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @BeforeEach
    void resetWireMock() {
        WIREMOCK.resetAll();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private NseTradingCalendar calendar;

    @Test
    void bindsTheConfiguredNseHolidayCalendar() {
        // Ganesh Chaturthi 2026 is on the official NSE Capital Market holiday list.
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 9, 14))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 9, 15))).isTrue();
    }

    @Test
    void backfillsAndPersistsCandlesIdempotently() throws Exception {
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/historical/candles")).willReturn(okJson(CANDLES)));

        long instrumentId = watchInstrument("HIST1");
        JsonNode run = startBackfill(instrumentId, "ONE_MINUTE", "2026-09-01T00:00:00Z", "2026-09-10T00:00:00Z");
        String runKey = run.path("runKey").asString();
        assertThat(run.path("totalChunks").asInt()).isEqualTo(1);

        JsonNode completed = awaitStatus(runKey, "COMPLETED");
        assertThat(completed.path("candlesWritten").asLong()).isEqualTo(1);

        JsonNode coverage = getJson("/api/v1/history/coverage?instrumentId=" + instrumentId + "&timeframe=ONE_MINUTE");
        assertThat(coverage.path("status").asString()).isEqualTo("COMPLETE");
        assertThat(coverage.path("candleCount").asLong()).isEqualTo(1);

        // Re-running the same range must not duplicate candles.
        JsonNode rerun = startBackfill(instrumentId, "ONE_MINUTE", "2026-09-01T00:00:00Z", "2026-09-10T00:00:00Z");
        awaitStatus(rerun.path("runKey").asString(), "COMPLETED");
        JsonNode after = getJson("/api/v1/history/coverage?instrumentId=" + instrumentId + "&timeframe=ONE_MINUTE");
        assertThat(after.path("candleCount").asLong()).isEqualTo(1);
    }

    @Test
    void plansProviderBoundedChunksForIntradayRanges() throws Exception {
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/historical/candles")).willReturn(okJson(CANDLES)));

        long instrumentId = watchInstrument("HIST2");
        // 5-minute max window is 30 days, so a 60-day range is two chunks.
        JsonNode run = startBackfill(instrumentId, "ONE_MINUTE", "2026-01-01T00:00:00Z", "2026-03-02T00:00:00Z");
        assertThat(run.path("totalChunks").asInt()).isEqualTo(2);
    }

    @Test
    void failedChunkIsResumableViaRetry() throws Exception {
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/historical/candles"))
                .inScenario("resume")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"FAILURE\",\"error\":{\"code\":\"GA000\",\"message\":\"temporary\"}}"))
                .willSetStateTo("ready"));
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/historical/candles"))
                .inScenario("resume")
                .whenScenarioStateIs("ready")
                .willReturn(okJson(CANDLES)));

        long instrumentId = watchInstrument("HIST3");
        JsonNode run = startBackfill(instrumentId, "ONE_MINUTE", "2026-09-01T00:00:00Z", "2026-09-10T00:00:00Z");
        String runKey = run.path("runKey").asString();

        JsonNode partial = awaitStatus(runKey, "PARTIAL");
        assertThat(partial.path("failedChunks").asInt()).isEqualTo(1);

        JsonNode retried = retry(runKey);
        assertThat(retried.path("runKey").asString()).isEqualTo(runKey);
        awaitStatus(runKey, "COMPLETED");

        JsonNode coverage = getJson("/api/v1/history/coverage?instrumentId=" + instrumentId + "&timeframe=ONE_MINUTE");
        assertThat(coverage.path("candleCount").asLong()).isEqualTo(1);
    }

    @Test
    void rejectsBackfillForUnwatchedInstrumentWithoutCallingTheBroker() throws Exception {
        long instrumentId = watchInstrument("HIST4");
        unwatchInstrument(instrumentId);

        String body = JSON.writeValueAsString(Map.of(
                "instrumentId", instrumentId,
                "timeframe", "ONE_MINUTE",
                "from", "2026-09-01T00:00:00Z",
                "to", "2026-09-10T00:00:00Z"));
        HttpResponse<String> response = post("/api/v1/history/backfill", body);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(response.body()).path("code").asString())
                .isEqualTo("HISTORY_INSTRUMENT_NOT_WATCHED");
        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo("/v1/historical/candles")));
    }

    @Test
    void rejectsNonM1BackfillBecauseHigherTimeframesAreDerived() throws Exception {
        long instrumentId = watchInstrument("HIST5");

        String body = JSON.writeValueAsString(Map.of(
                "instrumentId", instrumentId,
                "timeframe", "ONE_DAY",
                "from", "2026-09-01T00:00:00Z",
                "to", "2026-09-10T00:00:00Z"));
        HttpResponse<String> response = post("/api/v1/history/backfill", body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo("HISTORY_INVALID");
        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo("/v1/historical/candles")));
    }

    @Test
    void derivesHigherTimeframesFromThePersistedM1Base() throws Exception {
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/historical/candles")).willReturn(okJson(MINUTES)));

        long instrumentId = watchInstrument("HIST6");
        JsonNode run = startBackfill(instrumentId, "ONE_MINUTE", "2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z");
        awaitStatus(run.path("runKey").asString(), "COMPLETED");

        JsonNode m1 = getJson("/api/v1/history/candles?instrumentId=" + instrumentId
                + "&timeframe=ONE_MINUTE&from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&limit=100");
        assertThat(m1.size()).isEqualTo(5);
        assertThat(m1.get(0).path("definitionVersion").asString()).isEqualTo("er-m1-base-v1");

        JsonNode daily = getJson("/api/v1/history/candles?instrumentId=" + instrumentId
                + "&timeframe=ONE_DAY&from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&limit=100");
        assertThat(daily.size()).isEqualTo(1);
        assertThat(daily.get(0).path("openTime").asString()).isEqualTo("2026-09-01T03:45:00Z");
        assertThat(daily.get(0).path("open").decimalValue()).isEqualByComparingTo("100");
        assertThat(daily.get(0).path("high").decimalValue()).isEqualByComparingTo("104.5");
        assertThat(daily.get(0).path("low").decimalValue()).isEqualByComparingTo("99.5");
        assertThat(daily.get(0).path("close").decimalValue()).isEqualByComparingTo("104.25");
        assertThat(daily.get(0).path("volume").asLong()).isEqualTo(50);
        assertThat(daily.get(0).path("partial").asBoolean()).isFalse();
        assertThat(daily.get(0).path("definitionVersion").asString()).isEqualTo("er-aggregate-v1");
    }

    // --- helpers ------------------------------------------------------------------

    /** Persistence is limited to the active watchlist, so the instrument must be watched first. */
    private long watchInstrument(String symbol) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "exchange", "NSE",
                "segment", "CASH",
                "instrumentType", "EQ",
                "symbol", symbol,
                "name", symbol + " Ltd",
                "brokerSymbol", "NSE-" + symbol,
                "tickSize", 0.05,
                "lotSize", 1));
        HttpResponse<String> response = post("/api/v1/watchlist/items", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return JSON.readTree(response.body()).path("instrumentId").asLong();
    }

    private void unwatchInstrument(long instrumentId) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        uri("/api/v1/watchlist/items/" + instrumentId))
                .timeout(Duration.ofSeconds(10))
                .DELETE()
                .build());
        assertThat(response.statusCode()).isEqualTo(204);
    }

    private JsonNode startBackfill(long instrumentId, String timeframe, String from, String to) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "instrumentId", instrumentId,
                "timeframe", timeframe,
                "from", from,
                "to", to));
        HttpResponse<String> response = post("/api/v1/history/backfill", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return JSON.readTree(response.body());
    }

    private JsonNode retry(String runKey) throws Exception {
        HttpResponse<String> response = post("/api/v1/history/backfill/" + runKey + "/retry", "");
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private JsonNode awaitStatus(String runKey, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = getJson("/api/v1/history/backfill/" + runKey);
            if (expected.equals(last.path("status").asString())) {
                return last;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + expected + ", last=" + last);
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
