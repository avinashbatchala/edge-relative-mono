package com.edgerelative.application.strategy.application;

import com.edgerelative.application.strategy.domain.StrategyEngine;
import com.edgerelative.application.strategy.domain.family.SetupFamilyRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StrategyProperties.class)
public class StrategyConfiguration {

    @Bean
    public SetupFamilyRegistry setupFamilyRegistry() {
        return SetupFamilyRegistry.production();
    }

    @Bean
    public StrategyEngine strategyEngine(SetupFamilyRegistry setupFamilyRegistry) {
        return new StrategyEngine(setupFamilyRegistry);
    }

    @Bean
    public StrategyParametersProvider strategyParametersProvider(StrategyProperties properties) {
        return new StrategyParametersProvider(properties.toParameters());
    }
}
