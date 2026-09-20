package com.edgerelative.application.history;

import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.reference.TimeframeCatalog;
import com.edgerelative.application.reference.TimeframeCatalog.Spec;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic construction of higher timeframes from the canonical M1 base.
 *
 * <p>Intraday bars are anchored to the exchange session open, so a session that is not an integer
 * number of bars ends in an explicitly {@code partial} bar rather than a stretched one. D1 uses the
 * canonical session and W1 aggregates the actual sessions present. A bucket whose required minutes
 * are not fully present is marked {@code INCOMPLETE} rather than silently treated as a confirmed
 * close (DD-05 §93/§97/§103). The same code must be reused by live, replay and backtest consumers.
 */
public final class CandleAggregator {

    public static final String M1_DEFINITION_VERSION = "er-m1-base-v1";
    public static final String AGGREGATE_DEFINITION_VERSION = "er-aggregate-v1";
    public static final String QUALITY_GOOD = "GOOD";
    public static final String QUALITY_INCOMPLETE = "INCOMPLETE";
    /**
     * A fully-covered interval whose source bars all carry zero volume: a real no-trade interval,
     * which must not be encoded the same as a missing feed interval (DD-05 §§103/104).
     */
    public static final String QUALITY_NO_TRADES = "NO_TRADES";

    private final NseTradingCalendar calendar;

    public CandleAggregator(NseTradingCalendar calendar) {
        this.calendar = calendar;
    }

    public List<AggregatedCandle> aggregate(List<HistoricalCandle> source, String timeframeCode) {
        Spec spec = TimeframeCatalog.require(timeframeCode);
        if (TimeframeCatalog.M1.equals(spec.code())) {
            return source.stream().filter(this::inSession).map(CandleAggregator::passthrough).toList();
        }
        List<AggregatedCandle> result = new ArrayList<>();
        Accumulator current = null;
        Object currentKey = null;
        for (HistoricalCandle candle : source) {
            if (!inSession(candle)) {
                continue;
            }
            Bucket bucket = bucket(candle, spec);
            if (current == null || !bucket.key().equals(currentKey)) {
                if (current != null) {
                    result.add(current.toCandle(spec));
                }
                current = new Accumulator(bucket.start(), bucket.startDate(), bucket.endDate(), bucket.partial());
                currentKey = bucket.key();
            }
            current.add(candle);
        }
        if (current != null) {
            result.add(current.toCandle(spec));
        }
        return result;
    }

    /**
     * Canonical NSE session membership (DD-05 §§93/99/115): a minute at or after the session close
     * (vendor post-close data), before the open, or on a non-trading day is not part of the session
     * bar. Without this a vendor minute after 15:30 IST creates an extra bucket, so every derived
     * session looks incomplete and volume baselines cannot form. The rule is shared with the
     * ingestion boundary via {@link NseTradingCalendar#isSessionMinute(Instant)}.
     */
    private boolean inSession(HistoricalCandle candle) {
        return calendar.isSessionMinute(candle.openTime());
    }

