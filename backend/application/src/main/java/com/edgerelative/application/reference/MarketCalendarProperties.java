package com.edgerelative.application.reference;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exchange-calendar configuration.
 *
 * <p>Holidays are data, not code: they are maintained per the official NSE holiday master for the
 * Capital Market (equities) segment and bound from configuration so a new annual circular is a
 * config change, not a code change. Special/shortened sessions (DD-05 §116) are likewise data: a
 * date plus an explicit open/close window.
 */
@ConfigurationProperties(prefix = "market.calendar")
public class MarketCalendarProperties {

    /**
     * Trading holidays (ISO dates) for NSE cash equities.
     */
    private List<LocalDate> holidays = new ArrayList<>();

    /**
     * Special or shortened sessions (DD-05 §116): Muhurat trading, ad-hoc half days. Dates listed
     * here are trading days with the given window even if they fall on a weekend or holiday.
     */
    private List<SpecialSession> specialSessions = new ArrayList<>();

    public List<LocalDate> getHolidays() {
        return holidays;
    }

    public void setHolidays(List<LocalDate> holidays) {
        this.holidays = holidays;
    }

    public List<SpecialSession> getSpecialSessions() {
        return specialSessions;
    }

    public void setSpecialSessions(List<SpecialSession> specialSessions) {
        this.specialSessions = specialSessions;
    }

    /**
     * One explicit session window for a date.
     */
    public static class SpecialSession {

        private LocalDate date;
        private LocalTime open;
        private LocalTime close;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public LocalTime getOpen() {
            return open;
        }

        public void setOpen(LocalTime open) {
            this.open = open;
        }

        public LocalTime getClose() {
            return close;
        }

        public void setClose(LocalTime close) {
            this.close = close;
        }
    }
}
