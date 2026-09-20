package com.edgerelative.application.history.query;

import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.history.CandleAggregator;
import com.edgerelative.application.history.HistoricalCandle;
import com.edgerelative.application.history.HistoricalCoverage;
import com.edgerelative.application.history.HistoryException;
import com.edgerelative.application.history.HistoryProperties;
import com.edgerelative.application.history.HistoryRepository;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.TimeframeCatalog;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only canonical historical data service (DD-05 §94/§97).
 *
 * <p>Depends only on the canonical store and the shared aggregator. It deliberately has no
 * reference to {@code HistoricalDataBroker}, the Groww adapter, the backfill worker or the
 * watchlist, so consumers cannot trigger broker calls through it.
 */
@Service
public class HistoricalDataQueryService implements HistoricalDataReader {

    private static final int MAX_LIMIT = 10_000;

    private final CanonicalInstrumentService canonical;
    private final HistoryRepository repository;
    private final CandleAggregator aggregator;
    private final HistoryProperties properties;

    public HistoricalDataQueryService(
            CanonicalInstrumentService canonical,
            HistoryRepository repository,
            CandleAggregator aggregator,
            HistoryProperties properties) {
        this.canonical = canonical;
        this.repository = repository;
        this.aggregator = aggregator;
        this.properties = properties;
    }

    @Override
    public List<AggregatedCandle> candles(
            long instrumentId, String timeframeCode, Instant from, Instant to, int limit) {
        TimeframeCatalog.Spec spec = requireTimeframe(timeframeCode);
        if (from == null || to == null || !from.isBefore(to)) {
            throw new HistoryException(HistoryException.INVALID, "'from' must be before 'to'");
        }
        long m1TimeframeId = canonical.ensureTimeframe(TimeframeCatalog.M1);
        int requested = Math.min(Math.max(limit, 1), MAX_LIMIT);
        if (TimeframeCatalog.M1.equals(spec.code())) {
            return aggregator.aggregate(
                    repository.candles(instrumentId, m1TimeframeId, from, to, requested), spec.code());
        }
        List<HistoricalCandle> source =
                repository.candles(instrumentId, m1TimeframeId, from, to, properties.getMaxSourceCandles());
        return aggregator.aggregate(source, spec.code()).stream().limit(requested).toList();
    }

    @Override
    public HistoricalCoverage coverage(long instrumentId, String timeframeCode) {
        TimeframeCatalog.Spec spec = requireTimeframe(timeframeCode);
        long timeframeId = canonical.ensureTimeframe(spec.code());
        return repository.coverage(instrumentId, timeframeId, spec.code());
    }

    private static TimeframeCatalog.Spec requireTimeframe(String timeframeCode) {
        return TimeframeCatalog.find(timeframeCode)
                .orElseThrow(() -> new HistoryException(
                        HistoryException.INVALID, "Unsupported timeframe: " + timeframeCode));
    }
}
