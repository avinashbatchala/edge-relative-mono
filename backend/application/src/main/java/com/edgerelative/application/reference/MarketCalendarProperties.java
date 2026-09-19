package com.edgerelative.application.reference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exchange-calendar configuration.
 *
 * <p>Holidays are data, not code: they are maintained per the official NSE holiday master for the
 * Capital Market (equities) segment and bound from configuration so a new annual circular is a
 * config change, not a code change.
 */
@ConfigurationProperties(prefix = "market.calendar")
public class MarketCalendarProperties {

    /** Trading holidays (ISO dates) for NSE cash equities. */
    private List<LocalDate> holidays = new ArrayList<>();

    public List<LocalDate> getHolidays() {
        return holidays;
    }

    public void setHolidays(List<LocalDate> holidays) {
        this.holidays = holidays;
    }
}
