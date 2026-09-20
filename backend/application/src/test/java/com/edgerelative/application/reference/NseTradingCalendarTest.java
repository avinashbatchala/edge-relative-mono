package com.edgerelative.application.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Session semantics used by higher-timeframe candle construction.
 */
class NseTradingCalendarTest {

    private final NseTradingCalendar calendar = new NseTradingCalendar(Set.of(LocalDate.of(2026, 1, 26)));

    @Test
    void configuredHolidaysAndWeekendsAreNotTradingDays() {
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 1, 26))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 1, 24))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 1, 25))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 1, 27))).isTrue();
    }

    @Test
    void sessionOpensAt0915Ist() {
        assertThat(calendar.sessionOpen(LocalDate.of(2026, 1, 27))).isEqualTo(Instant.parse("2026-01-27T03:45:00Z"));
        assertThat(calendar.sessionClose(LocalDate.of(2026, 1, 27))).isEqualTo(Instant.parse("2026-01-27T10:00:00Z"));
    }

    @Test
    void sessionDateUsesKolkataAndWeekStartsMonday() {
        Instant istMorning = Instant.parse("2026-01-27T02:00:00Z");
        assertThat(calendar.sessionDate(istMorning)).isEqualTo(LocalDate.of(2026, 1, 27));
        assertThat(calendar.weekStart(LocalDate.of(2026, 1, 27))).isEqualTo(LocalDate.of(2026, 1, 26));
    }

    @Test
    void sessionLengthIsThreeHundredAndSeventyFiveMinutes() {
        assertThat(calendar.sessionMinutes()).isEqualTo(375);
    }

    /**
     * DD01 §131 / DD05 §115: the host default timezone must not change session semantics. These
     * instants are absolute; the test passes under any JVM default zone.
     */
    @Test
    void sessionInstantsAreIndependentOfTheHostDefaultTimezone() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            for (String zone : java.util.List.of(
                    "UTC", "Asia/Kolkata", "America/New_York", "Pacific/Kiritimati")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
                assertThat(calendar.sessionOpen(LocalDate.of(2026, 9, 18)))
                        .isEqualTo(Instant.parse("2026-09-18T03:45:00Z"));
                assertThat(calendar.sessionClose(LocalDate.of(2026, 9, 18)))
                        .isEqualTo(Instant.parse("2026-09-18T10:00:00Z"));
                // 18:30 UTC is midnight IST of the next day regardless of host zone.
                assertThat(calendar.sessionDate(Instant.parse("2026-09-18T18:30:00Z")))
                        .isEqualTo(LocalDate.of(2026, 9, 19));
            }
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }
}
