package com.edgerelative.application.history;

import com.edgerelative.application.corporateaction.application.CorporateActionAdjustmentService;
import com.edgerelative.application.history.api.CoverageResponse;
import com.edgerelative.application.history.api.HistoryCandleResponse;
import com.edgerelative.application.history.query.HistoricalDataReader;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

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
    private final CorporateActionAdjustmentService adjustments;

    public HistoryQueryController(HistoricalDataReader reader, CorporateActionAdjustmentService adjustments) {
        this.reader = reader;
        this.adjustments = adjustments;
    }

    /**
     * Canonical candles for a timeframe; M1 is read, higher timeframes are derived.
     *
     * <p>{@code adjustment=NONE} (default) returns raw tradable prices (DD-05 §112).
     * {@code adjustment=SPLIT_BONUS} returns the versioned back-adjusted analytical series, using
     * only corporate-action factors known at {@code asOf} (default now). Unsupported actions in the
     * window fail closed with {@code CORPORATE_ACTION_ADJUSTMENT_UNSUPPORTED}.
     */
    @GetMapping("/candles")
    public List<HistoryCandleResponse> candles(
            @RequestParam long instrumentId,
            @RequestParam String timeframe,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "5000") int limit,
            @RequestParam(defaultValue = "NONE") String adjustment,
            @RequestParam(required = false) Instant asOf) {
        String mode = adjustment.trim().toUpperCase(Locale.ROOT);
        if ("SPLIT_BONUS".equals(mode) || "ADJUSTED".equals(mode)) {
            return adjustments.adjustedCandles(instrumentId, timeframe, from, to, limit, asOf).stream()
                    .map(adjusted -> toResponse(adjusted.candle(), adjusted.cumulativePriceFactor()))
                    .toList();
        }
        if (!"NONE".equals(mode)) {
            throw new HistoryException(HistoryException.INVALID, "Unsupported adjustment: " + adjustment);
        }
        return reader.candles(instrumentId, timeframe, from, to, limit).stream()
                .map(candle -> toResponse(candle, null))
                .toList();
    }

    @GetMapping("/coverage")
    public CoverageResponse coverage(
            @RequestParam long instrumentId, @RequestParam String timeframe) {
        return toResponse(reader.coverage(instrumentId, timeframe));
    }

    private static HistoryCandleResponse toResponse(AggregatedCandle candle, BigDecimal cumulativeAdjustmentFactor) {
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
                candle.definitionVersion(),
                cumulativeAdjustmentFactor);
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
