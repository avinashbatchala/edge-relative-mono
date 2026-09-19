package com.edgerelative.application.watchlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
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

/** End-to-end coverage of the watchlist API over the real schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WatchlistIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("watchlist_test")
            .withUsername("watchlist_test")
            .withPassword("watchlist_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearWatchlist() throws Exception {
        JsonNode watchlist = get("/api/v1/watchlist");
        for (JsonNode entry : watchlist.path("entries")) {
            delete("/api/v1/watchlist/items/" + entry.path("instrumentId").asLong());
        }
    }

    @Test
    void addsAndListsACanonicalInstrument() throws Exception {
        var added = addInstrument("RELIANCE", "Reliance Industries Ltd", "NSE-RELIANCE");
        assertThat(added.statusCode()).isEqualTo(201);
        JsonNode entry = JSON.readTree(added.body());
        assertThat(entry.path("instrumentId").asLong()).isPositive();
        assertThat(entry.path("exchange").asString()).isEqualTo("NSE");
        assertThat(entry.path("symbol").asString()).isEqualTo("RELIANCE");
        assertThat(entry.path("brokerSymbol").asString()).isEqualTo("NSE-RELIANCE");

        JsonNode watchlist = get("/api/v1/watchlist");
        assertThat(watchlist.path("capacity").asInt()).isEqualTo(20);
        assertThat(watchlist.path("count").asInt()).isEqualTo(1);
        assertThat(watchlist.path("entries").get(0).path("symbol").asString()).isEqualTo("RELIANCE");
    }

    @Test
    void rejectsDuplicatesAndPreservesCanonicalIdentity() throws Exception {
        JsonNode first = JSON.readTree(addInstrument("TCS", "Tata Consultancy", "NSE-TCS").body());
        long instrumentId = first.path("instrumentId").asLong();

        var duplicate = addInstrument("TCS", "Tata Consultancy", "NSE-TCS");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(duplicate.body()).path("code").asString()).isEqualTo("WATCHLIST_DUPLICATE");

        // The canonical id is deterministic across sessions/requests.
        delete("/api/v1/watchlist/items/" + instrumentId);
        JsonNode again = JSON.readTree(addInstrument("TCS", "Tata Consultancy", "NSE-TCS").body());
        assertThat(again.path("instrumentId").asLong()).isEqualTo(instrumentId);
    }

    @Test
    void removesAnInstrument() throws Exception {
        JsonNode added = JSON.readTree(addInstrument("INFY", "Infosys", "NSE-INFY").body());
        long instrumentId = added.path("instrumentId").asLong();

        assertThat(delete("/api/v1/watchlist/items/" + instrumentId).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/watchlist").path("count").asInt()).isZero();
        assertThat(delete("/api/v1/watchlist/items/" + instrumentId).statusCode()).isEqualTo(404);
    }

    @Test
    void enforcesTheTwentyInstrumentMaximum() throws Exception {
        for (int i = 0; i < WatchlistService.CAPACITY; i++) {
            assertThat(addInstrument("SYM" + i, "Symbol " + i, "NSE-SYM" + i).statusCode())
                    .as("add %d", i)
                    .isEqualTo(201);
        }

        var overflow = addInstrument("OVERFLOW", "Overflow", "NSE-OVERFLOW");
        assertThat(overflow.statusCode()).isEqualTo(409);
        JsonNode error = JSON.readTree(overflow.body());
        assertThat(error.path("code").asString()).isEqualTo("WATCHLIST_FULL");
        assertThat(get("/api/v1/watchlist").path("count").asInt()).isEqualTo(WatchlistService.CAPACITY);
    }

    @Test
    void reordersTheWatchlist() throws Exception {
        long first = JSON.readTree(addInstrument("AAA", "Aaa", "NSE-AAA").body()).path("instrumentId").asLong();
        long second = JSON.readTree(addInstrument("BBB", "Bbb", "NSE-BBB").body()).path("instrumentId").asLong();

        var response = putOrder("{\"instrumentIds\":[%d,%d]}".formatted(second, first));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode entries = JSON.readTree(response.body()).path("entries");
        assertThat(entries.get(0).path("symbol").asString()).isEqualTo("BBB");
        assertThat(entries.get(1).path("symbol").asString()).isEqualTo("AAA");
    }

    // --- helpers ------------------------------------------------------------------

    private HttpResponse<String> addInstrument(String symbol, String name, String brokerSymbol) throws Exception {
        String body = JSON.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                "exchange", "NSE",
                "segment", "CASH",
                "instrumentType", "EQ",
                "symbol", symbol,
                "name", name,
                "brokerSymbol", brokerSymbol,
                "tickSize", 0.05,
                "lotSize", 1)));
        return post("/api/v1/watchlist/items", body);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpResponse<String> putOrder(String body) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/v1/watchlist/order"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .DELETE()
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
