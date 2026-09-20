package com.edgerelative.application.feature;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.history.HistoryRepository;
import com.edgerelative.application.history.NewCandle;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.watchlist.WatchlistService;
import com.edgerelative.application.watchlist.api.AddWatchlistItemRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
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

/** End-to-end coverage of the observational Feature Dashboard API and stream contract. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FeatureDashboardIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final WireMockServer WIREMOCK = startWireMock();

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("feature_dashboard")
            .withUsername("feature_dashboard")
            .withPassword("feature_dashboard");

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
        registry.add("feature.atr.default-length", () -> "3");
        registry.add("feature.atr.length-by-timeframe.M5", () -> "3");
        registry.add("feature.atr.length-by-timeframe.D1", () -> "3");
        registry.add("feature.rrs.fast-length", () -> "1");
        registry.add("feature.rrs.slow-length", () -> "2");
        registry.add("feature.rrs.persistence-window", () -> "2");
        registry.add("feature.rrs.slope-lookback", () -> "1");
        registry.add("feature.rrs.percentile-window", () -> "10");
        registry.add("feature.rve.fast-length", () -> "1");
        registry.add("feature.rve.slow-length", () -> "2");
        registry.add("feature.live.max-bars", () -> "2000");
        registry.add("feature.dashboard.cache-ttl", () -> "0s");
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
    private WatchlistService watchlist;

    @Test
    void dashboardMapsWatchlistRowsWithIdentityVersionsAndQuality() throws Exception {
        watchInstrument("DASHFEAT" + System.nanoTime());
        JsonNode rows = getJson("/api/v1/features/dashboard");

        assertThat(rows.isArray()).isTrue();
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        JsonNode row = rows.get(0);
        assertThat(row.path("instrumentId").asLong()).isPositive();
        assertThat(row.path("symbol").asString()).startsWith("DASHFEAT");
        assertThat(row.path("timeframe").asString()).isEqualTo("M5");
        assertThat(row.path("observationTime").isString()).isTrue();
        assertThat(row.path("featureSchemaVersion").asString()).isEqualTo("er-feature-schema-v1");
        assertThat(row.path("quality").asString()).isNotBlank();
        assertThat(row.path("availability").asString()).isNotBlank();
        assertThat(row.path("featureVersions").isObject()).isTrue();
        assertThat(row.path("featureVersions").size()).isGreaterThan(0);
        // Unsupported metric must be explicit, not silently zero.
        assertThat(row.path("unavailableReasons").path("VWAP_DISTANCE_ATR").asString()).isNotBlank();

        WIREMOCK.verify(0, getRequestedFor(urlPathEqualTo("/v1/historical/candles")));
    }

    @Test
    void diagnosticsReportTrustStateAndVersions() throws Exception {
        watchInstrument("DASHFEAT" + System.nanoTime());
        JsonNode diagnostics = getJson("/api/v1/features/diagnostics");

        assertThat(diagnostics.path("engineStatus").asString()).isEqualTo("UP");
        assertThat(diagnostics.path("watchlistCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(diagnostics.path("stateCounts").isObject()).isTrue();
        assertThat(diagnostics.path("metricGaps").isObject()).isTrue();
        assertThat(diagnostics.path("metricGaps").has("RRS_RAW")).isTrue();
        assertThat(diagnostics.path("versions").path("featureSchemaVersion").asString())
                .isEqualTo("er-feature-schema-v1");
        assertThat(diagnostics.path("versions").path("calculationVersion").asString()).isNotBlank();
        assertThat(diagnostics.path("counters").path("snapshots").asLong()).isNotNegative();
        assertThat(diagnostics.path("notes").isArray()).isTrue();
    }

    @Test
    void dashboardAndStreamShareIdentifiersAndEnvelope() throws Exception {
        long instrumentId = watchInstrument("DASHFEAT" + System.nanoTime());
        JsonNode rows = getJson("/api/v1/features/dashboard");
        JsonNode restRow = null;
        for (JsonNode candidate : rows) {
            if (candidate.path("instrumentId").asLong() == instrumentId) {
                restRow = candidate;
            }
        }
        assertThat(restRow).isNotNull();

        JsonNode envelope = firstStreamMessage();
        assertThat(envelope.path("type").asString()).isEqualTo("feature.snapshot");
        assertThat(envelope.path("version").asInt()).isEqualTo(1);
        JsonNode streamRow = null;
        for (JsonNode candidate : envelope.path("payload")) {
            if (candidate.path("instrumentId").asLong() == restRow.path("instrumentId").asLong()) {
                streamRow = candidate;
            }
        }
        assertThat(streamRow).isNotNull();
        assertThat(streamRow.path("instrumentId").asLong()).isEqualTo(restRow.path("instrumentId").asLong());
        assertThat(streamRow.path("timeframe").asString()).isEqualTo(restRow.path("timeframe").asString());
        assertThat(streamRow.path("featureSchemaVersion").asString())
                .isEqualTo(restRow.path("featureSchemaVersion").asString());
    }

    @Test
    void seriesIsBoundedAndChronological() throws Exception {
        long instrumentId = watchInstrument("DASHFEAT" + System.nanoTime());
        List<LocalDate> days = recentTradingDays(2);
        Instant from = calendar.sessionOpen(days.get(0));
        Instant to = calendar.sessionClose(days.get(1));
        JsonNode series = getJson("/api/v1/features/series?instrumentId=" + instrumentId
                + "&timeframe=M5&from=" + from + "&to=" + to + "&limit=5");

        assertThat(series.isArray()).isTrue();
        assertThat(series.size()).isLessThanOrEqualTo(5);
        Instant previous = Instant.MIN;
        for (JsonNode snapshot : series) {
            Instant anchor = Instant.parse(snapshot.path("anchorTimestamp").asString());
            assertThat(anchor).isAfterOrEqualTo(previous);
            previous = anchor;
        }
    }

    @Test
    void instrumentWithoutCandlesIsUnavailableButDoesNotBlankTheDashboard() throws Exception {
        long seeded = watchInstrument("DASHFEAT" + System.nanoTime());
        long empty = addToWatchlist("DASHNULL" + System.nanoTime());

        JsonNode rows = getJson("/api/v1/features/dashboard");
        JsonNode emptyRow = findRow(rows, empty);
        assertThat(emptyRow).isNotNull();
        assertThat(emptyRow.path("quality").asString()).isEqualTo("UNAVAILABLE");
        assertThat(emptyRow.path("availability").asString()).isEqualTo("MISSING_INPUT");
        assertThat(emptyRow.path("qualityReason").asString()).isNotBlank();
        assertThat(emptyRow.path("rrsRaw").isNull()).isTrue();
        assertThat(findRow(rows, seeded)).isNotNull();

        JsonNode diagnostics = getJson("/api/v1/features/diagnostics");
        assertThat(diagnostics.path("stateCounts").path("UNAVAILABLE").asInt()).isGreaterThanOrEqualTo(1);
    }

    private static JsonNode findRow(JsonNode rows, long instrumentId) {
        for (JsonNode row : rows) {
            if (row.path("instrumentId").asLong() == instrumentId) {
                return row;
            }
        }
        return null;
    }

    private long addToWatchlist(String symbol) {
        return watchlist
                .add(new AddWatchlistItemRequest("NSE", "CASH", "EQUITY", symbol, symbol, null, null, null))
                .instrumentId();
    }

    private long watchInstrument(String symbol) {
        AddWatchlistItemRequest request = new AddWatchlistItemRequest(
                "NSE", "CASH", "EQUITY", symbol, symbol, null, null, null);
        var entry = watchlist.add(request);
        long instrumentId = entry.instrumentId();
        ensureIndex();
        List<LocalDate> days = recentTradingDays(2);
        seedSession(instrumentId, days.get(0), 100, 100.0);
        seedSession(instrumentId, days.get(1), 120, 100.5);
        return instrumentId;
    }

    private void ensureIndex() {
        long benchmarkId = canonical.ensureInstrument("NSE", "CASH", "INDEX", "NIFTY", "NIFTY 50", null, null);
        List<LocalDate> days = recentTradingDays(2);
        seedSession(benchmarkId, days.get(0), 500, 20000.0);
        seedSession(benchmarkId, days.get(1), 520, 20000.0);
    }

    private List<LocalDate> recentTradingDays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate date = LocalDate.now(IST).minusDays(1);
        while (days.size() < count) {
            if (calendar.isTradingDay(date)) {
                days.add(0, date);
            }
            date = date.minusDays(1);
        }
        return days;
    }

    private void seedSession(long instrumentId, LocalDate date, long volume, double basePrice) {
        long timeframeId = canonical.ensureTimeframe("M1");
        Instant open = calendar.sessionOpen(date);
        List<NewCandle> candles = new ArrayList<>();
        int minutes = (int) calendar.sessionMinutes();
        for (int minute = 0; minute < minutes; minute++) {
            Instant barOpen = open.plusSeconds(60L * minute);
            double price = basePrice + minute * 0.001;
            candles.add(new NewCandle(
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

    private JsonNode firstStreamMessage() throws Exception {
        CompletableFuture<String> first = new CompletableFuture<>();
        HttpClient client = HttpClient.newHttpClient();
        WebSocket socket = client.newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://127.0.0.1:" + port + "/ws/features"),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                first.complete(data.toString());
                                webSocket.request(1);
                                return null;
                            }
                        })
                .get(10, TimeUnit.SECONDS);
        try {
            return JSON.readTree(first.get(20, TimeUnit.SECONDS));
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = send(path);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
