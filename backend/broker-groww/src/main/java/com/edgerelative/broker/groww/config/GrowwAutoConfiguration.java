package com.edgerelative.broker.groww.config;

import com.edgerelative.broker.groww.GrowwBrokerAdapter;
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
import com.edgerelative.broker.groww.execution.GrowwDisabledExecutionAdapter;
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
import com.edgerelative.broker.groww.resilience.GrowwRateLimiter;
import com.edgerelative.broker.groww.resilience.GrowwRetryPolicy;
import com.edgerelative.broker.groww.resilience.GrowwWaiter;
import com.edgerelative.broker.groww.resilience.SlidingWindowGrowwRateLimiter;
import com.edgerelative.broker.groww.resilience.ThreadSleepingGrowwWaiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the Groww adapter. Registered as a Spring Boot auto-configuration so the application only has
 * to depend on this module; the application never constructs Groww beans by hand.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "broker.groww", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GrowwProperties.class)
public class GrowwAutoConfiguration {

    public GrowwAutoConfiguration(GrowwProperties properties) {
        properties.validate();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock growwClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(RandomGenerator.class)
    RandomGenerator growwRandom() {
        return RandomGenerator.getDefault();
    }

    @Bean
    GrowwWaiter growwWaiter() {
        return new ThreadSleepingGrowwWaiter();
    }

    @Bean
    JsonMapper growwJsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    HttpClient growwHttpClient(GrowwProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    GrowwErrorDecoder growwErrorDecoder() {
        return new GrowwErrorDecoder();
    }

    @Bean
    GrowwResponseDecoder growwResponseDecoder(JsonMapper mapper, GrowwErrorDecoder errorDecoder) {
        return new GrowwResponseDecoder(mapper, errorDecoder);
    }

    @Bean
    GrowwRequestFactory growwRequestFactory(GrowwProperties properties, JsonMapper mapper) {
        return new GrowwRequestFactory(properties, mapper);
    }

    @Bean
    GrowwHttpClient growwHttpClientAdapter(HttpClient client, GrowwResponseDecoder decoder) {
        return new GrowwHttpClient(client, decoder);
    }

    @Bean
    GrowwMapper growwMapper(JsonMapper mapper) {
        return new GrowwMapper(mapper);
    }

    @Bean
    GrowwMetrics growwMetrics(ObjectProvider<MeterRegistry> registry) {
        MeterRegistry resolved = registry.getIfAvailable(SimpleMeterRegistry::new);
        return new GrowwMetrics(resolved);
    }

    @Bean
    GrowwRateLimiter growwRateLimiter(
            GrowwProperties properties, Clock clock, GrowwWaiter waiter, GrowwMetrics metrics) {
        return new SlidingWindowGrowwRateLimiter(properties, clock, waiter, metrics);
    }

    @Bean
    GrowwCooldownManager growwCooldownManager(
            GrowwProperties properties, Clock clock, GrowwWaiter waiter, GrowwMetrics metrics) {
        return new GrowwCooldownManager(clock, waiter, metrics, properties.getCooldown().getDefaultDuration());
    }

    @Bean
    GrowwCircuitBreaker growwCircuitBreaker(GrowwProperties properties, Clock clock, GrowwMetrics metrics) {
        GrowwProperties.CircuitBreaker config = properties.getCircuitBreaker();
        return new GrowwCircuitBreaker(
                clock, metrics, config.getFailureThreshold(), config.getOpenDuration(), config.getHalfOpenProbes());
    }

    @Bean
    GrowwRetryPolicy growwRetryPolicy(
            GrowwProperties properties, Clock clock, RandomGenerator random, GrowwWaiter waiter, GrowwMetrics metrics) {
        return new GrowwRetryPolicy(properties, clock, random, waiter, metrics);
    }

    @Bean
    GrowwHealthMonitor growwHealthMonitor(
            Clock clock, GrowwCooldownManager cooldown, GrowwCircuitBreaker circuitBreaker) {
        return new GrowwHealthMonitor(clock, cooldown, circuitBreaker);
    }

    @Bean
    GrowwCallExecutor growwCallExecutor(
            GrowwProperties properties,
            GrowwRateLimiter rateLimiter,
            GrowwCooldownManager cooldown,
            GrowwCircuitBreaker circuitBreaker,
            GrowwRetryPolicy retryPolicy,
            GrowwMetrics metrics,
            GrowwHealthMonitor health,
            Clock clock) {
        return new GrowwCallExecutor(
                rateLimiter,
                cooldown,
                circuitBreaker,
                retryPolicy,
                metrics,
                health,
                clock,
                properties.getMaxInFlight(),
                properties.getOperationTimeout());
    }

    @Bean
    GrowwAuthenticationClient growwAuthenticationClient(
            GrowwProperties properties,
            GrowwCallExecutor callExecutor,
            GrowwHttpClient http,
            GrowwRequestFactory requests,
            JsonMapper mapper,
            Clock clock) {
        return new GrowwAuthenticationClient(properties, callExecutor, http, requests, mapper, clock);
    }

    @Bean
    GrowwAccessTokenProvider growwAccessTokenProvider(
            GrowwProperties properties,
            GrowwAuthenticationClient authenticationClient,
            Clock clock,
            GrowwMetrics metrics) {
        return new GrowwAccessTokenProvider(properties, authenticationClient, clock, metrics);
    }

    @Bean
    GrowwAuthorizedExecutor growwAuthorizedExecutor(
            GrowwCallExecutor callExecutor, GrowwAccessTokenProvider tokenProvider) {
        return new GrowwAuthorizedExecutor(callExecutor, tokenProvider);
    }

    @Bean(destroyMethod = "close")
    ExecutorService growwBulkExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    GrowwMarketDataClient growwMarketDataClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        return new GrowwMarketDataClient(executor, http, requests, mapper);
    }

    @Bean
    GrowwHistoricalRangeSplitter growwHistoricalRangeSplitter() {
        return new GrowwHistoricalRangeSplitter();
    }

    @Bean
    GrowwHistoricalDataClient growwHistoricalDataClient(
            GrowwProperties properties,
            GrowwAuthorizedExecutor executor,
            GrowwHttpClient http,
            GrowwRequestFactory requests,
            GrowwMapper mapper,
            GrowwHistoricalRangeSplitter splitter,
            ExecutorService bulkExecutor) {
        return new GrowwHistoricalDataClient(
                executor,
                http,
                requests,
                mapper,
                splitter,
                bulkExecutor,
                properties.getBulkMaxConcurrency());
    }

    @Bean
    GrowwPortfolioClient growwPortfolioClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        return new GrowwPortfolioClient(executor, http, requests, mapper);
    }

