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
import java.util.ArrayList;
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
        long m1TimeframeId = timeframeId(TimeframeCatalog.M1);
        int requested = Math.min(Math.max(limit, 1), MAX_LIMIT);
        if (TimeframeCatalog.M1.equals(spec.code())) {
            List<AggregatedCandle> bars = aggregator.aggregate(
                    repository.candles(instrumentId, m1TimeframeId, from, to, requested), spec.code());
            return markInProgressIncomplete(bars, to);
        }
        List<HistoricalCandle> source =
                repository.candles(instrumentId, m1TimeframeId, from, to, properties.getMaxSourceCandles());
        List<AggregatedCandle> derived = markInProgressIncomplete(aggregator.aggregate(source, spec.code()), to);
        // Keep the most recent `requested` bars; taking the first N would drop the window ending at
        // `to` and silently move every anchor backwards (DD-05 §128/§151).
        if (derived.size() <= requested) {
            return derived;
        }
        return List.copyOf(derived.subList(derived.size() - requested, derived.size()));
    }

    /**
     * A bar whose close boundary is after the requested end is still in progress at that decision
     * time and must not be treated as a confirmed close (DD-05 §§101/102, DD-02 §17). Confirmed-close
     * consumers therefore see {@code complete=false} rather than a finalized-looking bar.
     */
    private static List<AggregatedCandle> markInProgressIncomplete(List<AggregatedCandle> bars, Instant to) {
        List<AggregatedCandle> result = new ArrayList<>(bars.size());
        for (AggregatedCandle bar : bars) {
            if (bar.complete() && bar.closeTime() != null && bar.closeTime().isAfter(to)) {
                result.add(new AggregatedCandle(
                        bar.openTime(),
                        bar.closeTime(),
                        bar.open(),
                        bar.high(),
                        bar.low(),
                        bar.close(),
                        bar.volume(),
                        bar.openInterest(),
                        bar.tradeCount(),
                        bar.vwap(),
                        bar.partial(),
                        false,
                        bar.qualityState(),
                        bar.definitionVersion()));
            } else {
                result.add(bar);
            }
        }
        return result;
    }

    @Override
    public HistoricalCoverage coverage(long instrumentId, String timeframeCode) {
        TimeframeCatalog.Spec spec = requireTimeframe(timeframeCode);
        return repository.coverage(instrumentId, timeframeId(spec.code()), spec.code());
    }

    /** Read-only: the registry is seeded by migrations, so the read path never mutates reference data. */
    private long timeframeId(String code) {
        return canonical.findTimeframeId(code)
                .orElseThrow(() -> new HistoryException(
                        HistoryException.NOT_FOUND, "Timeframe not registered: " + code));
    }

    private static TimeframeCatalog.Spec requireTimeframe(String timeframeCode) {
        return TimeframeCatalog.find(timeframeCode)
                .orElseThrow(() -> new HistoryException(
                        HistoryException.INVALID, "Unsupported timeframe: " + timeframeCode));
    }
}
