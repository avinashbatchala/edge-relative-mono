package com.edgerelative.application.reference;

import java.util.HashSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds the NSE trading calendar from configuration. */
@Configuration
@EnableConfigurationProperties(MarketCalendarProperties.class)
public class MarketCalendarConfiguration {

    @Bean
    public NseTradingCalendar nseTradingCalendar(MarketCalendarProperties properties) {
        return new NseTradingCalendar(new HashSet<>(properties.getHolidays()));
    }
}
