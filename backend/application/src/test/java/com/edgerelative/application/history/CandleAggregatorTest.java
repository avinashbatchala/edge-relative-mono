package com.edgerelative.application.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.reference.NseTradingCalendar;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Deterministic construction of higher timeframes from the M1 base.
 */
class CandleAggregatorTest {

    private static final LocalDate SESSION = LocalDate.of(2026, 9, 18);

    private final NseTradingCalendar calendar = NseTradingCalendar.weekendsOnly();
    private final CandleAggregator aggregator = new CandleAggregator(calendar);

    @Test
    void m1ToM5AnchorsToSessionOpenAndFlagsIncompleteBuckets() {
        List<HistoricalCandle> source = minutes(calendar.sessionOpen(SESSION), 12, new BigDecimal("100"));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "M5");

        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(SESSION));
        assertThat(bars.get(0).closeTime()).isEqualTo(calendar.sessionOpen(SESSION).plusSeconds(300));
        assertThat(bars.get(0).open()).isEqualByComparingTo("100");
        assertThat(bars.get(0).high()).isEqualByComparingTo("104.5");
        assertThat(bars.get(0).low()).isEqualByComparingTo("99.5");
        assertThat(bars.get(0).close()).isEqualByComparingTo("104.25");
        assertThat(bars.get(0).volume()).isEqualTo(50);
        assertThat(bars.get(0).partial()).isFalse();
        assertThat(bars.get(0).complete()).isTrue();
        assertThat(bars.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_GOOD);
        assertThat(bars.get(0).definitionVersion()).isEqualTo(CandleAggregator.AGGREGATE_DEFINITION_VERSION);
        assertThat(bars.get(1).openTime()).isEqualTo(calendar.sessionOpen(SESSION).plusSeconds(300));
        // Only two of the five required minutes are present.
        assertThat(bars.get(2).qualityState()).isEqualTo(CandleAggregator.QUALITY_INCOMPLETE);
    }

    @Test
    void eachSessionResetsToItsOwnOpen() {
        List<HistoricalCandle> source = new ArrayList<>(minutes(calendar.sessionOpen(SESSION), 5, new BigDecimal("100")));
        LocalDate next = SESSION.plusDays(3);
        source.addAll(minutes(calendar.sessionOpen(next), 5, new BigDecimal("200")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "M5");

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).close()).isEqualByComparingTo("104.25");
        assertThat(bars.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_GOOD);
        assertThat(bars.get(1).openTime()).isEqualTo(calendar.sessionOpen(next));
        assertThat(bars.get(1).open()).isEqualByComparingTo("200");
    }

    @Test
    void finalBarTruncatedByTheSessionIsMarkedPartial() {
        List<HistoricalCandle> source = List.of(
                minute(calendar.sessionOpen(SESSION), new BigDecimal("100")),
                minute(calendar.sessionOpen(SESSION).plusSeconds(6 * 3600 + 60), new BigDecimal("110")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "H2");

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).partial()).isFalse();
        assertThat(bars.get(1).partial()).isTrue();
        assertThat(bars.get(1).closeTime()).isEqualTo(calendar.sessionClose(SESSION));
    }

    @Test
    void d1UsesTheCanonicalSessionAndFlagsMissingMinutes() {
        List<HistoricalCandle> source = minutes(calendar.sessionOpen(SESSION), 10, new BigDecimal("50"));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "D1");

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(SESSION));
        assertThat(bars.get(0).closeTime()).isEqualTo(calendar.sessionClose(SESSION));
        assertThat(bars.get(0).open()).isEqualByComparingTo("50");
        assertThat(bars.get(0).close()).isEqualByComparingTo("59.25");
        assertThat(bars.get(0).volume()).isEqualTo(100);
        assertThat(bars.get(0).partial()).isFalse();
        assertThat(bars.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_INCOMPLETE);
    }

    @Test
    void w1AggregatesActualSessionsWithoutAssumingFiveDays() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        List<HistoricalCandle> source = new ArrayList<>(minutes(calendar.sessionOpen(monday), 3, new BigDecimal("10")));
        source.addAll(minutes(calendar.sessionOpen(monday.plusDays(4)), 2, new BigDecimal("20")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "W1");

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(monday));
        assertThat(bars.get(0).volume()).isEqualTo(50);
        assertThat(bars.get(0).close()).isEqualByComparingTo("21.25");
        assertThat(bars.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_INCOMPLETE);
    }

    @Test
    void m1PassesThroughUnchangedWithItsDefinitionVersion() {
        List<HistoricalCandle> source = minutes(calendar.sessionOpen(SESSION), 3, new BigDecimal("7"));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "M1");

        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).definitionVersion()).isEqualTo(CandleAggregator.M1_DEFINITION_VERSION);
        assertThat(bars.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_GOOD);
        assertThat(bars.get(0).open()).isEqualByComparingTo("7");
    }

    @Test
    void postCloseVendorMinutesAreExcludedSoSessionsStayComplete() {
        List<HistoricalCandle> source = new ArrayList<>();
        source.addAll(minutes(calendar.sessionOpen(SESSION), 375, new BigDecimal("100")));
        source.addAll(minutes(calendar.sessionClose(SESSION), 30, new BigDecimal("999")));

        List<AggregatedCandle> m5 = aggregator.aggregate(source, "M5");

        assertThat(m5).hasSize(75);
        AggregatedCandle last = m5.get(74);
        assertThat(last.openTime()).isEqualTo(calendar.sessionClose(SESSION).minusSeconds(300));
        assertThat(last.closeTime()).isEqualTo(calendar.sessionClose(SESSION));
        assertThat(last.complete()).isTrue();
        assertThat(last.qualityState()).isEqualTo(CandleAggregator.QUALITY_GOOD);
        assertThat(last.volume()).isEqualTo(50);
        // M1 passthrough must also stop at the session close.
        assertThat(aggregator.aggregate(source, "M1")).hasSize(375);
    }

    @Test
    void w1AnchorsToCanonicalSessionBoundariesNotToTheFirstOrLastMinute() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        LocalDate friday = monday.plusDays(4);
        List<HistoricalCandle> source = new ArrayList<>();
        // Monday's first minute is missing: the weekly open must still be the session open.
        source.addAll(minutes(calendar.sessionOpen(monday).plusSeconds(60), 3, new BigDecimal("10")));
        // Friday's last minute is missing: the weekly close must still be the session close.
        source.addAll(minutes(calendar.sessionOpen(friday), 374, new BigDecimal("20")));

        List<AggregatedCandle> bars = aggregator.aggregate(source, "W1");

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).openTime()).isEqualTo(calendar.sessionOpen(monday));
        assertThat(bars.get(0).closeTime()).isEqualTo(calendar.sessionClose(friday));
    }

    @Test
    void fullyCoveredZeroVolumeIntervalIsNoTradesNotMissingData() {
        List<HistoricalCandle> source = new ArrayList<>();
        Instant start = calendar.sessionOpen(SESSION);
        for (int i = 0; i < 5; i++) {
            Instant open = start.plusSeconds(i * 60L);
            source.add(new HistoricalCandle(
                    open,
                    open.plusSeconds(60),
                    new BigDecimal("100"),
                    new BigDecimal("100"),
                    new BigDecimal("100"),
                    new BigDecimal("100"),
                    0,
                    null,
                    null,
                    null,
                    true,
                    CandleAggregator.QUALITY_GOOD));
        }

        List<AggregatedCandle> bars = aggregator.aggregate(source, "M5");

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).volume()).isZero();
        assertThat(bars.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_NO_TRADES);
    }

    @Test
    void specialSessionShiftsBucketBoundariesAndD1Close() {
        LocalDate muhurat = LocalDate.of(2026, 11, 8); // Sunday special session 18:00-19:00 IST
        NseTradingCalendar special = new NseTradingCalendar(
                java.util.Set.of(),
                java.util.Map.of(
                        muhurat, new NseTradingCalendar.Session(java.time.LocalTime.of(18, 0), java.time.LocalTime.of(19, 0))));
        CandleAggregator specialAggregator = new CandleAggregator(special);
        List<HistoricalCandle> source =
                minutes(special.sessionOpen(muhurat), 60, new BigDecimal("100"));

        List<AggregatedCandle> m30 = specialAggregator.aggregate(source, "M30");
        assertThat(m30).hasSize(2);
        assertThat(m30.get(0).openTime()).isEqualTo(special.sessionOpen(muhurat));
        assertThat(m30.get(1).closeTime()).isEqualTo(special.sessionClose(muhurat));
        assertThat(m30.get(0).qualityState()).isEqualTo(CandleAggregator.QUALITY_GOOD);

        List<AggregatedCandle> d1 = specialAggregator.aggregate(source, "D1");
        assertThat(d1).hasSize(1);
        assertThat(d1.get(0).openTime()).isEqualTo(special.sessionOpen(muhurat));
        assertThat(d1.get(0).closeTime()).isEqualTo(special.sessionClose(muhurat));
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
                openTime.plusSeconds(60),
                base,
                base.add(new BigDecimal("0.5")),
                base.subtract(new BigDecimal("0.5")),
                base.add(new BigDecimal("0.25")),
                10,
                null,
                null,
                null,
                true,
                CandleAggregator.QUALITY_GOOD);
    }
}
