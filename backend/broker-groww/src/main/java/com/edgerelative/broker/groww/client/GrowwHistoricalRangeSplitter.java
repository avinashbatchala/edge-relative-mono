package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.model.BrokerCandleInterval;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a historical window into requests Groww will accept.
 *
 * <p>Limits are from the documented Backtesting table: 1-5m = 30 days, 10-30m = 90 days, and
 * 1h/4h/1d/1w/1m = 180 days per request.
 */
public class GrowwHistoricalRangeSplitter {

    public record TimeRange(Instant start, Instant end) {
    }

    public List<TimeRange> split(Instant start, Instant end, BrokerCandleInterval interval) {
        Duration max = maxDuration(interval);
        List<TimeRange> ranges = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            Instant next = cursor.plus(max);
            Instant chunkEnd = next.isAfter(end) ? end : next;
            ranges.add(new TimeRange(cursor, chunkEnd));
            cursor = chunkEnd;
        }
        return ranges;
    }

    static Duration maxDuration(BrokerCandleInterval interval) {
        return switch (interval) {
            case ONE_MINUTE, TWO_MINUTE, THREE_MINUTE, FIVE_MINUTE -> Duration.ofDays(30);
            case TEN_MINUTE, FIFTEEN_MINUTE, THIRTY_MINUTE -> Duration.ofDays(90);
            case ONE_HOUR, FOUR_HOUR, ONE_DAY, ONE_WEEK, ONE_MONTH -> Duration.ofDays(180);
        };
    }
}
