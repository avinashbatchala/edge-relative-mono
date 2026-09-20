package com.edgerelative.broker.groww.support;

import com.edgerelative.broker.groww.auth.GrowwAccessTokenProvider;
import com.edgerelative.broker.groww.auth.GrowwAuthenticationClient;
import com.edgerelative.broker.groww.auth.GrowwAuthorizedExecutor;
import com.edgerelative.broker.groww.client.GrowwHistoricalDataClient;
import com.edgerelative.broker.groww.client.GrowwHistoricalRangeSplitter;
import com.edgerelative.broker.groww.client.GrowwInstrumentClient;
import com.edgerelative.broker.groww.client.GrowwInstrumentCsvParser;
import com.edgerelative.broker.groww.client.GrowwMarginClient;
import com.edgerelative.broker.groww.client.GrowwMarketDataClient;
import com.edgerelative.broker.groww.client.GrowwOrderQueryClient;
import com.edgerelative.broker.groww.client.GrowwPortfolioClient;
import com.edgerelative.broker.groww.client.GrowwSmartOrderQueryClient;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.health.GrowwHealthMonitor;
import com.edgerelative.broker.groww.http.GrowwErrorDecoder;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.http.GrowwResponseDecoder;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import com.edgerelative.broker.groww.resilience.GrowwCircuitBreaker;
import com.edgerelative.broker.groww.resilience.GrowwCooldownManager;
import com.edgerelative.broker.groww.resilience.GrowwRetryPolicy;
import com.edgerelative.broker.groww.resilience.GrowwWaiter;
import com.edgerelative.broker.groww.resilience.SlidingWindowGrowwRateLimiter;
import com.edgerelative.broker.groww.resilience.ThreadSleepingGrowwWaiter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a fully wired Groww adapter graph pointed at a WireMock server.
 */
public final class GrowwTestFixture implements AutoCloseable {

    private final WireMockServer server;
    private final ExecutorService bulkExecutor;
    private final GrowwProperties properties;

    private final GrowwMarketDataClient marketData;
    private final GrowwHistoricalDataClient historical;
    private final GrowwPortfolioClient portfolio;
    private final GrowwOrderQueryClient orderQuery;
    private final GrowwMarginClient margin;
    private final GrowwSmartOrderQueryClient smartOrders;
    private final GrowwInstrumentClient instruments;

    public GrowwTestFixture() {
        this(properties -> {
        });
    }

    public GrowwTestFixture(java.util.function.Consumer<GrowwProperties> customizer) {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        this.properties = GrowwPropertiesBuilder.defaults();
        customizer.accept(properties);
        properties.setBaseUrl(server.baseUrl());
        properties.setInstrumentMasterUrl(server.baseUrl() + "/instruments/instrument.csv");
        properties.setOperationTimeout(Duration.ofSeconds(30));
        properties.getRetry().setMaxAttempts(1);

        Clock clock = Clock.systemUTC();
        GrowwWaiter waiter = new ThreadSleepingGrowwWaiter();
        GrowwMetrics metrics = new GrowwMetrics(new SimpleMeterRegistry());
        JsonMapper json = JsonMapper.builder().build();
        GrowwMapper mapper = new GrowwMapper(json);

        GrowwErrorDecoder errorDecoder = new GrowwErrorDecoder();
        GrowwResponseDecoder responseDecoder = new GrowwResponseDecoder(json, errorDecoder);
        GrowwRequestFactory requests = new GrowwRequestFactory(properties, json);
        GrowwHttpClient http = new GrowwHttpClient(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(), responseDecoder);

        GrowwCooldownManager cooldown =
                new GrowwCooldownManager(clock, waiter, metrics, properties.getCooldown().getDefaultDuration());
        GrowwCircuitBreaker circuitBreaker = new GrowwCircuitBreaker(
                clock,
                metrics,
                properties.getCircuitBreaker().getFailureThreshold(),
                properties.getCircuitBreaker().getOpenDuration(),
                properties.getCircuitBreaker().getHalfOpenProbes());
        GrowwHealthMonitor health = new GrowwHealthMonitor(clock, cooldown, circuitBreaker);
        GrowwRetryPolicy retryPolicy =
                new GrowwRetryPolicy(properties, clock, RandomGenerator.getDefault(), waiter, metrics);
        GrowwCallExecutor callExecutor = new GrowwCallExecutor(
                new SlidingWindowGrowwRateLimiter(properties, clock, waiter, metrics),
                cooldown,
                circuitBreaker,
                retryPolicy,
                metrics,
                health,
                clock,
                properties.getMaxInFlight(),
                properties.getOperationTimeout());

        GrowwAuthenticationClient authenticationClient =
                new GrowwAuthenticationClient(properties, callExecutor, http, requests, json, clock);
        GrowwAccessTokenProvider tokenProvider =
                new GrowwAccessTokenProvider(properties, authenticationClient, clock, metrics);
        GrowwAuthorizedExecutor authorizedExecutor = new GrowwAuthorizedExecutor(callExecutor, tokenProvider);

        this.bulkExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.marketData = new GrowwMarketDataClient(authorizedExecutor, http, requests, mapper);
        this.historical = new GrowwHistoricalDataClient(
                authorizedExecutor, http, requests, mapper, new GrowwHistoricalRangeSplitter(),
                bulkExecutor, properties.getBulkMaxConcurrency());
        this.portfolio = new GrowwPortfolioClient(authorizedExecutor, http, requests, mapper);
        this.orderQuery = new GrowwOrderQueryClient(authorizedExecutor, http, requests, mapper);
        this.margin = new GrowwMarginClient(authorizedExecutor, http, requests, mapper);
        this.smartOrders = new GrowwSmartOrderQueryClient(authorizedExecutor, http, requests, mapper);
        this.instruments = new GrowwInstrumentClient(
                properties, callExecutor, http, requests, new GrowwInstrumentCsvParser(mapper), clock);
    }

    public WireMockServer server() {
        return server;
    }

    public GrowwProperties properties() {
        return properties;
    }

    public GrowwMarketDataClient marketData() {
        return marketData;
    }

    public GrowwHistoricalDataClient historical() {
        return historical;
    }

    public GrowwPortfolioClient portfolio() {
        return portfolio;
    }

    public GrowwOrderQueryClient orderQuery() {
        return orderQuery;
    }

    public GrowwMarginClient margin() {
        return margin;
    }

    public GrowwSmartOrderQueryClient smartOrders() {
        return smartOrders;
    }

    public GrowwInstrumentClient instruments() {
        return instruments;
    }

    @Override
    public void close() {
        bulkExecutor.close();
        server.stop();
    }
}
