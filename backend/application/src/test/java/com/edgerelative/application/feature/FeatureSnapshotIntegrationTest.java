package com.edgerelative.application.feature;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import com.edgerelative.application.feature.persistence.FeatureSnapshotWriter;
import com.edgerelative.application.history.HistoryRepository;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.broker.api.model.BrokerCandle;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end feature snapshot persistence and API against a real database and mock broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FeatureSnapshotIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    private static final WireMockServer WIREMOCK = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("feature_test")
            .withUsername("feature_test")
            .withPassword("feature_test");

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
        registry.add("feature.history-days", () -> "30");
        registry.add("feature.rvol.min-samples", () -> "1");
        registry.add("feature.rvol.daily-lookback", () -> "5");
        registry.add("feature.rvol.interval-lookback", () -> "5");
        registry.add("feature.rvol.cumulative-lookback", () -> "5");
        registry.add("feature.atr.default-length", () -> "3");
        registry.add("feature.atr.length-by-timeframe.M1", () -> "3");
        registry.add("feature.rrs.fast-length", () -> "1");
        registry.add("feature.rrs.slow-length", () -> "2");
        registry.add("feature.rrs.persistence-window", () -> "2");
        registry.add("feature.rrs.slope-lookback", () -> "1");
        registry.add("feature.rrs.percentile-window", () -> "10");
        registry.add("feature.rve.fast-length", () -> "1");
        registry.add("feature.rve.slow-length", () -> "2");
        registry.add("feature.live.max-bars", () -> "2000");
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

    @Autowired
    private CanonicalInstrumentService canonical;

    @Autowired
    private HistoryRepository history;

    @Autowired
    private FeatureSnapshotWriter writer;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void computesPersistsAndAvoidsTheBroker() throws Exception {
        long subjectId = watchEquity("FEAT" + System.nanoTime());
        long benchmarkId = ensureIndex();
        seedSession(subjectId, D1, 100, 100.0);
        seedSession(subjectId, D2, 120, 100.5);
        seedSession(benchmarkId, D1, 500, 20000.0);
        seedSession(benchmarkId, D2, 520, 20000.0);
        Instant anchor = calendar.sessionClose(D2);

        JsonNode snapshot = getJson("/api/v1/features/snapshot?instrumentId=" + subjectId
                + "&timeframe=M1&anchor=" + anchor);
        assertThat(snapshot.path("features").path("ATR").path("availability").asString()).isEqualTo("VALID");
        assertThat(snapshot.path("features").path("RRS_RAW").path("availability").asString()).isEqualTo("VALID");
        assertThat(snapshot.path("market").isObject()).isTrue();
        assertThat(snapshot.path("benchmark").path("marketCode").asString()).isEqualTo("NIFTY50");

        writer.flush();
        Long snapshots = jdbc.queryForObject("SELECT count(*) FROM market.feature_snapshot", Long.class);
        Long values = jdbc.queryForObject("SELECT count(*) FROM market.feature_snapshot_value", Long.class);
        assertThat(snapshots).isGreaterThan(0);
        assertThat(values).isGreaterThan(0);

        // The feature path must never call the broker.
        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo("/v1/historical/candles")));
    }

    @Test
    void historicalSeriesIsDeterministic() throws Exception {
        long subjectId = watchEquity("FEAT" + System.nanoTime());
        long benchmarkId = ensureIndex();
        seedSession(subjectId, D1, 100, 100.0);
        seedSession(subjectId, D2, 120, 100.5);
        seedSession(benchmarkId, D1, 500, 20000.0);
        seedSession(benchmarkId, D2, 520, 20000.0);
        Instant to = calendar.sessionClose(D2);
        Instant from = to.minusSeconds(600);

        JsonNode first = getJson("/api/v1/features/series?instrumentId=" + subjectId
                + "&timeframe=M1&from=" + from + "&to=" + to);
        JsonNode second = getJson("/api/v1/features/series?instrumentId=" + subjectId
                + "&timeframe=M1&from=" + from + "&to=" + to);
        assertThat(first.isArray()).isTrue();
        assertThat(first.size()).isGreaterThan(0);
        assertThat(first.toString()).isEqualTo(second.toString());
    }

    @Test
    void snapshotsAreIdempotentAndImmutable() throws Exception {
        long subjectId = watchEquity("FEAT" + System.nanoTime());
        long benchmarkId = ensureIndex();
        seedSession(subjectId, D1, 100, 100.0);
        seedSession(subjectId, D2, 120, 100.5);
        seedSession(benchmarkId, D1, 500, 20000.0);
        seedSession(benchmarkId, D2, 520, 20000.0);
        Instant anchor = calendar.sessionClose(D2);
        String url = "/api/v1/features/snapshot?instrumentId=" + subjectId + "&timeframe=M1&anchor=" + anchor;
        getJson(url);
        getJson(url);
        writer.flush();
        Long snapshots = jdbc.queryForObject(
                "SELECT count(*) FROM market.feature_snapshot WHERE instrument_id = " + subjectId, Long.class);
        assertThat(snapshots).isEqualTo(1L);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE market.feature_snapshot SET snapshot_quality = 'GOOD' WHERE instrument_id = ?", subjectId))
                .isInstanceOf(DataAccessException.class);
    }

    private long watchEquity(String symbol) {
        return canonical.ensureInstrument("NSE", "CASH", "EQUITY", symbol, "Feature Test " + symbol, null, null);
    }

    private long ensureIndex() {
        return canonical.ensureInstrument("NSE", "CASH", "INDEX", "NIFTY50", "Nifty 50", null, null);
    }

    private void seedSession(long instrumentId, LocalDate date, long volume, double basePrice) {
        long timeframeId = canonical.ensureTimeframe("M1");
        Instant open = calendar.sessionOpen(date);
        List<BrokerCandle> candles = new ArrayList<>();
        int minutes = (int) calendar.sessionMinutes();
        for (int minute = 0; minute < minutes; minute++) {
            Instant barOpen = open.plusSeconds(60L * minute);
            double price = basePrice + minute * 0.001;
            candles.add(new BrokerCandle(
                    barOpen,
                    BigDecimal.valueOf(price),
                    BigDecimal.valueOf(price + 0.002),
                    BigDecimal.valueOf(price - 0.002),
                    BigDecimal.valueOf(price),
                    volume,
                    null));
        }
        history.upsertCandles(instrumentId, timeframeId, candles, 1000);
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = send(path);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(30))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
