package com.edgerelative.broker.groww.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.api.model.BrokerCandleInterval;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class GrowwHistoricalRangeSplitterTest {

    private final GrowwHistoricalRangeSplitter splitter = new GrowwHistoricalRangeSplitter();
    private final Instant start = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void usesDocumentedMaxDurationsPerInterval() {
        assertThat(GrowwHistoricalRangeSplitter.maxDuration(BrokerCandleInterval.FIVE_MINUTE))
                .isEqualTo(Duration.ofDays(30));
        assertThat(GrowwHistoricalRangeSplitter.maxDuration(BrokerCandleInterval.FIFTEEN_MINUTE))
                .isEqualTo(Duration.ofDays(90));
        assertThat(GrowwHistoricalRangeSplitter.maxDuration(BrokerCandleInterval.ONE_HOUR))
                .isEqualTo(Duration.ofDays(180));
        assertThat(GrowwHistoricalRangeSplitter.maxDuration(BrokerCandleInterval.ONE_DAY))
                .isEqualTo(Duration.ofDays(180));
    }

    @Test
    void splitsLongRangesIntoContiguousChunks() {
        List<GrowwHistoricalRangeSplitter.TimeRange> ranges =
                splitter.split(start, start.plus(Duration.ofDays(75)), BrokerCandleInterval.FIVE_MINUTE);

        assertThat(ranges).hasSize(3);
        assertThat(ranges.get(0).start()).isEqualTo(start);
        assertThat(ranges.get(0).end()).isEqualTo(start.plus(Duration.ofDays(30)));
        assertThat(ranges.get(1).start()).isEqualTo(ranges.get(0).end());
        assertThat(ranges.get(2).end()).isEqualTo(start.plus(Duration.ofDays(75)));
    }

    @Test
    void shortRangeProducesSingleChunk() {
        List<GrowwHistoricalRangeSplitter.TimeRange> ranges =
                splitter.split(start, start.plus(Duration.ofDays(2)), BrokerCandleInterval.ONE_MINUTE);
        assertThat(ranges).hasSize(1);
    }
}
