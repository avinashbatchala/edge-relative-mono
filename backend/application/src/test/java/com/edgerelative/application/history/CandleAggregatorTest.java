package com.edgerelative.application.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deterministic construction of higher timeframes from the M1 base. */
class CandleAggregatorTest {

    private static final LocalDate SESSION = LocalDate.of(2026, 9, 18);

    private final NseTradingCalendar calendar = NseTradingCalendar.weekendsOnly();
    private final CandleAggregator aggregator = new CandleAggregator(calendar);

    @Test
    void m1ToM5AnchorsToSessionOpenAndAggregates() {
        List<HistoricalCandle> source = minutes(calendar.sessionOpen(SESSION), 12, new BigDecimal("100"));

        List<AggregatedCandle> bars = aggregator.aggregate(source, BrokerCandleInterval.FIVE_MINUTE);

        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(SESSION));
        assertThat(bars.get(0).open()).isEqualByComparingTo("100");
        assertThat(bars.get(0).high()).isEqualByComparingTo("104.5");
        assertThat(bars.get(0).low()).isEqualByComparingTo("99.5");
        assertThat(bars.get(0).close()).isEqualByComparingTo("104.25");
        assertThat(bars.get(0).volume()).isEqualTo(50);
        assertThat(bars.get(0).partial()).isFalse();
        assertThat(bars.get(0).definitionVersion()).isEqualTo(CandleAggregator.AGGREGATE_DEFINITION_VERSION);
        // 09:15 + 5m boundaries stay anchored to the session open.
        assertThat(bars.get(1).openTime()).isEqualTo(calendar.sessionOpen(SESSION).plusSeconds(300));
        assertThat(bars.get(2).openTime()).isEqualTo(calendar.sessionOpen(SESSION).plusSeconds(600));
        assertThat(bars.get(2).volume()).isEqualTo(20);
    }

    @Test
    void m1ToM5AcrossASecondSessionResetsToThatSessionOpen() {
        List<HistoricalCandle> source = new ArrayList<>(minutes(calendar.sessionOpen(SESSION), 5, new BigDecimal("100")));
        LocalDate next = SESSION.plusDays(3);
        source.addAll(minutes(calendar.sessionOpen(next), 5, new BigDecimal("200")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, BrokerCandleInterval.FIVE_MINUTE);

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).close()).isEqualByComparingTo("104.25");
        assertThat(bars.get(1).openTime()).isEqualTo(calendar.sessionOpen(next));
        assertThat(bars.get(1).open()).isEqualByComparingTo("200");
    }

    @Test
    void finalBarTruncatedByTheSessionIsMarkedPartial() {
        List<HistoricalCandle> source = List.of(
                minute(calendar.sessionOpen(SESSION), new BigDecimal("100")),
                minute(calendar.sessionOpen(SESSION).plusSeconds(4 * 3600 + 300), new BigDecimal("110")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, BrokerCandleInterval.FOUR_HOUR);

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).partial()).isFalse();
        assertThat(bars.get(1).partial()).isTrue();
    }

    @Test
    void d1UsesTheCanonicalSession() {
        List<HistoricalCandle> source = minutes(calendar.sessionOpen(SESSION), 10, new BigDecimal("50"));

        List<AggregatedCandle> bars = aggregator.aggregate(source, BrokerCandleInterval.ONE_DAY);

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(SESSION));
        assertThat(bars.get(0).open()).isEqualByComparingTo("50");
        assertThat(bars.get(0).close()).isEqualByComparingTo("59.25");
        assertThat(bars.get(0).volume()).isEqualTo(100);
        assertThat(bars.get(0).partial()).isFalse();
    }

    @Test
    void w1AggregatesActualSessionsWithoutAssumingFiveDays() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        List<HistoricalCandle> source = new ArrayList<>(minutes(calendar.sessionOpen(monday), 3, new BigDecimal("10")));
        source.addAll(minutes(calendar.sessionOpen(monday.plusDays(4)), 2, new BigDecimal("20")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, BrokerCandleInterval.ONE_WEEK);

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(monday));
        assertThat(bars.get(0).volume()).isEqualTo(50);
        assertThat(bars.get(0).close()).isEqualByComparingTo("21.25");
    }

    @Test
    void m1PassesThroughUnchangedWithItsDefinitionVersion() {
        List<HistoricalCandle> source = minutes(calendar.sessionOpen(SESSION), 3, new BigDecimal("7"));

        List<AggregatedCandle> bars = aggregator.aggregate(source, BrokerCandleInterval.ONE_MINUTE);

        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).definitionVersion()).isEqualTo(CandleAggregator.M1_DEFINITION_VERSION);
        assertThat(bars.get(0).open()).isEqualByComparingTo("7");
    }

    private static List<HistoricalCandle> minutes(Instant start, int count, BigDecimal base) {
        List<HistoricalCandle> candles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            candles.add(minute(start.plusSeconds(i * 60L), base.add(BigDecimal.valueOf(i))));
        }
        return candles;
    }

    private static HistoricalCandle minute(Instant openTime, BigDecimal base) {
        return new HistoricalCandle(
                openTime,
                base,
                base.add(new BigDecimal("0.5")),
                base.subtract(new BigDecimal("0.5")),
                base.add(new BigDecimal("0.25")),
                10,
                null);
    }
}
