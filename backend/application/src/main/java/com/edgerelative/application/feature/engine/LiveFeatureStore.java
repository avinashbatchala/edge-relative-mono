package com.edgerelative.application.feature.engine;

import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded in-memory live state for the active watchlist (DD-05 §125/§144, DD-04 concurrency rules).
 *
 * <p>One buffer per instrument/timeframe. A buffer is a single-writer state owner: each accepts only
 * a strictly increasing bar close time, so a duplicate or out-of-order event is ignored rather than
 * double-advancing a rolling window. There is no global lock, no virtual thread fan-out, and no
 * database round-trip on the hot path; the same pure {@link FeatureEngine} that replay uses is called
 * over the bounded buffer.
 */
public final class LiveFeatureStore {

    private final FeatureEngine engine;
    private final int maxBars;
    private final Map<SeriesKey, SeriesBuffer> buffers = new ConcurrentHashMap<>();

    public LiveFeatureStore(FeatureEngine engine, int maxBars) {
        if (maxBars < 1) {
            throw new IllegalArgumentException("maxBars must be >= 1");
        }
        this.engine = engine;
        this.maxBars = maxBars;
    }

    /**
     * Preloads warm-up history before live events begin.
     */
    public void seed(long instrumentId, String timeframe, List<AggregatedCandle> history) {
        SeriesBuffer buffer = buffer(instrumentId, timeframe);
        for (AggregatedCandle candle : history) {
            buffer.seed(candle);
        }
    }

    /**
     * @return a snapshot after the event, or the unchanged last snapshot when the event is stale/duplicate.
     */
    public FeatureSnapshot accept(FeatureContextTemplate template, AggregatedCandle subjectBar) {
        buffer(template.subjectInstrumentId(), template.timeframe()).accept(subjectBar);
        return snapshot(template);
    }

    public FeatureSnapshot snapshot(FeatureContextTemplate template) {
        List<AggregatedCandle> subject = history(template.subjectInstrumentId(), template.timeframe());
        List<AggregatedCandle> market = template.marketInstrumentId() == null
                ? List.of()
                : history(template.marketInstrumentId(), template.timeframe());
        List<AggregatedCandle> sector = template.sectorInstrumentId() == null
                ? List.of()
                : history(template.sectorInstrumentId(), template.timeframe());
        return engine.snapshot(new FeatureContext(
                template.subjectInstrumentId(),
                template.timeframe(),
                subject,
                market,
                sector,
                template.benchmark(),
                template.policy(),
                template.versions(),
                template.calendar(),
                template.marketCode(),
                template.sectorCode()));
    }

    public List<AggregatedCandle> history(long instrumentId, String timeframe) {
        return buffer(instrumentId, timeframe).snapshot();
    }

    /**
     * Visible for tests: stale/duplicate events rejected by a series.
     */
    public long rejectedEvents(long instrumentId, String timeframe) {
        return buffer(instrumentId, timeframe).rejected();
    }

    public int seriesCount() {
        return buffers.size();
    }

    private SeriesBuffer buffer(long instrumentId, String timeframe) {
        return buffers.computeIfAbsent(new SeriesKey(instrumentId, timeframe), key -> new SeriesBuffer(maxBars));
    }

    public record SeriesKey(long instrumentId, String timeframe) {
    }

    private static final class SeriesBuffer {

        private final int maxBars;
        private final Deque<AggregatedCandle> candles = new ArrayDeque<>();
        private Instant lastClose = Instant.MIN;
        private long rejected;

        SeriesBuffer(int maxBars) {
            this.maxBars = maxBars;
        }

        synchronized void accept(AggregatedCandle candle) {
            if (!candle.closeTime().isAfter(lastClose)) {
                rejected++;
                return;
            }
            candles.addLast(candle);
            lastClose = candle.closeTime();
            trim();
        }

        synchronized void seed(AggregatedCandle candle) {
            if (candles.isEmpty()) {
                candles.addLast(candle);
                lastClose = candle.closeTime();
                return;
            }
            AggregatedCandle last = candles.peekLast();
            if (candle.closeTime().isAfter(last.closeTime())) {
                candles.addLast(candle);
                lastClose = candle.closeTime();
                trim();
            }
        }

        synchronized List<AggregatedCandle> snapshot() {
            return new ArrayList<>(candles);
        }

        synchronized long rejected() {
            return rejected;
        }

        private void trim() {
            while (candles.size() > maxBars) {
                candles.removeFirst();
            }
        }
    }
}