    private static AggregatedCandle passthrough(HistoricalCandle candle) {
        return new AggregatedCandle(
                candle.openTime(),
                candle.closeTime(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.openInterest(),
                candle.tradeCount(),
                candle.vwap(),
                false,
                candle.complete(),
                candle.qualityState(),
                M1_DEFINITION_VERSION);
    }

    private Bucket bucket(HistoricalCandle candle, Spec spec) {
        LocalDate session = calendar.sessionDate(candle.openTime());
        if (spec.calendarBased() && spec.code().equals("W1")) {
            LocalDate weekStart = calendar.weekStart(session);
            // Session-anchored like D1 (DD-05 §§93/99/100): the week opens at the canonical open of
            // its first contributing session, not at whatever minute happened to arrive first.
            return new Bucket(weekStart, calendar.sessionOpen(session), weekStart, weekStart.plusDays(6), false);
        }
        if (spec.calendarBased()) {
            return new Bucket(session, calendar.sessionOpen(session), session, session, false);
        }
        Duration duration = spec.duration();
        Instant open = calendar.sessionOpen(session);
        Instant close = calendar.sessionClose(session);
        long seconds = duration.getSeconds();
        long elapsed = Duration.between(open, candle.openTime()).getSeconds();
        long index = elapsed <= 0 ? 0 : elapsed / seconds;
        Instant start = open.plusSeconds(index * seconds);
        boolean partial = start.plusSeconds(seconds).isAfter(close);
        return new Bucket(start, start, session, session, partial);
    }

    private record Bucket(Object key, Instant start, LocalDate startDate, LocalDate endDate, boolean partial) {
    }

    private final class Accumulator {

        private final Instant openTime;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final boolean partial;
        private final Map<LocalDate, Integer> receivedBySession = new HashMap<>();
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private Instant closeTime;
        private Instant lastOpenTime;
        private long volume;
        private BigDecimal openInterest;
        private boolean seeded;

        Accumulator(Instant openTime, LocalDate startDate, LocalDate endDate, boolean partial) {
            this.openTime = openTime;
            this.startDate = startDate;
            this.endDate = endDate;
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
            closeTime = candle.closeTime();
            lastOpenTime = candle.openTime();
            volume += candle.volume();
            if (candle.openInterest() != null) {
                openInterest = candle.openInterest();
            }
            LocalDate session = calendar.sessionDate(candle.openTime());
            receivedBySession.merge(session, 1, Integer::sum);
        }

        AggregatedCandle toCandle(Spec spec) {
            String quality = completeness(spec);
            Instant effectiveClose = effectiveClose(spec);
            return new AggregatedCandle(
                    openTime,
                    effectiveClose,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    openInterest,
                    null,
                    null,
                    partial,
                    true,
                    quality,
                    AGGREGATE_DEFINITION_VERSION);
        }

        private Instant effectiveClose(Spec spec) {
            if (!spec.calendarBased()) {
                Instant sessionClose = calendar.sessionClose(startDate);
                Instant nominalEnd = openTime.plusSeconds(spec.duration().getSeconds());
                return nominalEnd.isAfter(sessionClose) ? sessionClose : nominalEnd;
            }
            if (spec.code().equals("D1")) {
                return calendar.sessionClose(startDate);
            }
            // W1 closes at the canonical close of its last contributing session (DD-05 §§93/100),
            // not at the last minute that happened to arrive.
            LocalDate lastSession = lastOpenTime == null ? startDate : calendar.sessionDate(lastOpenTime);
            return calendar.sessionClose(lastSession);
        }

        private String completeness(Spec spec) {
            if (!spec.calendarBased()) {
                Instant sessionClose = calendar.sessionClose(startDate);
                Instant nominalEnd = openTime.plusSeconds(spec.duration().getSeconds());
                Instant bucketEnd = nominalEnd.isAfter(sessionClose) ? sessionClose : nominalEnd;
                long expectedMinutes = Duration.between(openTime, bucketEnd).toMinutes();
                return quality(receivedBySession.getOrDefault(startDate, 0) >= expectedMinutes);
            }
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                if (!calendar.isTradingDay(date)) {
                    continue;
                }
                if (receivedBySession.getOrDefault(date, 0) < calendar.sessionMinutes(date)) {
                    return QUALITY_INCOMPLETE;
                }
            }
            return quality(true);
        }

        /**
         * Distinguishes a fully-covered interval with no trades from a missing feed interval
         * (DD-05 §§103/104): missing minutes are {@code INCOMPLETE}; a covered zero-volume interval is
         * {@code NO_TRADES}; otherwise {@code GOOD}.
         */
        private String quality(boolean covered) {
            if (!covered) {
                return QUALITY_INCOMPLETE;
            }
            return volume == 0 ? QUALITY_NO_TRADES : QUALITY_GOOD;
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
