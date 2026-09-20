package com.edgerelative.application.reference;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Set;

/**
 * NSE cash-session calendar.
 *
 * <p>Provides the session boundaries and day/week semantics higher-timeframe candle construction
 * depends on: weekends, an explicit holiday set, and explicit special/shortened session overrides
 * (DD-05 §116). Dates with an override are trading days with the overridden window even if they fall
 * on a weekend or holiday. The version is part of candle lineage, so changing session rules must
 * change it.
 */
public final class NseTradingCalendar {

    public static final ZoneId EXCHANGE_ZONE = ZoneId.of("Asia/Kolkata");
    public static final String VERSION = "nse-session-v1";

    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 15);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(15, 30);
    private static final Session NORMAL_SESSION = new Session(SESSION_OPEN, SESSION_CLOSE);

    /**
     * A non-normal session window for one date (Muhurat trading, ad-hoc shortened sessions).
     */
    public record Session(LocalTime open, LocalTime close) {
        public Session {
            if (open == null || close == null || !open.isBefore(close)) {
                throw new IllegalArgumentException("session open must be before close");
            }
        }
    }

    private final Set<LocalDate> holidays;
    private final Map<LocalDate, Session> specialSessions;

    public NseTradingCalendar(Set<LocalDate> holidays) {
        this(holidays, Map.of());
    }

    public NseTradingCalendar(Set<LocalDate> holidays, Map<LocalDate, Session> specialSessions) {
        this.holidays = Set.copyOf(holidays);
        this.specialSessions = Map.copyOf(specialSessions);
    }

    /**
     * Weekends-only calendar; suitable until a dated holiday source is wired in.
     */
    public static NseTradingCalendar weekendsOnly() {
        return new NseTradingCalendar(Set.of());
    }

    public boolean isTradingDay(LocalDate date) {
        if (specialSessions.containsKey(date)) {
            return true;
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    public LocalDate sessionDate(Instant instant) {
        return instant.atZone(EXCHANGE_ZONE).toLocalDate();
    }

    /**
     * Number of one-minute slots in a normal session (375 for 09:15–15:30).
     */
    public long sessionMinutes() {
        return Duration.between(SESSION_OPEN, SESSION_CLOSE).toMinutes();
    }

    /**
     * Number of one-minute slots in the session for a specific date, honouring special sessions.
     */
    public long sessionMinutes(LocalDate date) {
        Session session = specialSessions.getOrDefault(date, NORMAL_SESSION);
        return Duration.between(session.open(), session.close()).toMinutes();
    }

    public Instant sessionOpen(LocalDate date) {
        Session session = specialSessions.getOrDefault(date, NORMAL_SESSION);
        return date.atTime(session.open()).atZone(EXCHANGE_ZONE).toInstant();
    }

    public Instant sessionClose(LocalDate date) {
        Session session = specialSessions.getOrDefault(date, NORMAL_SESSION);
        return date.atTime(session.close()).atZone(EXCHANGE_ZONE).toInstant();
    }

    public LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Whether an open-time falls inside the canonical continuous cash session for its date:
     * a trading day and the half-open window {@code [sessionOpen, sessionClose)}. This is the single
     * definition of "session minute" used by both candle aggregation and canonical ingestion, so the
     * persisted M1 base and its derived bars cannot disagree about which minutes belong to it
     * (DD-05 §§93/99/115). Special sessions are honoured via explicit per-date overrides (§116).
     */
    public boolean isSessionMinute(Instant openTime) {
        LocalDate session = sessionDate(openTime);
        if (!isTradingDay(session)) {
            return false;
        }
        return !openTime.isBefore(sessionOpen(session)) && openTime.isBefore(sessionClose(session));
    }
}
