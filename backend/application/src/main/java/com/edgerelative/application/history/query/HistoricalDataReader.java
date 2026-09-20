package com.edgerelative.application.history.query;

import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.history.HistoricalCoverage;
import java.time.Instant;
import java.util.List;

/**
 * The single broker-neutral boundary for reading canonical historical data.
 *
 * <p>Charts, features, backtests, replay and research depend on this port, not on any broker. The
 * implementation reads only the canonical store and derives higher timeframes with the shared
 * {@code CandleAggregator}; it has no path to {@code HistoricalDataBroker} or the Groww adapter.
 */
public interface HistoricalDataReader {

    /** Canonical candles for a timeframe; M1 is read, higher timeframes are derived. */
    List<AggregatedCandle> candles(
            long instrumentId, String timeframeCode, Instant from, Instant to, int limit);

    HistoricalCoverage coverage(long instrumentId, String timeframeCode);
}
