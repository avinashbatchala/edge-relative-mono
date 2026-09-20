package com.edgerelative.application.feature.policy;

import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.engine.FeatureMetrics;
import com.edgerelative.application.feature.engine.LiveFeatureStore;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the immutable feature policy/versions and the pure engine.
 */
@Configuration
@EnableConfigurationProperties(FeatureProperties.class)
public class FeatureConfiguration {

    @Bean
    public FeaturePolicy featurePolicy(FeatureProperties properties) {
        properties.validate();
        return properties.toPolicy();
    }

    @Bean
    public FeatureVersions featureVersions(FeaturePolicy policy) {
        return new FeatureVersions(policy);
    }

    @Bean
    public FeatureEngine featureEngine() {
        return new FeatureEngine();
    }

    @Bean
    public FeatureMetrics featureMetrics(MeterRegistry registry) {
        return new FeatureMetrics(registry);
    }

    @Bean
    public LiveFeatureStore liveFeatureStore(FeatureEngine engine, FeatureProperties properties) {
        return new LiveFeatureStore(engine, properties.getLive().getMaxBars());
    }

    /**
     * Injected clock for genuinely clock-dependent orchestration only; never used in calculations.
     */
    @Bean
    public Clock featureClock() {
        return Clock.systemUTC();
    }
}
