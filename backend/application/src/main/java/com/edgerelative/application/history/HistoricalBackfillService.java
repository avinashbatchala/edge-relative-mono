package com.edgerelative.application.history;

import com.edgerelative.application.history.HistoricalBackfillPlanner.Chunk;
import com.edgerelative.application.history.HistoryRepository.ClaimedChunk;
import com.edgerelative.application.history.api.BackfillRunResponse;
import com.edgerelative.application.history.api.StartBackfillRequest;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.TimeframeCatalog;
import com.edgerelative.application.watchlist.WatchlistService;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import com.edgerelative.broker.api.model.BrokerCandleSeries;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.HistoricalCandleRequest;
import com.edgerelative.broker.api.port.HistoricalDataBroker;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans and executes historical backfills (obtaining/populating canonical data).
 *
 * <p>Coverage is inspected first so only missing chunks are fetched; a chunk is the resumable unit,
 * and every candle write is idempotent. All broker calls go through the existing resiliency layer at
 * BULK priority. This is the write/ingestion side only — canonical reads live in
 * {@code HistoricalDataQueryService} (DD-05 §94/§97).
 */
@Service
public class HistoricalBackfillService {

    private final CanonicalInstrumentService canonical;
    private final HistoricalDataBroker historicalDataBroker;
    private final HistoryRepository repository;
    private final WatchlistService watchlist;
    private final BackfillChunkWriter chunkWriter;

    public HistoricalBackfillService(
            CanonicalInstrumentService canonical,
            HistoricalDataBroker historicalDataBroker,
            HistoryRepository repository,
            WatchlistService watchlist,
            BackfillChunkWriter chunkWriter) {
        this.canonical = canonical;
        this.historicalDataBroker = historicalDataBroker;
        this.repository = repository;
        this.watchlist = watchlist;
        this.chunkWriter = chunkWriter;
    }

    @Transactional
    public BackfillRunResponse start(StartBackfillRequest request) {
        if (request == null || request.from() == null || request.to() == null || request.timeframe() == null) {
            throw new HistoryException(HistoryException.INVALID, "instrumentId, timeframe, from and to are required");
        }
        // Only the M1 base is persisted from the broker; higher timeframes are derived from it
        // (DD-05 §94/§97) rather than independently trusting vendor bars.
        if (!TimeframeCatalog.M1.equalsIgnoreCase(request.timeframe().trim())) {
            throw new HistoryException(
                    HistoryException.INVALID,
                    "Only M1 is the canonical persisted base; %s is derived".formatted(request.timeframe()));
        }
        // Only the active watchlist may be persisted; data collection does not imply execution
        // eligibility, but it is still deliberately limited to the watched universe.
        if (!watchlist.isWatched(request.instrumentId())) {
            throw new HistoryException(
                    HistoryException.NOT_WATCHED,
                    "Instrument %d is not on the active watchlist".formatted(request.instrumentId()));
        }
        long timeframeId = canonical.ensureTimeframe(TimeframeCatalog.M1);
        Duration maxWindow = historicalDataBroker.maxWindow(BrokerCandleInterval.ONE_MINUTE);
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

    // --- worker-facing ------------------------------------------------------------

    public Optional<ClaimedChunk> claimNextChunk() {
        return repository.claimNextChunk();
    }

    public void releaseChunk(ClaimedChunk chunk) {
        repository.releaseCoverage(chunk.coverageId());
    }

    /**
     * Claims -> fetches -> writes one chunk. Intentionally not transactional: the broker call runs
     * outside any transaction, the write is atomic in {@link BackfillChunkWriter}, and failure
     * marking runs in its own transaction so a bad chunk ends FAILED instead of stuck RUNNING.
     */
    public void processChunk(ClaimedChunk chunk) {
        try {
            if (isBlank(chunk.brokerSymbol()) || isBlank(chunk.segment())) {
                throw new HistoryException(
                        HistoryException.INVALID, "No broker mapping for instrument " + chunk.instrumentId());
            }
            HistoricalCandleRequest request = new HistoricalCandleRequest(
                    BrokerExchange.valueOf(chunk.exchange()),
                    BrokerSegment.valueOf(chunk.segment()),
                    chunk.brokerSymbol(),
                    chunk.start(),
                    chunk.end(),
                    BrokerCandleInterval.ONE_MINUTE);
            BrokerCandleSeries series = historicalDataBroker.candles(request);
            chunkWriter.apply(chunk, series.candles());
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
