package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.domain.BacktestWindow;
import com.edgerelative.application.reference.NseTradingCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BacktestWindowTest {

    @Test
    void warmupCountsTradingSessionsSkippingWeekends() {
        // Start Monday 2026-09-14; five sessions back is Monday 2026-09-07 (weekend skipped).
        Instant from = BacktestWindow.warmupStart(
                LocalDate.of(2026, 9, 14), 5, NseTradingCalendar.weekendsOnly());
        assertThat(from).isEqualTo(LocalDate.of(2026, 9, 7).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void zeroWarmupStartsAtTheRequestedStartDate() {
        Instant from = BacktestWindow.warmupStart(
                LocalDate.of(2026, 9, 14), 0, NseTradingCalendar.weekendsOnly());
        assertThat(from).isEqualTo(LocalDate.of(2026, 9, 14).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
