package com.edgerelative.application.history;

import com.edgerelative.application.history.api.CoverageResponse;
import com.edgerelative.application.history.api.HistoryCandleResponse;
import com.edgerelative.application.history.query.HistoricalDataReader;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral canonical historical data API (charts, features, backtests, research).
 *
 * <p>Depends only on the read port; it has no path to a broker. This is the stable boundary a future
 * Python research service can consume (Python must not read PostgreSQL directly).
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryQueryController {

    private final HistoricalDataReader reader;

    public HistoryQueryController(HistoricalDataReader reader) {
        this.reader = reader;
    }

    /**
     * Canonical candles for a timeframe; M1 is read, higher timeframes are derived.
     */
    @GetMapping("/candles")
    public List<HistoryCandleResponse> candles(
            @RequestParam long instrumentId,
            @RequestParam String timeframe,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "5000") int limit) {
        return reader.candles(instrumentId, timeframe, from, to, limit).stream()
                .map(HistoryQueryController::toResponse)
                .toList();
    }

    @GetMapping("/coverage")
    public CoverageResponse coverage(
            @RequestParam long instrumentId, @RequestParam String timeframe) {
        return toResponse(reader.coverage(instrumentId, timeframe));
    }

    private static HistoryCandleResponse toResponse(AggregatedCandle candle) {
        return new HistoryCandleResponse(
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
                candle.partial(),
                candle.complete(),
                candle.qualityState(),
                candle.definitionVersion());
    }

    private static CoverageResponse toResponse(HistoricalCoverage coverage) {
        return new CoverageResponse(
                coverage.instrumentId(),
                coverage.timeframe(),
                coverage.earliest(),
                coverage.latest(),
                coverage.candleCount(),
                coverage.completedChunks(),
                coverage.pendingChunks(),
                coverage.failedChunks(),
                coverage.lastSyncedAt(),
                coverage.status());
    }
}
