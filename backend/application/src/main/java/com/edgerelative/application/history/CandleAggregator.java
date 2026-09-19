package com.edgerelative.application.history;

import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic construction of higher timeframes from the canonical M1 base.
 *
 * <p>Intraday bars are anchored to the exchange session open, so a session that is not an integer
 * number of bars ends in an explicitly {@code partial} bar rather than a stretched one. D1 uses the
 * canonical session, and W1/MN1 aggregate the actual sessions present. The same code must be reused
 * by live, replay and backtest consumers.
 */
public final class CandleAggregator {

    public static final String M1_DEFINITION_VERSION = "er-m1-base-v1";
    public static final String AGGREGATE_DEFINITION_VERSION = "er-aggregate-v1";

    private final NseTradingCalendar calendar;

    public CandleAggregator(NseTradingCalendar calendar) {
        this.calendar = calendar;
    }

    public List<AggregatedCandle> aggregate(List<HistoricalCandle> source, BrokerCandleInterval target) {
        if (target == BrokerCandleInterval.ONE_MINUTE) {
            return source.stream()
                    .map(candle -> new AggregatedCandle(
                            candle.openTime(),
                            candle.open(),
                            candle.high(),
                            candle.low(),
                            candle.close(),
                            candle.volume(),
                            candle.openInterest(),
                            false,
                            M1_DEFINITION_VERSION))
                    .toList();
        }
        List<AggregatedCandle> result = new ArrayList<>();
        Accumulator current = null;
        Object currentKey = null;
        for (HistoricalCandle candle : source) {
            Bucket bucket = bucket(candle, target);
            if (current == null || !bucket.key().equals(currentKey)) {
                if (current != null) {
                    result.add(current.toCandle());
                }
                current = new Accumulator(bucket.start(), bucket.partial());
                currentKey = bucket.key();
            }
            current.add(candle);
        }
        if (current != null) {
            result.add(current.toCandle());
        }
        return result;
    }

    private Bucket bucket(HistoricalCandle candle, BrokerCandleInterval target) {
        LocalDate session = calendar.sessionDate(candle.openTime());
        return switch (target) {
            case ONE_WEEK -> new Bucket(calendar.weekStart(session), candle.openTime(), false);
            case ONE_MONTH -> new Bucket(YearMonth.from(session), candle.openTime(), false);
            case ONE_DAY -> new Bucket(session, calendar.sessionOpen(session), false);
            default -> intraday(candle, session, target);
        };
    }

    private Bucket intraday(HistoricalCandle candle, LocalDate session, BrokerCandleInterval target) {
        Duration duration = CanonicalInstrumentService.barDuration(target);
        if (duration == null) {
            throw new IllegalArgumentException("Not an intraday timeframe: " + target);
        }
        Instant open = calendar.sessionOpen(session);
        Instant close = calendar.sessionClose(session);
        long seconds = duration.getSeconds();
        long elapsed = Duration.between(open, candle.openTime()).getSeconds();
        long index = elapsed <= 0 ? 0 : elapsed / seconds;
        Instant start = open.plusSeconds(index * seconds);
        boolean partial = start.plusSeconds(seconds).isAfter(close);
        return new Bucket(start, start, partial);
    }

    private record Bucket(Object key, Instant start, boolean partial) {
    }

    private static final class Accumulator {

        private final Instant openTime;
        private final boolean partial;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private long volume;
        private BigDecimal openInterest;
        private boolean seeded;

        Accumulator(Instant openTime, boolean partial) {
            this.openTime = openTime;
            this.partial = partial;
        }

        void add(HistoricalCandle candle) {
            if (!seeded) {
                open = candle.open();
                high = candle.high();
                low = candle.low();
                seeded = true;
            } else {
                high = max(high, candle.high());
                low = min(low, candle.low());
            }
            close = candle.close();
            volume += candle.volume();
            if (candle.openInterest() != null) {
                openInterest = candle.openInterest();
            }
        }

        AggregatedCandle toCandle() {
            return new AggregatedCandle(
                    openTime, open, high, low, close, volume, openInterest, partial, AGGREGATE_DEFINITION_VERSION);
        }

        private static BigDecimal max(BigDecimal left, BigDecimal right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.max(right);
        }

        private static BigDecimal min(BigDecimal left, BigDecimal right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.min(right);
        }
    }
}
