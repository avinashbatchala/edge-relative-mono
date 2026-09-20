package com.edgerelative.application.risk.application;

import com.edgerelative.application.risk.application.port.RiskContextProvider;
import com.edgerelative.application.risk.domain.RiskEvaluator;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskConfiguration {

    @Bean
    public RiskEvaluator riskEvaluator() {
        return new RiskEvaluator();
    }

    /**
     * Fail-closed default until authoritative producers exist. No placeholder context is ever
     * synthesized: an absent provider yields an unavailable context and no production approval.
     */
    @Bean
    @ConditionalOnMissingBean(RiskContextProvider.class)
    public RiskContextProvider unavailableRiskContextProvider() {
        return (brokerAccountId, at) -> Optional.empty();
    }
}
