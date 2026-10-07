package com.edgerelative.fundamentals.nse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.fundamentals.api.model.FundamentalMetric;
import com.edgerelative.fundamentals.api.model.FundamentalRequest;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;
import com.edgerelative.fundamentals.api.model.StatementLine;
import com.edgerelative.fundamentals.nse.config.YahooFinanceProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the shared, frozen fundamental fixtures consumed by the (future) Python implementation too.
 */
class FundamentalFixtureTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void yahooAnnualFixtureMatches() throws IOException {
        JsonNode fixture = load("yahoo-reliance-annual-v1.json");
        String symbol = fixture.path("symbol").asString();
        String suffix = fixture.path("exchange").asString().equalsIgnoreCase("BSE") ? ".BO" : ".NS";
        Instant asOf = Instant.parse(fixture.path("asOf").asString());

        server.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200).withHeader("Set-Cookie", "A1=test; Path=/")));
        server.stubFor(get(urlEqualTo("/v1/test/getcrumb")).willReturn(aResponse().withStatus(200).withBody("crumb")));
        server.stubFor(get(urlPathEqualTo("/v10/finance/quoteSummary/" + symbol + suffix))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture.path("providerResponse").toString())));

        YahooFinanceProperties properties = new YahooFinanceProperties();
        properties.setQueryBaseUrl(server.baseUrl());
        properties.setCookieBaseUrl(server.baseUrl());
        properties.setFallbackQueryBaseUrl(server.baseUrl());
        properties.setMinRequestInterval(java.time.Duration.ZERO);
        YahooFundamentalProvider provider = new YahooFundamentalProvider(
                properties,
                HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(),
                JSON);

        FundamentalSnapshot snapshot = provider.fetch(
                new FundamentalRequest(
                        fixture.path("exchange").asString(),
                        symbol,
                        asOf,
                        com.edgerelative.fundamentals.api.model.ReportingBasis.valueOf(
                                fixture.path("expected").path("reportingBasis").asString())));

        JsonNode expected = fixture.path("expected");
        assertThat(snapshot.provider()).isEqualTo(fixture.path("provider").asString());
        assertThat(snapshot.exchange()).isEqualTo(fixture.path("exchange").asString());
        assertThat(snapshot.symbol()).isEqualTo(symbol);
        assertThat(snapshot.period().fiscalYear()).isEqualTo(expected.path("fiscalYear").asString());
        assertThat(snapshot.period().type().name()).isEqualTo(expected.path("periodType").asString());
        assertThat(snapshot.period().basis().name()).isEqualTo(expected.path("reportingBasis").asString());
        assertThat(snapshot.period().periodEnd()).hasToString(expected.path("periodEnd").asString());
        assertThat(snapshot.filing().filedAt()).isEqualTo(Instant.parse(expected.path("filedAt").asString()));

        Map<String, StatementLine> statements =
                snapshot.statements().stream().collect(Collectors.toMap(StatementLine::lineCode, Function.identity()));
        assertThat(statements.keySet()).containsExactlyInAnyOrderElementsOf(expectedStatements(expected));
        for (JsonNode line : expected.path("statements")) {
            StatementLine actual = statements.get(line.path("lineCode").asString());
            assertThat(actual).as(line.path("lineCode").asString()).isNotNull();
            assertThat(actual.value()).isEqualByComparingTo(new BigDecimal(line.path("value").asString()));
            assertThat(actual.unit()).isEqualTo(line.path("unit").asString());
            assertThat(actual.scale()).isEqualTo(line.path("scale").asString());
        }

        Map<String, FundamentalMetric> metrics =
                snapshot.metrics().stream().collect(Collectors.toMap(FundamentalMetric::metricCode, Function.identity()));
        assertThat(metrics.keySet()).containsExactlyInAnyOrderElementsOf(expectedMetrics(expected));
        for (JsonNode metric : expected.path("metrics")) {
            FundamentalMetric actual = metrics.get(metric.path("metricCode").asString());
            assertThat(actual).as(metric.path("metricCode").asString()).isNotNull();
            assertThat(actual.value()).isEqualByComparingTo(new BigDecimal(metric.path("value").asString()));
            assertThat(actual.unit()).isEqualTo(metric.path("unit").asString());
        }
    }

    private static java.util.List<String> expectedStatements(JsonNode expected) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        expected.path("statements").forEach(node -> codes.add(node.path("lineCode").asString()));
        return codes;
    }

    private static java.util.List<String> expectedMetrics(JsonNode expected) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        expected.path("metrics").forEach(node -> codes.add(node.path("metricCode").asString()));
        return codes;
    }

    private static JsonNode load(String name) throws IOException {
        return JSON.readTree(Files.readString(fixturesDir().resolve(name)));
    }

    private static Path fixturesDir() {
        Path working = Path.of(System.getProperty("user.dir"));
        for (Path candidate = working; candidate != null; candidate = candidate.getParent()) {
            Path fixtures = candidate.resolve("contracts").resolve("fixtures").resolve("fundamentals");
            if (Files.isDirectory(fixtures)) {
                return fixtures;
            }
        }
        throw new IllegalStateException("contracts/fixtures/fundamentals not found from " + working);
    }
}
