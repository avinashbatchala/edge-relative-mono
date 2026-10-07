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

    /**
     * Canonical candles for a timeframe; M1 is read, higher timeframes are derived.
     *
     * <p>Clamped to the interactive request cap so a single chart/feature request cannot read an
     * unbounded range.
     */
    List<AggregatedCandle> candles(
            long instrumentId, String timeframeCode, Instant from, Instant to, int limit);

    /**
     * Canonical candles for a controlled server-side replay/batch caller (backtest, dataset
     * manifest). Not clamped to the interactive cap; bounded by {@code history.replay} limits and
     * still returns the most-recent {@code maxBars} with the same exact-timestamp semantics.
     */
    List<AggregatedCandle> replayCandles(
            long instrumentId, String timeframeCode, Instant from, Instant to, int maxBars);

    HistoricalCoverage coverage(long instrumentId, String timeframeCode);
}
