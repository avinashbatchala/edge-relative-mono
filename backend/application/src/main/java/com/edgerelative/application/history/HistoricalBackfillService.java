package com.edgerelative.application.history;

import com.edgerelative.application.history.HistoricalBackfillPlanner.Chunk;
import com.edgerelative.application.history.HistoryRepository.ClaimedChunk;
import com.edgerelative.application.history.api.BackfillRunResponse;
import com.edgerelative.application.history.api.CoverageResponse;
import com.edgerelative.application.history.api.HistoryCandleResponse;
import com.edgerelative.application.history.api.StartBackfillRequest;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.watchlist.WatchlistService;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import com.edgerelative.broker.api.model.BrokerCandleSeries;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.HistoricalCandleRequest;
import com.edgerelative.broker.api.port.HistoricalDataBroker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans and executes historical backfills.
 *
 * <p>Coverage is inspected first so only missing chunks are fetched; a chunk is the resumable unit,
 * and every candle write is idempotent. All broker calls go through the existing resiliency layer at
 * BULK priority.
 */
@Service
public class HistoricalBackfillService {

    private final CanonicalInstrumentService canonical;
    private final HistoricalDataBroker historicalDataBroker;
    private final HistoryRepository repository;
    private final HistoryProperties properties;
    private final WatchlistService watchlist;
    private final CandleAggregator aggregator;

    public HistoricalBackfillService(
            CanonicalInstrumentService canonical,
            HistoricalDataBroker historicalDataBroker,
            HistoryRepository repository,
            HistoryProperties properties,
            WatchlistService watchlist,
            CandleAggregator aggregator) {
        this.canonical = canonical;
        this.historicalDataBroker = historicalDataBroker;
        this.repository = repository;
        this.properties = properties;
        this.watchlist = watchlist;
        this.aggregator = aggregator;
    }

    @Transactional
    public BackfillRunResponse start(StartBackfillRequest request) {
        if (request == null || request.from() == null || request.to() == null || request.timeframe() == null) {
            throw new HistoryException(HistoryException.INVALID, "instrumentId, timeframe, from and to are required");
        }
        // Only the M1 base is persisted from the broker; higher timeframes are derived from it
        // (DD-05 §94/§97) rather than independently trusting vendor bars.
        if (request.timeframe() != BrokerCandleInterval.ONE_MINUTE) {
            throw new HistoryException(
                    HistoryException.INVALID,
                    "Only ONE_MINUTE is the canonical persisted base; %s is derived".formatted(request.timeframe()));
        }
        // Only the active watchlist may be persisted; data collection does not imply execution
        // eligibility, but it is still deliberately limited to the watched universe.
        if (!watchlist.isWatched(request.instrumentId())) {
            throw new HistoryException(
                    HistoryException.NOT_WATCHED,
                    "Instrument %d is not on the active watchlist".formatted(request.instrumentId()));
        }
        long timeframeId = canonical.ensureTimeframe(request.timeframe());
        Duration maxWindow = historicalDataBroker.maxWindow(request.timeframe());
        List<Chunk> chunks = HistoricalBackfillPlanner.plan(request.from(), request.to(), maxWindow);
        // Gap-aware: only missing chunks are queued; completed coverage is left untouched.
        for (Chunk chunk : chunks) {
            repository.upsertPendingChunk(request.instrumentId(), timeframeId, chunk.start(), chunk.end());
        }
        UUID runKey = UUID.randomUUID();
        long runId = repository.createRun(
                runKey, request.instrumentId(), timeframeId, request.from(), request.to(), chunks.size());
        repository.refreshRun(runId);
        return repository.findRun(runKey.toString())
                .orElseThrow(() -> new HistoryException(HistoryException.NOT_FOUND, "Run not found"));
    }

    public CoverageResponse coverage(long instrumentId, BrokerCandleInterval timeframe) {
        long timeframeId = canonical.ensureTimeframe(timeframe);
        return repository.coverage(instrumentId, timeframeId, CanonicalInstrumentService.timeframeCode(timeframe));
    }

    public List<BackfillRunResponse> runs(long instrumentId, int limit) {
        return repository.runsForInstrument(instrumentId, Math.min(Math.max(limit, 1), 100));
    }

    public BackfillRunResponse run(String runKey) {
        return repository.findRun(runKey)
                .orElseThrow(() -> new HistoryException(HistoryException.NOT_FOUND, "Run not found: " + runKey));
    }

    public BackfillRunResponse retry(String runKey) {
        run(runKey);
        repository.requeueFailed(runKey);
        return run(runKey);
    }

    /**
     * Canonical series for a timeframe. M1 is read directly; higher timeframes are derived
     * deterministically from the persisted M1 base with the same aggregator used everywhere.
     */
    public List<HistoryCandleResponse> candles(
            long instrumentId, BrokerCandleInterval timeframe, Instant from, Instant to, int limit) {
        long m1TimeframeId = canonical.ensureTimeframe(BrokerCandleInterval.ONE_MINUTE);
        int requested = Math.min(Math.max(limit, 1), 10_000);
        if (timeframe == BrokerCandleInterval.ONE_MINUTE) {
            return aggregator
                    .aggregate(repository.candles(instrumentId, m1TimeframeId, from, to, requested), timeframe)
                    .stream()
                    .map(HistoricalBackfillService::toResponse)
                    .toList();
        }
        List<HistoricalCandle> source =
                repository.candles(instrumentId, m1TimeframeId, from, to, properties.getMaxSourceCandles());
        return aggregator.aggregate(source, timeframe).stream()
                .limit(requested)
                .map(HistoricalBackfillService::toResponse)
                .toList();
    }

    private static HistoryCandleResponse toResponse(AggregatedCandle candle) {
        return new HistoryCandleResponse(
                candle.openTime(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.openInterest(),
                candle.partial(),
                candle.definitionVersion());
    }

    // --- worker-facing ------------------------------------------------------------

    public Optional<ClaimedChunk> claimNextChunk() {
        return repository.claimNextChunk();
    }

    public void releaseChunk(ClaimedChunk chunk) {
        repository.releaseCoverage(chunk.coverageId());
    }

    @Transactional
    public void processChunk(ClaimedChunk chunk) {
        try {
            if (isBlank(chunk.brokerSymbol()) || isBlank(chunk.segment())) {
                throw new HistoryException(
                        HistoryException.INVALID, "No broker mapping for instrument " + chunk.instrumentId());
            }
            BrokerCandleInterval interval = CanonicalInstrumentService.intervalForTimeframeCode(chunk.timeframeCode());
            HistoricalCandleRequest request = new HistoricalCandleRequest(
                    BrokerExchange.valueOf(chunk.exchange()),
                    BrokerSegment.valueOf(chunk.segment()),
                    chunk.brokerSymbol(),
                    chunk.start(),
                    chunk.end(),
                    interval);
            BrokerCandleSeries series = historicalDataBroker.candles(request);
            int inserted = repository.insertCandles(
                    chunk.instrumentId(), chunk.timeframeId(), series.candles(), properties.getInsertBatchSize());
            repository.markCoverageCompleted(chunk.coverageId(), inserted);
        } catch (RuntimeException exception) {
            repository.markCoverageFailed(chunk.coverageId(), exception.getMessage());
        } finally {
            for (long runId : repository.coveringRunIds(
                    chunk.instrumentId(), chunk.timeframeId(), chunk.start(), chunk.end())) {
                repository.refreshRun(runId);
            }
        }
    }

    public void recoverStaleWork() {
        repository.recoverStaleWork();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