    @Bean
    GrowwOrderQueryClient growwOrderQueryClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        return new GrowwOrderQueryClient(executor, http, requests, mapper);
    }

    @Bean
    GrowwMarginClient growwMarginClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        return new GrowwMarginClient(executor, http, requests, mapper);
    }

    @Bean
    GrowwSmartOrderQueryClient growwSmartOrderQueryClient(
            GrowwAuthorizedExecutor executor, GrowwHttpClient http, GrowwRequestFactory requests, GrowwMapper mapper) {
        return new GrowwSmartOrderQueryClient(executor, http, requests, mapper);
    }

    @Bean
    GrowwInstrumentCsvParser growwInstrumentCsvParser(GrowwMapper mapper) {
        return new GrowwInstrumentCsvParser(mapper);
    }

    @Bean
    GrowwInstrumentClient growwInstrumentClient(
            GrowwProperties properties,
            GrowwCallExecutor callExecutor,
            GrowwHttpClient http,
            GrowwRequestFactory requests,
            GrowwInstrumentCsvParser parser,
            Clock clock) {
        return new GrowwInstrumentClient(properties, callExecutor, http, requests, parser, clock);
    }

    @Bean
    GrowwDisabledExecutionAdapter growwDisabledExecutionAdapter() {
        return new GrowwDisabledExecutionAdapter();
    }

    @Bean
    GrowwBrokerAdapter growwBrokerAdapter() {
        return new GrowwBrokerAdapter();
    }
}
