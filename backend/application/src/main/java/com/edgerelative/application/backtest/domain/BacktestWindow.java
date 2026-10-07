package com.edgerelative.application.backtest.domain;

import com.edgerelative.application.reference.NseTradingCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Canonical run window arithmetic. Warm-up is expressed in trading sessions (calendar-aware), not
 * wall-clock days, so a fixed session count yields the same warm-up regardless of weekends/holidays.
 */
public final class BacktestWindow {

    private BacktestWindow() {
    }

    /** First instant loaded for feature warm-up: {@code warmupSessions} sessions before the start. */
    public static Instant warmupStart(LocalDate startDate, int warmupSessions, NseTradingCalendar calendar) {
        LocalDate cursor = startDate;
        int counted = 0;
        while (counted < Math.max(0, warmupSessions)) {
            cursor = cursor.minusDays(1);
            if (calendar.isTradingDay(cursor)) {
                counted++;
            }
        }
        return cursor.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public static Instant start(BacktestSpec spec) {
        return spec.startDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public static Instant endExclusive(BacktestSpec spec) {
        return spec.endDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
