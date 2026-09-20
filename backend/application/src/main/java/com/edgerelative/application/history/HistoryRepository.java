package com.edgerelative.application.history;

import com.edgerelative.application.history.api.BackfillRunResponse;
import com.edgerelative.application.reference.CanonicalInstrumentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * jOOQ persistence for canonical candles, coverage, and ingestion runs.
 */
@Repository
public class HistoryRepository {

    private static final int MAX_ERROR_LENGTH = 1000;
    private static final String M1_DEFINITION_VERSION = "er-m1-base-v1";
    private static final String SOURCE_REVISION = "groww-m1-backfill-v1";
    private static final int M1_CLOSE_SECONDS = 60;

    private final DSLContext dsl;

    public HistoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // --- candles ------------------------------------------------------------------

    /**
     * Serializes writers for one instrument/timeframe for the current transaction. Consecutive
     * boundary chunks can both fetch the same minute, so without this two workers could race the
     * read-then-insert revision logic.
     */
    public void lockSeries(long instrumentId, long timeframeId) {
        dsl.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                "market.candle:" + instrumentId + ":" + timeframeId);
    }

    /**
     * Writes M1 candles idempotently and append-only. An identical existing current bar is left
     * untouched; a changed bar becomes a new revision and the previous one is marked non-current
     * (DD-05 §105/§106). Returns the number of rows written.
     */
    public int upsertCandles(
            long instrumentId, long timeframeId, List<NewCandle> candles, int batchSize) {
        if (candles.isEmpty()) {
            return 0;
        }
        Instant min = candles.get(0).openTime();
        Instant max = min;
        for (NewCandle candle : candles) {
            if (candle.openTime().isBefore(min)) {
                min = candle.openTime();
            }
            if (candle.openTime().isAfter(max)) {
                max = candle.openTime();
            }
        }
        Map<Instant, Record> current = new HashMap<>();
        for (Record record : dsl.fetch(
                "SELECT candle_id, open_time, revision_no, open, high, low, close, volume, open_interest, close_time "
                        + "FROM market.candle WHERE instrument_id = ? AND timeframe_id = ? "
                        + "AND candle_definition_version = ? AND is_current AND open_time BETWEEN ?::timestamptz AND ?::timestamptz",
                instrumentId,
                timeframeId,
                M1_DEFINITION_VERSION,
                utc(min),
                utc(max))) {
            current.put(record.get("open_time", OffsetDateTime.class).toInstant(), record);
        }

        List<Query> inserts = new ArrayList<>();
        List<Long> superseded = new ArrayList<>();
        for (NewCandle candle : candles) {
            Instant openTime = candle.openTime();
            Instant closeTime = openTime.plusSeconds(M1_CLOSE_SECONDS);
            Record existing = current.get(openTime);
            if (existing == null) {
                inserts.add(insertCandle(instrumentId, timeframeId, candle, closeTime, 1, null));
            } else if (!sameValues(existing, candle, closeTime)) {
                superseded.add(existing.get("candle_id", Long.class));
                inserts.add(insertCandle(
                        instrumentId,
                        timeframeId,
                        candle,
                        closeTime,
                        existing.get("revision_no", Integer.class) + 1,
                        existing.get("revision_no", Integer.class)));
            }
        }

        if (superseded.isEmpty() && inserts.isEmpty()) {
            return 0;
        }
        List<Query> demotions = new ArrayList<>(superseded.size());
        for (Long candleId : superseded) {
            demotions.add(dsl.query("UPDATE market.candle SET is_current = FALSE WHERE candle_id = ?", candleId));
        }
        int written = 0;
        for (int i = 0; i < demotions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, demotions.size());
            dsl.batch(demotions.subList(i, end)).execute();
        }
        for (int i = 0; i < inserts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, inserts.size());
            written += sum(dsl.batch(inserts.subList(i, end)).execute());
        }
        return written;
    }

    private Query insertCandle(
            long instrumentId,
            long timeframeId,
            NewCandle candle,
            Instant closeTime,
            int revisionNo,
            Integer previousRevisionNo) {
        return dsl.query(
                "INSERT INTO market.candle (instrument_id, timeframe_id, open_time, close_time, open, high, low, "
                        + "close, volume, open_interest, source, candle_definition_version, source_revision, "
                        + "revision_no, previous_revision_no, is_current, is_complete, quality_state) "
                        + "VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, ?, ?, 'GROWW', ?, ?, ?, ?, TRUE, TRUE, 'GOOD') "
                        // Conflict-tolerant: a concurrent/duplicate write of the same bar is ignored
                        // rather than aborting the whole chunk (the series lock usually prevents this).
                        + "ON CONFLICT DO NOTHING",
                instrumentId,
                timeframeId,
                utc(candle.openTime()),
                utc(closeTime),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.openInterest(),
                M1_DEFINITION_VERSION,
                SOURCE_REVISION,
                revisionNo,
                previousRevisionNo);
    }

    private static boolean sameValues(Record existing, NewCandle candle, Instant closeTime) {
        return equal(existing.get("open", BigDecimal.class), candle.open())
                && equal(existing.get("high", BigDecimal.class), candle.high())
                && equal(existing.get("low", BigDecimal.class), candle.low())
                && equal(existing.get("close", BigDecimal.class), candle.close())
                && existing.get("volume", Long.class) == candle.volume()
                && equal(existing.get("open_interest", BigDecimal.class), candle.openInterest())
                && closeTime.equals(existing.get("close_time", OffsetDateTime.class).toInstant());
    }

    private static boolean equal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    /**
     * Reads canonical candles ascending. When the range holds more than {@code limit} rows the
     * <em>most recent</em> rows are returned, not the earliest: callers (charts, feature warmup,
     * replay) always need the window ending at {@code to}. Returning the earliest rows would silently
     * move a feature anchor into the past (DD-05 §128/§151).
     */
    public List<HistoricalCandle> candles(
            long instrumentId, long timeframeId, Instant from, Instant to, int limit) {
        return dsl.fetch(
                        "SELECT open_time, close_time, open, high, low, close, volume, open_interest, trade_count, "
                                + "vwap, is_complete, quality_state FROM ("
                                + "  SELECT open_time, close_time, open, high, low, close, volume, open_interest, "
                                + "         trade_count, vwap, is_complete, quality_state FROM market.candle "
                                + "  WHERE instrument_id = ? AND timeframe_id = ? AND is_current "
                                // Half-open [from, to), matching the chunk, coverage and validity
                                // ranges; a closed end would return the boundary minute and disagree
                                // with the chunk that produced it.
                                + "  AND open_time >= ?::timestamptz AND open_time < ?::timestamptz "
                                + "  ORDER BY open_time DESC LIMIT ?"
                                + ") recent ORDER BY open_time",
                        instrumentId,
                        timeframeId,
                        utc(from),
                        utc(to),
                        limit)
                .map(record -> new HistoricalCandle(
                        record.get("open_time", OffsetDateTime.class).toInstant(),
                        record.get("close_time", OffsetDateTime.class).toInstant(),
                        record.get("open", BigDecimal.class),
                        record.get("high", BigDecimal.class),
                        record.get("low", BigDecimal.class),
                        record.get("close", BigDecimal.class),
                        record.get("volume", Long.class),
                        record.get("open_interest", BigDecimal.class),
                        record.get("trade_count", Integer.class),
                        record.get("vwap", BigDecimal.class),
                        Boolean.TRUE.equals(record.get("is_complete", Boolean.class)),
                        record.get("quality_state", String.class)));
    }

    // --- coverage -----------------------------------------------------------------

    public void upsertPendingChunk(long instrumentId, long timeframeId, Instant start, Instant end) {
        dsl.execute(
                "INSERT INTO market.candle_coverage (instrument_id, timeframe_id, market_data_source_id, "
                        + "chunk_start, chunk_end, status) VALUES (?, ?, (SELECT market_data_source_id FROM "
                        + "reference.market_data_source WHERE code = ?), ?::timestamptz, ?::timestamptz, 'PENDING') "
                        // Re-running a download requeues only previously failed chunks; completed and
                        // pending chunks are left untouched so the re-run focuses on what is missing.
                        + "ON CONFLICT (instrument_id, timeframe_id, chunk_start, chunk_end) DO UPDATE SET "
                        + "status = 'PENDING', last_error = NULL, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE market.candle_coverage.status = 'FAILED'",
                instrumentId,
                timeframeId,
                CanonicalInstrumentService.BROKER_CODE,
                utc(start),
                utc(end));
    }

    public HistoricalCoverage coverage(long instrumentId, long timeframeId, String timeframeCode) {
        Record totals = dsl.fetchOne(
                "SELECT min(open_time) AS earliest, max(open_time) AS latest, count(*) AS candle_count "
                        + "FROM market.candle WHERE instrument_id = ? AND timeframe_id = ? AND is_current",
                instrumentId,
                timeframeId);
        Record chunks = dsl.fetchOne(
                "SELECT count(*) FILTER (WHERE status = 'COMPLETED') AS completed, "
                        + "count(*) FILTER (WHERE status IN ('PENDING','RUNNING')) AS pending, "
                        + "count(*) FILTER (WHERE status = 'FAILED') AS failed, "
                        + "max(last_synced_at) AS last_synced "
                        + "FROM market.candle_coverage WHERE instrument_id = ? AND timeframe_id = ?",
                instrumentId,
                timeframeId);
        long candleCount = value(totals, "candle_count", 0L);
        int completed = (int) value(chunks, "completed", 0L);
        int pending = (int) value(chunks, "pending", 0L);
        int failed = (int) value(chunks, "failed", 0L);
        String status;
        if (pending > 0) {
            status = "RUNNING";
        } else if (failed > 0) {
            status = "PARTIAL";
        } else if (completed > 0 || candleCount > 0) {
            status = "COMPLETE";
        } else {
            status = "EMPTY";
        }
        OffsetDateTime earliest = totals == null ? null : totals.get("earliest", OffsetDateTime.class);
        OffsetDateTime latest = totals == null ? null : totals.get("latest", OffsetDateTime.class);
        OffsetDateTime lastSynced = chunks == null ? null : chunks.get("last_synced", OffsetDateTime.class);
        return new HistoricalCoverage(
                instrumentId,
                timeframeCode,
                earliest == null ? null : earliest.toInstant(),
                latest == null ? null : latest.toInstant(),
                candleCount,
                completed,
                pending,
                failed,
                lastSynced == null ? null : lastSynced.toInstant(),
                status);
    }

    public Optional<ClaimedChunk> claimNextChunk() {
        Record claimed = dsl.fetchOne(
                "UPDATE market.candle_coverage cc SET status = 'RUNNING', attempts = cc.attempts + 1 "
                        + "WHERE cc.candle_coverage_id = ("
                        + "  SELECT c2.candle_coverage_id FROM market.candle_coverage c2 "
                        + "  WHERE c2.status = 'PENDING' AND EXISTS ("
                        + "    SELECT 1 FROM market.ingestion_run ir "
                        + "    WHERE ir.instrument_id = c2.instrument_id AND ir.timeframe_id = c2.timeframe_id "
                        + "      AND ir.status IN ('QUEUED','RUNNING') "
                        + "      AND ir.requested_from <= c2.chunk_start AND ir.requested_to >= c2.chunk_end) "
                        + "  ORDER BY c2.chunk_start LIMIT 1 FOR UPDATE SKIP LOCKED) "
                        + "RETURNING candle_coverage_id, instrument_id, timeframe_id, chunk_start, chunk_end");
        if (claimed == null) {
            return Optional.empty();
        }
        long timeframeId = claimed.get("timeframe_id", Long.class);
        long instrumentId = claimed.get("instrument_id", Long.class);
        Record meta = dsl.fetchOne(
                "SELECT e.code AS exchange, i.segment AS segment, t.code AS timeframe_code, "
                        + "bm.broker_token AS broker_symbol "
                        + "FROM reference.instrument i "
                        + "JOIN reference.exchange e ON e.exchange_id = i.exchange_id "
                        + "JOIN reference.timeframe t ON t.timeframe_id = ? "
                        + "LEFT JOIN reference.broker b ON b.code = ? "
                        + "LEFT JOIN reference.broker_instrument_mapping bm ON bm.instrument_id = i.instrument_id "
                        + "AND bm.broker_id = b.broker_id AND bm.valid_to IS NULL "
                        + "WHERE i.instrument_id = ?",
                timeframeId,
                CanonicalInstrumentService.BROKER_CODE,
                instrumentId);
        return Optional.of(new ClaimedChunk(
                claimed.get("candle_coverage_id", Long.class),
                instrumentId,
                timeframeId,
                meta.get("exchange", String.class),
                meta.get("segment", String.class),
                meta.get("timeframe_code", String.class),
                meta.get("broker_symbol", String.class),
                claimed.get("chunk_start", OffsetDateTime.class).toInstant(),
                claimed.get("chunk_end", OffsetDateTime.class).toInstant()));
    }

    /**
     * Current canonical candles within a half-open chunk range. Used to record the accepted count so
     * coverage and candle queries agree even when a re-fetch writes no new rows.
     */
    public int countCurrentCandles(long instrumentId, long timeframeId, Instant start, Instant end) {
        Long count = dsl.fetchOne(
                        "SELECT count(*) AS c FROM market.candle WHERE instrument_id = ? AND timeframe_id = ? "
                                + "AND is_current AND open_time >= ?::timestamptz AND open_time < ?::timestamptz",
                        instrumentId,
                        timeframeId,
                        utc(start),
                        utc(end))
                .get("c", Long.class);
        return count == null ? 0 : count.intValue();
    }

    public void markCoverageCompleted(long coverageId, int candleCount) {
        dsl.execute(
                "UPDATE market.candle_coverage SET status = 'COMPLETED', candle_count = ?, last_error = NULL, "
                        + "last_synced_at = CURRENT_TIMESTAMP WHERE candle_coverage_id = ?",
                candleCount,
                coverageId);
    }

    public void markCoverageFailed(long coverageId, String error) {
        dsl.execute(
                "UPDATE market.candle_coverage SET status = 'FAILED', last_error = ? WHERE candle_coverage_id = ?",
                truncate(error),
                coverageId);
    }

    public void releaseCoverage(long coverageId) {
        dsl.execute(
                "UPDATE market.candle_coverage SET status = 'PENDING' WHERE candle_coverage_id = ?", coverageId);
    }

    public List<Long> coveringRunIds(long instrumentId, long timeframeId, Instant start, Instant end) {
        return dsl.fetch(
                        "SELECT ingestion_run_id FROM market.ingestion_run "
                                + "WHERE instrument_id = ? AND timeframe_id = ? AND status IN ('QUEUED','RUNNING') "
                                + "AND requested_from <= ?::timestamptz AND requested_to >= ?::timestamptz",
                        instrumentId,
                        timeframeId,
                        utc(start),
                        utc(end))
                .getValues("ingestion_run_id", Long.class);
    }

    // --- runs ---------------------------------------------------------------------

    public long createRun(
            UUID runKey, long instrumentId, long timeframeId, Instant from, Instant to, int totalChunks) {
        Record record = dsl.fetchOne(
                "INSERT INTO market.ingestion_run (run_key, instrument_id, timeframe_id, market_data_source_id, "
                        + "requested_from, requested_to, status, total_chunks) VALUES (?, ?, ?, (SELECT "
                        + "market_data_source_id FROM reference.market_data_source WHERE code = ?), "
                        + "?::timestamptz, ?::timestamptz, 'QUEUED', ?) "
                        + "RETURNING ingestion_run_id",
                runKey,
                instrumentId,
                timeframeId,
                CanonicalInstrumentService.BROKER_CODE,
                utc(from),
                utc(to),
                totalChunks);
        return record.get("ingestion_run_id", Long.class);
    }

    public void refreshRun(long runId) {
        dsl.execute(
                "UPDATE market.ingestion_run ir SET "
                        + "completed_chunks = (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = ir.instrument_id "
                        + "  AND cc.timeframe_id = ir.timeframe_id AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to AND cc.status = 'COMPLETED'), "
                        + "failed_chunks = (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = ir.instrument_id "
                        + "  AND cc.timeframe_id = ir.timeframe_id AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to AND cc.status = 'FAILED'), "
                        + "candles_written = (SELECT COALESCE(sum(cc.candle_count), 0) FROM market.candle_coverage cc WHERE cc.instrument_id = ir.instrument_id "
                        + "  AND cc.timeframe_id = ir.timeframe_id AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to), "
                        + "status = CASE "
                        + "  WHEN ir.status = 'CANCELLED' THEN 'CANCELLED' "
                        + "  WHEN (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = ir.instrument_id "
                        + "        AND cc.timeframe_id = ir.timeframe_id AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to AND cc.status IN ('PENDING','RUNNING')) > 0 THEN 'RUNNING' "
                        + "  WHEN (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = ir.instrument_id "
                        + "        AND cc.timeframe_id = ir.timeframe_id AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to AND cc.status = 'FAILED') > 0 THEN 'PARTIAL' "
                        + "  ELSE 'COMPLETED' END, "
                        + "completed_at = CASE "
                        + "  WHEN (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = ir.instrument_id "
                        + "        AND cc.timeframe_id = ir.timeframe_id AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to AND cc.status IN ('PENDING','RUNNING')) = 0 THEN CURRENT_TIMESTAMP "
                        + "  ELSE NULL END "
                        + "WHERE ir.ingestion_run_id = ?",
                runId);
    }

    public Optional<BackfillRunResponse> findRun(String runKey) {
        Record record = dsl.fetchOne(
                "SELECT ir.run_key, ir.instrument_id, t.code AS timeframe_code, ir.requested_from, ir.requested_to, "
                        + "ir.status, ir.total_chunks, ir.completed_chunks, ir.failed_chunks, ir.candles_written, "
                        + "ir.last_error, ir.created_at, ir.updated_at, ir.completed_at "
                        + "FROM market.ingestion_run ir JOIN reference.timeframe t ON t.timeframe_id = ir.timeframe_id "
                        + "WHERE ir.run_key = ?",
                UUID.fromString(runKey));
        return record == null ? Optional.empty() : Optional.of(toRun(record));
    }

    public List<BackfillRunResponse> runsForInstrument(long instrumentId, int limit) {
        return dsl.fetch(
                        "SELECT ir.run_key, ir.instrument_id, t.code AS timeframe_code, ir.requested_from, ir.requested_to, "
                                + "ir.status, ir.total_chunks, ir.completed_chunks, ir.failed_chunks, ir.candles_written, "
                                + "ir.last_error, ir.created_at, ir.updated_at, ir.completed_at "
                                + "FROM market.ingestion_run ir JOIN reference.timeframe t ON t.timeframe_id = ir.timeframe_id "
                                + "WHERE ir.instrument_id = ? ORDER BY ir.created_at DESC LIMIT ?",
                        instrumentId,
                        limit)
                .map(HistoryRepository::toRun);
    }

    public void requeueFailed(String runKey) {
        dsl.execute(
                "UPDATE market.candle_coverage cc SET status = 'PENDING', last_error = NULL "
                        + "FROM market.ingestion_run ir WHERE ir.run_key = ? "
                        + "AND cc.instrument_id = ir.instrument_id AND cc.timeframe_id = ir.timeframe_id "
                        + "AND cc.status = 'FAILED' AND cc.chunk_start >= ir.requested_from AND cc.chunk_end <= ir.requested_to",
                UUID.fromString(runKey));
        dsl.execute(
                "UPDATE market.ingestion_run SET status = 'QUEUED', last_error = NULL, completed_at = NULL WHERE run_key = ?",
                UUID.fromString(runKey));
    }

    public void recoverStaleWork() {
        dsl.execute("UPDATE market.candle_coverage SET status = 'PENDING' WHERE status = 'RUNNING'");
        dsl.execute("UPDATE market.ingestion_run SET status = 'QUEUED' WHERE status = 'RUNNING'");
    }

    // --- helpers ------------------------------------------------------------------

    private static int sum(int[] results) {
        int total = 0;
        for (int result : results) {
            if (result > 0) {
                total += result;
            }
        }
        return total;
    }

    private static BackfillRunResponse toRun(Record record) {
        return new BackfillRunResponse(
                record.get("run_key", UUID.class).toString(),
                record.get("instrument_id", Long.class),
                record.get("timeframe_code", String.class),
                record.get("requested_from", OffsetDateTime.class).toInstant(),
                record.get("requested_to", OffsetDateTime.class).toInstant(),
                record.get("status", String.class),
                record.get("total_chunks", Integer.class),
                record.get("completed_chunks", Integer.class),
                record.get("failed_chunks", Integer.class),
                record.get("candles_written", Long.class),
                record.get("last_error", String.class),
                record.get("created_at", OffsetDateTime.class).toInstant(),
                record.get("updated_at", OffsetDateTime.class).toInstant(),
                record.get("completed_at", OffsetDateTime.class) == null
                        ? null
                        : record.get("completed_at", OffsetDateTime.class).toInstant());
    }

    /**
     * Binds instants as explicit UTC ISO strings cast to {@code timestamptz}. This avoids any
     * dependence on the JVM default zone (DD-04: the host timezone must not define behavior).
     */
    private static String utc(Instant instant) {
        return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC).toString();
    }

    private static long value(Record record, String field, long fallback) {
        Long value = record == null ? null : record.get(field, Long.class);
        return value == null ? fallback : value;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    /**
     * One claimed chunk plus everything needed to call the broker.
     */
    public record ClaimedChunk(
            long coverageId,
            long instrumentId,
            long timeframeId,
            String exchange,
            String segment,
            String timeframeCode,
            String brokerSymbol,
            Instant start,
            Instant end) {
    }
}
