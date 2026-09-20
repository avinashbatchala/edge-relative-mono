package com.edgerelative.application.history;

import com.edgerelative.application.history.HistoryRepository.ClaimedChunk;
import com.edgerelative.broker.api.model.BrokerCandle;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one chunk's candle writes atomically. Kept separate from the broker call and the failure
 * handling so that a failed write rolls back cleanly and the chunk can be marked FAILED in its own
 * transaction rather than being left stuck in RUNNING.
 */
@Service
public class BackfillChunkWriter {

    private final HistoryRepository repository;
    private final HistoryProperties properties;

    public BackfillChunkWriter(HistoryRepository repository, HistoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public int apply(ClaimedChunk chunk, List<BrokerCandle> candles) {
        repository.lockSeries(chunk.instrumentId(), chunk.timeframeId());
        List<NewCandle> canonical = candles.stream()
                .map(candle -> new NewCandle(
                        candle.openTime(),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close(),
                        candle.volume(),
                        candle.openInterest()))
                .toList();
        int written = repository.upsertCandles(
                chunk.instrumentId(), chunk.timeframeId(), canonical, properties.getInsertBatchSize());
        repository.markCoverageCompleted(chunk.coverageId(), written);
        return written;
    }
}
