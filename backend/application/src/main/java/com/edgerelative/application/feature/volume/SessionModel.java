package com.edgerelative.application.feature.volume;

import com.edgerelative.application.reference.NseTradingCalendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Session-relative time model built on the canonical exchange calendar. All intraday volume
 * normalisation is defined in these terms so opening, midday and closing volume cannot be confused
 * (DD-02 §44–§46, DD-05 §153/§154).
 */
public final class SessionModel {

    private final NseTradingCalendar calendar;
    private final int timeframeMinutes;

    public SessionModel(NseTradingCalendar calendar, int timeframeMinutes) {
        if (timeframeMinutes < 1) {
            throw new IllegalArgumentException("timeframe minutes must be >= 1");
        }
        this.calendar = calendar;
        this.timeframeMinutes = timeframeMinutes;
    }

    public LocalDate sessionDate(Instant instant) {
        return calendar.sessionDate(instant);
    }

    public int minutesSinceOpen(Instant instant) {
        return (int) Duration.between(calendar.sessionOpen(sessionDate(instant)), instant).toMinutes();
    }

    public int slotIndex(Instant barOpenTime) {
        return minutesSinceOpen(barOpenTime) / timeframeMinutes;
    }

    public int sessionMinutes() {
        return (int) calendar.sessionMinutes();
    }

    /**
     * Number of bars a fully-observed session should contain, including the truncated final bar.
     * For timeframes that do not divide the 375-minute session evenly (M30, H1, H2, H4) the last bar
     * is partial, so the count is the ceiling, not the floor (DD-05 §98/§153).
     */
    public int expectedBars() {
        return (sessionMinutes() + timeframeMinutes - 1) / timeframeMinutes;
    }

    public int timeframeMinutes() {
        return timeframeMinutes;
    }
}
