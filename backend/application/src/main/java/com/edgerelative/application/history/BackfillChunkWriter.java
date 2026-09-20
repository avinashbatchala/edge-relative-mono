package com.edgerelative.application.history;

import com.edgerelative.application.history.HistoryRepository.ClaimedChunk;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.broker.api.model.BrokerCandle;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
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
    private final NseTradingCalendar calendar;
    private final Clock clock;

    public BackfillChunkWriter(
            HistoryRepository repository,
            HistoryProperties properties,
            NseTradingCalendar calendar,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional
    public int apply(ClaimedChunk chunk, List<BrokerCandle> candles) {
        repository.lockSeries(chunk.instrumentId(), chunk.timeframeId());
        // The persisted M1 base follows the candle definition: only canonical continuous-session
        // minutes. Pre-open/auction and post-close prints belong to a separate dataset (DD-05
        // §§99/115) and must not be stamped 'er-m1-base-v1'/'GOOD', where they would inflate
        // coverage and mislead any consumer that reads the store directly. Special sessions are
        // honoured through the calendar overrides (DD-05 §116).
        List<NewCandle> canonical = new ArrayList<>(candles.size());
        int malformed = 0;
        for (BrokerCandle candle : candles) {
            if (candle.openTime() == null || !calendar.isSessionMinute(candle.openTime())) {
                continue;
            }
            if (!wellFormed(candle)) {
                malformed++;
                continue;
            }
            canonical.add(new NewCandle(
                    candle.openTime(),
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    candle.volume(),
                    candle.openInterest()));
        }
        if (malformed > 0) {
            // Flag the bad rows (DD-05 §117); never fabricate replacement values.
            repository.recordIncident(
                    chunk.instrumentId(),
                    "CORRUPT_DATA",
                    "WARN",
                    clock.instant(),
                    "{\"timeframeId\":" + chunk.timeframeId() + ",\"malformedCandles\":" + malformed + "}");
        }
        repository.upsertCandles(
                chunk.instrumentId(), chunk.timeframeId(), canonical, properties.getInsertBatchSize());
        // Record the accepted (current) candle count, not the rows written: a re-fetch of an already
        // populated chunk writes nothing yet the coverage must still agree with the candle query.
        int accepted = repository.countCurrentCandles(
                chunk.instrumentId(), chunk.timeframeId(), chunk.start(), chunk.end());
        repository.markCoverageCompleted(chunk.coverageId(), accepted);
        return accepted;
    }

    /**
     * Candle validation (DD-05 §117): {@code low <= open/close <= high}, {@code high >= low} and
     * {@code volume >= 0}. Null OHLC is left to the mapper, which already drops unusable rows.
     */
    private static boolean wellFormed(BrokerCandle candle) {
        BigDecimal open = candle.open();
        BigDecimal high = candle.high();
        BigDecimal low = candle.low();
        BigDecimal close = candle.close();
        if (high == null || low == null || close == null) {
            return false;
        }
        if (high.compareTo(low) < 0 || candle.volume() < 0) {
            return false;
        }
        if (close.compareTo(high) > 0 || close.compareTo(low) < 0) {
            return false;
        }
        return open == null || (open.compareTo(high) <= 0 && open.compareTo(low) >= 0);
    }
}
