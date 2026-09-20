package com.edgerelative.application.tradeplan.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TradePlanConfiguration {

    /** Fail-closed default: without an authoritative freshness producer, inputs are not confirmed. */
    @Bean
    @ConditionalOnMissingBean(TradePlanTrustPort.class)
    public TradePlanTrustPort unavailableTradePlanTrustPort() {
        return row -> false;
    }
}
