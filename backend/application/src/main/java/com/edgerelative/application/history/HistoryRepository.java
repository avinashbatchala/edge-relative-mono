package com.edgerelative.application.history;

import com.edgerelative.application.history.api.BackfillRunResponse;
import com.edgerelative.application.history.api.CoverageResponse;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.broker.api.model.BrokerCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** jOOQ persistence for canonical candles, coverage, and backfill runs. */
@Repository
public class HistoryRepository {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final DSLContext dsl;

    public HistoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // --- candles ------------------------------------------------------------------

    public int insertCandles(long instrumentId, long timeframeId, List<BrokerCandle> candles, int batchSize) {
        if (candles.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        List<Query> batch = new ArrayList<>(Math.min(batchSize, candles.size()));
        for (BrokerCandle candle : candles) {
            batch.add(dsl.query(
                    "INSERT INTO market.candle (instrument_id, timeframe_id, open_time, open, high, low, close, "
                            + "volume, open_interest, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'GROWW') "
                            + "ON CONFLICT (instrument_id, timeframe_id, open_time) DO NOTHING",
                    instrumentId,
                    timeframeId,
                    timestamp(candle.openTime()),
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    candle.volume(),
                    candle.openInterest()));
            if (batch.size() >= batchSize) {
                inserted += sum(dsl.batch(batch).execute());
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            inserted += sum(dsl.batch(batch).execute());
        }
        return inserted;
    }

    public List<HistoricalCandle> candles(long instrumentId, long timeframeId, Instant from, Instant to, int limit) {
        return dsl.fetch(
                        "SELECT open_time, open, high, low, close, volume, open_interest FROM market.candle "
                                + "WHERE instrument_id = ? AND timeframe_id = ? AND open_time >= ? AND open_time <= ? "
                                + "ORDER BY open_time LIMIT ?",
                        instrumentId,
                        timeframeId,
                        timestamp(from),
                        timestamp(to),
                        limit)
                .map(record -> new HistoricalCandle(
                        record.get("open_time", OffsetDateTime.class).toInstant(),
                        record.get("open", BigDecimal.class),
                        record.get("high", BigDecimal.class),
                        record.get("low", BigDecimal.class),
                        record.get("close", BigDecimal.class),
                        record.get("volume", Long.class),
                        record.get("open_interest", BigDecimal.class)));
    }

    // --- coverage -----------------------------------------------------------------

    public void upsertPendingChunk(long instrumentId, long timeframeId, Instant start, Instant end) {
        dsl.execute(
                "INSERT INTO market.candle_coverage (instrument_id, timeframe_id, chunk_start, chunk_end, status) "
                        + "VALUES (?, ?, ?, ?, 'PENDING') ON CONFLICT (instrument_id, timeframe_id, chunk_start, chunk_end) "
                        + "DO UPDATE SET updated_at = CURRENT_TIMESTAMP WHERE market.candle_coverage.status = 'FAILED'",
                instrumentId,
                timeframeId,
                timestamp(start),
                timestamp(end));
    }

    public CoverageResponse coverage(long instrumentId, long timeframeId, String timeframeCode) {
        Record totals = dsl.fetchOne(
                "SELECT min(open_time) AS earliest, max(open_time) AS latest, count(*) AS candle_count "
                        + "FROM market.candle WHERE instrument_id = ? AND timeframe_id = ?",
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
        return new CoverageResponse(
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
                        + "    SELECT 1 FROM market.backfill_run br "
                        + "    WHERE br.instrument_id = c2.instrument_id AND br.timeframe_id = c2.timeframe_id "
                        + "      AND br.status IN ('QUEUED','RUNNING') "
                        + "      AND br.requested_from <= c2.chunk_start AND br.requested_to >= c2.chunk_end) "
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
                        "SELECT backfill_run_id FROM market.backfill_run "
                                + "WHERE instrument_id = ? AND timeframe_id = ? AND status IN ('QUEUED','RUNNING') "
                                + "AND requested_from <= ? AND requested_to >= ?",
                        instrumentId,
                        timeframeId,
                        timestamp(start),
                        timestamp(end))
                .getValues("backfill_run_id", Long.class);
    }

    // --- runs ---------------------------------------------------------------------

    public long createRun(
            UUID runKey, long instrumentId, long timeframeId, Instant from, Instant to, int totalChunks) {
        Record record = dsl.fetchOne(
                "INSERT INTO market.backfill_run (run_key, instrument_id, timeframe_id, requested_from, requested_to, "
                        + "status, total_chunks) VALUES (?, ?, ?, ?, ?, 'QUEUED', ?) RETURNING backfill_run_id",
                runKey,
                instrumentId,
                timeframeId,
                timestamp(from),
                timestamp(to),
                totalChunks);
        return record.get("backfill_run_id", Long.class);
    }

    public void refreshRun(long runId) {
        dsl.execute(
                "UPDATE market.backfill_run br SET "
                        + "completed_chunks = (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = br.instrument_id "
                        + "  AND cc.timeframe_id = br.timeframe_id AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to AND cc.status = 'COMPLETED'), "
                        + "failed_chunks = (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = br.instrument_id "
                        + "  AND cc.timeframe_id = br.timeframe_id AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to AND cc.status = 'FAILED'), "
                        + "candles_written = (SELECT COALESCE(sum(cc.candle_count), 0) FROM market.candle_coverage cc WHERE cc.instrument_id = br.instrument_id "
                        + "  AND cc.timeframe_id = br.timeframe_id AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to), "
                        + "status = CASE "
                        + "  WHEN br.status = 'CANCELLED' THEN 'CANCELLED' "
                        + "  WHEN (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = br.instrument_id "
                        + "        AND cc.timeframe_id = br.timeframe_id AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to AND cc.status IN ('PENDING','RUNNING')) > 0 THEN 'RUNNING' "
                        + "  WHEN (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = br.instrument_id "
                        + "        AND cc.timeframe_id = br.timeframe_id AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to AND cc.status = 'FAILED') > 0 THEN 'PARTIAL' "
                        + "  ELSE 'COMPLETED' END, "
                        + "completed_at = CASE "
                        + "  WHEN (SELECT count(*) FROM market.candle_coverage cc WHERE cc.instrument_id = br.instrument_id "
                        + "        AND cc.timeframe_id = br.timeframe_id AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to AND cc.status IN ('PENDING','RUNNING')) = 0 THEN CURRENT_TIMESTAMP "
                        + "  ELSE NULL END "
                        + "WHERE br.backfill_run_id = ?",
                runId);
    }

    public Optional<BackfillRunResponse> findRun(String runKey) {
        Record record = dsl.fetchOne(
                "SELECT br.run_key, br.instrument_id, t.code AS timeframe_code, br.requested_from, br.requested_to, "
                        + "br.status, br.total_chunks, br.completed_chunks, br.failed_chunks, br.candles_written, "
                        + "br.last_error, br.created_at, br.updated_at, br.completed_at "
                        + "FROM market.backfill_run br JOIN reference.timeframe t ON t.timeframe_id = br.timeframe_id "
                        + "WHERE br.run_key = ?",
                UUID.fromString(runKey));
        return record == null ? Optional.empty() : Optional.of(toRun(record));
    }

    public List<BackfillRunResponse> runsForInstrument(long instrumentId, int limit) {
        return dsl.fetch(
                        "SELECT br.run_key, br.instrument_id, t.code AS timeframe_code, br.requested_from, br.requested_to, "
                                + "br.status, br.total_chunks, br.completed_chunks, br.failed_chunks, br.candles_written, "
                                + "br.last_error, br.created_at, br.updated_at, br.completed_at "
                                + "FROM market.backfill_run br JOIN reference.timeframe t ON t.timeframe_id = br.timeframe_id "
                                + "WHERE br.instrument_id = ? ORDER BY br.created_at DESC LIMIT ?",
                        instrumentId,
                        limit)
                .map(HistoryRepository::toRun);
    }

    public void requeueFailed(String runKey) {
        dsl.execute(
                "UPDATE market.candle_coverage cc SET status = 'PENDING', last_error = NULL "
                        + "FROM market.backfill_run br WHERE br.run_key = ? "
                        + "AND cc.instrument_id = br.instrument_id AND cc.timeframe_id = br.timeframe_id "
                        + "AND cc.status = 'FAILED' AND cc.chunk_start >= br.requested_from AND cc.chunk_end <= br.requested_to",
                UUID.fromString(runKey));
        dsl.execute(
                "UPDATE market.backfill_run SET status = 'QUEUED', last_error = NULL, completed_at = NULL WHERE run_key = ?",
                UUID.fromString(runKey));
    }

    public void recoverStaleWork() {
        dsl.execute("UPDATE market.candle_coverage SET status = 'PENDING' WHERE status = 'RUNNING'");
        dsl.execute("UPDATE market.backfill_run SET status = 'QUEUED' WHERE status = 'RUNNING'");
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

    private static java.sql.Timestamp timestamp(Instant instant) {
        // The JDBC session is pinned to UTC (Hikari connection-init-sql), so a plain timestamp is
        // unambiguous. jOOQ plain SQL does not infer parameter types from the schema.
        return instant == null ? null : java.sql.Timestamp.from(instant);
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

    /** One claimed chunk plus everything needed to call the broker. */
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
