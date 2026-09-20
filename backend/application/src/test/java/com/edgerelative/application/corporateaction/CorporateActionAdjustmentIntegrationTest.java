package com.edgerelative.application.corporateaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.corporateaction.persistence.CorporateActionFactorRepository;
import com.edgerelative.application.history.HistoryRepository;
import com.edgerelative.application.history.NewCandle;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
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

/**
 * End-to-end coverage of the adjusted analytical series at the HTTP boundary: raw stays raw, the
 * split factor applies once before the ex-date, point-in-time as-of reads do not leak, and an
 * unsupported action fails closed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CorporateActionAdjustmentIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final WireMockServer WIREMOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("ca_adjust")
            .withUsername("ca_adjust")
            .withPassword("ca_adjust");

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

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CanonicalInstrumentService canonical;

    @Autowired
    private HistoryRepository history;

    @Autowired
    private CorporateActionFactorRepository corporateActions;

    private long seed(String symbol) {
        long instrumentId = canonical.ensureInstrument(
                "NSE", "CASH", "EQ", symbol, symbol + " Ltd", new BigDecimal("0.05"), 1L);
        long timeframeId = canonical.ensureTimeframe("M1");
        List<NewCandle> candles = new ArrayList<>();
        for (String open : List.of("2026-09-15T03:45:00Z", "2026-09-15T03:46:00Z", "2026-09-16T03:45:00Z")) {
            candles.add(new NewCandle(
                    Instant.parse(open),
                    new BigDecimal("100"),
                    new BigDecimal("101"),
                    new BigDecimal("99"),
                    new BigDecimal("100"),
                    10,
                    null));
        }
        history.upsertCandles(instrumentId, timeframeId, candles, 1000);
        return instrumentId;
    }

    private JsonNode candles(long instrumentId, String adjustment, String asOf) throws Exception {
        String url = "http://127.0.0.1:" + port + "/api/v1/history/candles?instrumentId=" + instrumentId
                + "&timeframe=M1&from=2026-09-15T00:00:00Z&to=2026-09-17T00:00:00Z&limit=100&adjustment=" + adjustment
                + (asOf == null ? "" : "&asOf=" + asOf);
        return JSON.readTree(get(url));
    }

    @Test
    void adjustedSeriesHalvesPricesAndDoublesVolumeBeforeTheExDateWhileRawStaysRaw() throws Exception {
        String symbol = "CAADJ" + System.nanoTime();
        long instrumentId = seed(symbol);
        long actionId = corporateActions.insertAction(
                instrumentId, "SPLIT", LocalDate.of(2026, 9, 16), null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "test");
        corporateActions.insertFactor(
                actionId, instrumentId, new BigDecimal("0.5"), new BigDecimal("2"),
                Instant.parse("2026-09-01T00:00:00Z"), "er-ca-factor-v1");

        JsonNode adjusted = candles(instrumentId, "SPLIT_BONUS", null);
        assertThat(adjusted.size()).isEqualTo(3);
        JsonNode before = adjusted.get(0);
        assertThat(before.path("openTime").asString()).isEqualTo("2026-09-15T03:45:00Z");
        assertThat(before.path("close").decimalValue()).isEqualByComparingTo("50");
        assertThat(before.path("volume").asLong()).isEqualTo(20);
        assertThat(before.path("cumulativeAdjustmentFactor").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(before.path("definitionVersion").asString()).isEqualTo("er-ca-adjusted-v1");
        // On the ex-date the factor no longer applies (factor == 1, no adjustment).
        JsonNode exDate = adjusted.get(2);
        assertThat(exDate.path("openTime").asString()).isEqualTo("2026-09-16T03:45:00Z");
        assertThat(exDate.path("close").decimalValue()).isEqualByComparingTo("100");

        JsonNode raw = candles(instrumentId, "NONE", null);
        assertThat(raw.get(0).path("close").decimalValue()).isEqualByComparingTo("100");
        assertThat(raw.get(0).path("volume").asLong()).isEqualTo(10);
        assertThat(raw.get(0).path("cumulativeAdjustmentFactor").isNull()).isTrue();
    }

    @Test
    void adjustedSeriesOnlyReadsFactorsKnownAtTheAsOfTime() throws Exception {
        String symbol = "CAASOF" + System.nanoTime();
        long instrumentId = seed(symbol);
        long actionId = corporateActions.insertAction(
                instrumentId, "SPLIT", LocalDate.of(2026, 9, 16), null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "test");
        // The factor became known only after the as-of instant.
        corporateActions.insertFactor(
                actionId, instrumentId, new BigDecimal("0.5"), new BigDecimal("2"),
                Instant.parse("2026-09-20T00:00:00Z"), "er-ca-factor-v1");

        JsonNode beforeAnnouncement = candles(instrumentId, "SPLIT_BONUS", "2026-09-10T00:00:00Z");
        assertThat(beforeAnnouncement.get(0).path("close").decimalValue()).isEqualByComparingTo("100");
        assertThat(beforeAnnouncement.get(0).path("cumulativeAdjustmentFactor").decimalValue())
                .isEqualByComparingTo("1");

        JsonNode afterAnnouncement = candles(instrumentId, "SPLIT_BONUS", "2026-09-25T00:00:00Z");
        assertThat(afterAnnouncement.get(0).path("close").decimalValue()).isEqualByComparingTo("50");
    }

    @Test
    void unsupportedActionInTheWindowFailsClosedWhileRawRemainsAvailable() throws Exception {
        String symbol = "CADIV" + System.nanoTime();
        long instrumentId = seed(symbol);
        corporateActions.insertAction(
                instrumentId, "DIVIDEND", LocalDate.of(2026, 9, 16), null, null,
                null, null, new BigDecimal("5.00"), "INR", "test");

        HttpResponse<String> adjusted = send("http://127.0.0.1:" + port
                + "/api/v1/history/candles?instrumentId=" + instrumentId
                + "&timeframe=M1&from=2026-09-15T00:00:00Z&to=2026-09-17T00:00:00Z&limit=100&adjustment=SPLIT_BONUS");
        assertThat(adjusted.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(adjusted.body()).path("code").asString())
                .isEqualTo("CORPORATE_ACTION_ADJUSTMENT_UNSUPPORTED");

        // Raw history is always available; the unsupported action only blocks the adjusted series.
        assertThat(candles(instrumentId, "NONE", null).size()).isEqualTo(3);
    }

    private String get(String url) throws Exception {
        HttpResponse<String> response = send(url);
        assertThat(response.statusCode()).as(url).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> send(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
