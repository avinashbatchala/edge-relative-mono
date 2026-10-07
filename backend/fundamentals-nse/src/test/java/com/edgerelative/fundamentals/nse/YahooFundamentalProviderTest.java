package com.edgerelative.fundamentals.nse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.fundamentals.api.error.FundamentalNotFoundException;
import com.edgerelative.fundamentals.api.error.FundamentalUnavailableException;
import com.edgerelative.fundamentals.api.model.FundamentalMetric;
import com.edgerelative.fundamentals.api.model.FundamentalRequest;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;
import com.edgerelative.fundamentals.nse.config.YahooFinanceProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class YahooFundamentalProviderTest {

    private static final Instant AS_OF = Instant.parse("2026-04-01T00:00:00Z");

    private WireMockServer server;
    private YahooFundamentalProvider provider;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        YahooFinanceProperties properties = new YahooFinanceProperties();
        properties.setQueryBaseUrl(server.baseUrl());
        properties.setCookieBaseUrl(server.baseUrl());
        properties.setFallbackQueryBaseUrl(server.baseUrl());
        properties.setMinRequestInterval(java.time.Duration.ZERO);
        HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        provider = new YahooFundamentalProvider(properties, httpClient, JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private void stubCrumbAndSummary(String summaryBody) {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200).withHeader("Set-Cookie", "A1=test; Path=/")));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/v1/test/getcrumb"))
                .willReturn(aResponse().withStatus(200).withBody("crumb-value")));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                        urlPathEqualTo("/v10/finance/quoteSummary/RELIANCE.NS"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(summaryBody)));
    }

    @Test
    void mapsYahooSummaryIntoPointInTimeSnapshot() {
        stubCrumbAndSummary("""
                {
                  "quoteSummary": {
                    "result": [{
                      "financialData": {
                        "returnOnEquity": {"raw": 0.15},
                        "debtToEquity": {"raw": 0.4},
                        "revenueGrowth": {"raw": 0.1},
                        "earningsGrowth": {"raw": 0.08},
                        "profitMargins": {"raw": 0.12},
                        "targetMeanPrice": {"raw": 3000.0}
                      },
                      "defaultKeyStatistics": {
                        "priceToBook": {"raw": 2.5},
                        "forwardPE": {"raw": 20.0},
                        "trailingEps": {"raw": 100.0}
                      },
                      "summaryDetail": {
                        "trailingPE": {"raw": 25.0},
                        "dividendYield": {"raw": 0.004},
                        "marketCap": {"raw": 2000000000000}
                      },
                      "incomeStatementHistory": {
                        "incomeStatementHistory": [{
                          "endDate": {"raw": 1743359400},
                          "totalRevenue": {"raw": 9000000000000},
                          "grossProfit": {"raw": 3000000000000},
                          "operatingIncome": {"raw": 2000000000000},
                          "netIncome": {"raw": 1500000000000}
                        }]
                      }
                    }],
                    "error": null
                  }
                }
                """);

        FundamentalSnapshot snapshot =
                provider.fetch(FundamentalRequest.of("NSE", "RELIANCE", AS_OF));

        assertThat(snapshot.provider()).isEqualTo("yahoo-nse");
        assertThat(snapshot.exchange()).isEqualTo("NSE");
        assertThat(snapshot.symbol()).isEqualTo("RELIANCE");
        assertThat(snapshot.period().fiscalYear()).isEqualTo("FY2025");
        assertThat(snapshot.period().periodEnd()).hasToString("2025-03-31");
        assertThat(snapshot.filing().filedAt()).isEqualTo(AS_OF);
        assertThat(snapshot.filing().filedAt()).isBeforeOrEqualTo(snapshot.asOf());
        assertThat(snapshot.statements())
                .anySatisfy(line -> assertThat(line.lineCode()).isEqualTo("total_revenue"));
        assertThat(snapshot.metrics())
                .extracting(FundamentalMetric::metricCode)
                .contains("trailing_pe", "price_to_book", "return_on_equity");
        assertThat(snapshot.metrics())
                .filteredOn(metric -> metric.metricCode().equals("trailing_pe"))
                .extracting(FundamentalMetric::value)
                .containsExactly(new BigDecimal("25.0"));
    }

    @Test
    void reportsNotFoundWhenResultEmpty() {
        stubCrumbAndSummary("{\"quoteSummary\":{\"result\":[],\"error\":null}}");

        assertThatThrownBy(() -> provider.fetch(FundamentalRequest.of("NSE", "RELIANCE", AS_OF)))
                .isInstanceOf(FundamentalNotFoundException.class);
    }

    @Test
    void mapsRateLimitToUnavailable() {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200)));
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/v1/test/getcrumb"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> provider.fetch(FundamentalRequest.of("NSE", "RELIANCE", AS_OF)))
                .isInstanceOf(FundamentalUnavailableException.class);
    }
}
