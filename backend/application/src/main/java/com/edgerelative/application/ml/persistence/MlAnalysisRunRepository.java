package com.edgerelative.application.ml.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Persistence for the ML analysis-run queue (DD-04/DD-07 ML ops). The UI enqueues a run; the Python
 * research runner atomically claims the oldest queued run with {@code FOR UPDATE SKIP LOCKED}, trains,
 * and writes the result back. All timestamps are database time so multiple app instances agree.
 */
@Repository
public class MlAnalysisRunRepository {

    private final DSLContext dsl;

    public MlAnalysisRunRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record Run(
            long id,
            String key,
            String status,
            String requestedBy,
            String configJson,
            String progressJson,
            String metricsJson,
            Long modelVersionId,
            String error,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {
    }

    public String enqueue(String configJson, String requestedBy) {
        Record record = dsl.fetchOne(
                "INSERT INTO control.ml_analysis_run (config, requested_by) VALUES (?::jsonb, ?) "
                        + "RETURNING analysis_run_key",
                configJson, requestedBy);
        return record.get("analysis_run_key", java.util.UUID.class).toString();
    }

    /**
     * Claims the oldest queued run, marking it RUNNING with a lease. The sub-select takes a row lock
     * and skips rows already locked, so concurrent runners never claim the same run.
     */
    public Optional<Run> claimNext(String leaseOwner, long leaseSeconds) {
        Record record = dsl.fetchOne(
                "UPDATE control.ml_analysis_run SET status = 'RUNNING', started_at = now(), "
                        + "lease_owner = ?, lease_expires_at = now() + (? * interval '1 second'), "
                        + "error = NULL "
                        + "WHERE analysis_run_id = ("
                        + "  SELECT analysis_run_id FROM control.ml_analysis_run "
                        + "  WHERE status = 'QUEUED' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1) "
                        + "RETURNING *",
                leaseOwner, leaseSeconds);
        return record == null ? Optional.empty() : Optional.of(map(record));
    }

    public Optional<Run> find(String key) {
        Record record = dsl.fetchOne(
                "SELECT * FROM control.ml_analysis_run WHERE analysis_run_key = ?", java.util.UUID.fromString(key));
        return record == null ? Optional.empty() : Optional.of(map(record));
    }

    public List<Run> list(int limit) {
        return dsl.fetch("SELECT * FROM control.ml_analysis_run ORDER BY created_at DESC LIMIT ?", limit)
                .map(MlAnalysisRunRepository::map);
    }

    public void updateProgress(String key, String progressJson) {
        dsl.execute(
                "UPDATE control.ml_analysis_run SET progress = ?::jsonb WHERE analysis_run_key = ?",
                progressJson, java.util.UUID.fromString(key));
    }

    /** Marks a claimed run SUCCEEDED and links the model version the runner registered. */
    public void complete(String key, long modelVersionId, String metricsJson) {
        dsl.execute(
                "UPDATE control.ml_analysis_run SET status = 'SUCCEEDED', completed_at = now(), "
                        + "model_version_id = ?, metrics = ?::jsonb, lease_owner = NULL, lease_expires_at = NULL "
                        + "WHERE analysis_run_key = ?",
                modelVersionId, metricsJson, java.util.UUID.fromString(key));
    }

    public void fail(String key, String error) {
        dsl.execute(
                "UPDATE control.ml_analysis_run SET status = 'FAILED', completed_at = now(), error = ?, "
                        + "lease_owner = NULL, lease_expires_at = NULL WHERE analysis_run_key = ?",
                error, java.util.UUID.fromString(key));
    }

    /** Cancels a queued run; a run already RUNNING is not interrupted (returns false). */
    public boolean cancel(String key) {
        int updated = dsl.execute(
                "UPDATE control.ml_analysis_run SET status = 'CANCELLED', completed_at = now() "
                        + "WHERE analysis_run_key = ? AND status = 'QUEUED'",
                java.util.UUID.fromString(key));
        return updated > 0;
    }

    private static Run map(Record record) {
        return new Run(
                record.get("analysis_run_id", Long.class),
                record.get("analysis_run_key", java.util.UUID.class).toString(),
                record.get("status", String.class),
                record.get("requested_by", String.class),
                record.get("config", String.class),
                record.get("progress", String.class),
                record.get("metrics", String.class),
                record.get("model_version_id", Long.class),
                record.get("error", String.class),
                record.get("created_at", Instant.class),
                record.get("started_at", Instant.class),
                record.get("completed_at", Instant.class));
    }
}
