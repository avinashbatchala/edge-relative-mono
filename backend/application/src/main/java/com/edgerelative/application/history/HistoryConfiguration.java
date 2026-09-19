package com.edgerelative.application.history;

import com.edgerelative.application.reference.NseTradingCalendar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Enables strongly typed backfill configuration and the canonical aggregation beans. */
@Configuration
@EnableConfigurationProperties(HistoryProperties.class)
public class HistoryConfiguration {

    @Bean
    public CandleAggregator candleAggregator(NseTradingCalendar calendar) {
        return new CandleAggregator(calendar);
    }
}
