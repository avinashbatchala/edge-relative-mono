package com.edgerelative.application.reference;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Minimal NSE cash-session calendar.
 *
 * <p>Provides the session boundaries and day/week semantics higher-timeframe candle construction
 * depends on. It is deliberately small for now: weekends plus an explicit holiday set. The version
 * is part of candle lineage, so changing session rules must change it.
 */
public final class NseTradingCalendar {

    public static final ZoneId EXCHANGE_ZONE = ZoneId.of("Asia/Kolkata");
    public static final String VERSION = "nse-session-v1";

    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 15);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);

    private final Set<LocalDate> holidays;

    public NseTradingCalendar(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    /** Weekends-only calendar; suitable until a dated holiday source is wired in. */
    public static NseTradingCalendar weekendsOnly() {
        return new NseTradingCalendar(Set.of());
    }

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    public LocalDate sessionDate(Instant instant) {
        return instant.atZone(EXCHANGE_ZONE).toLocalDate();
    }

    /** Number of one-minute slots in a normal session (375 for 09:15–15:30). */
    public long sessionMinutes() {
        return Duration.between(SESSION_OPEN, SESSION_CLOSE).toMinutes();
    }

    public Instant sessionOpen(LocalDate date) {
        return date.atTime(SESSION_OPEN).atZone(EXCHANGE_ZONE).toInstant();
    }

    public Instant sessionClose(LocalDate date) {
        return date.atTime(SESSION_CLOSE).atZone(EXCHANGE_ZONE).toInstant();
    }

    public LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
