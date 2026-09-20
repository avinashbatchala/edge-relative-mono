package com.edgerelative.application.reference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the NSE trading calendar from configuration.
 */
@Configuration
@EnableConfigurationProperties(MarketCalendarProperties.class)
public class MarketCalendarConfiguration {

    @Bean
    public NseTradingCalendar nseTradingCalendar(MarketCalendarProperties properties) {
        Map<java.time.LocalDate, NseTradingCalendar.Session> specialSessions = new HashMap<>();
        for (MarketCalendarProperties.SpecialSession session : properties.getSpecialSessions()) {
            if (session.getDate() != null && session.getOpen() != null && session.getClose() != null) {
                specialSessions.put(
                        session.getDate(), new NseTradingCalendar.Session(session.getOpen(), session.getClose()));
            }
        }
        return new NseTradingCalendar(new HashSet<>(properties.getHolidays()), specialSessions);
    }
}
