package com.edgerelative.application.ml.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Model registry access over the existing {@code control.model} / {@code control.model_version}
 * tables. Artifacts are referenced by URI + checksum; the frozen JSON tree dump itself lives on disk.
 */
@Repository
public class MlModelRegistryRepository {

    private final DSLContext dsl;

    public MlModelRegistryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record ModelVersion(
            long modelVersionId,
            long modelId,
            String code,
            String name,
            int version,
            String lifecycleState,
            String algorithm,
            String artifactUri,
            String artifactChecksum,
            LocalDate trainingPeriodStart,
            LocalDate trainingPeriodEnd,
            LocalDate validationPeriodStart,
            LocalDate validationPeriodEnd,
            LocalDate testPeriodStart,
            LocalDate testPeriodEnd,
            String metricsJson,
            String strategyCompatibilityJson,
            Instant createdAt) {
    }

    public long ensureModel(String code, String name, String description) {
        Record record = dsl.fetchOne(
                "INSERT INTO control.model (model_key, code, name, description) VALUES (gen_random_uuid(), ?, ?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description "
                        + "RETURNING model_id",
                code, name, description);
        return record.get("model_id", Long.class);
    }

    public int nextVersion(long modelId) {
        Record record = dsl.fetchOne(
                "SELECT COALESCE(MAX(version), 0) + 1 AS next FROM control.model_version WHERE model_id = ?",
                modelId);
        return record.get("next", Integer.class);
    }

    public long insertVersion(
            long modelId,
            int version,
            String lifecycleState,
            String algorithm,
            String artifactUri,
            String artifactChecksum,
            LocalDate trainingPeriodStart,
            LocalDate trainingPeriodEnd,
            LocalDate validationPeriodStart,
            LocalDate validationPeriodEnd,
            LocalDate testPeriodStart,
            LocalDate testPeriodEnd,
            String metricsJson,
            String strategyCompatibilityJson,
            String codeVersion) {
        Record record = dsl.fetchOne(
                "INSERT INTO control.model_version (model_id, version, lifecycle_state, algorithm, artifact_uri, "
                        + "artifact_checksum, training_period_start, training_period_end, validation_period_start, "
                        + "validation_period_end, test_period_start, test_period_end, metrics, strategy_compatibility, "
                        + "code_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?) "
                        + "RETURNING model_version_id",
                modelId, version, lifecycleState, algorithm, artifactUri, artifactChecksum,
                trainingPeriodStart, trainingPeriodEnd, validationPeriodStart, validationPeriodEnd,
                testPeriodStart, testPeriodEnd, metricsJson, strategyCompatibilityJson, codeVersion);
        return record.get("model_version_id", Long.class);
    }

    public void updateLifecycle(long modelVersionId, String lifecycleState) {
        dsl.execute(
                "UPDATE control.model_version SET lifecycle_state = ? WHERE model_version_id = ?",
                lifecycleState, modelVersionId);
    }

    public Optional<ModelVersion> find(long modelVersionId) {
        Record record = dsl.fetchOne(
                "SELECT mv.*, m.code AS model_code, m.name AS model_name FROM control.model_version mv "
                        + "JOIN control.model m ON m.model_id = mv.model_id WHERE mv.model_version_id = ?",
                modelVersionId);
        return record == null ? Optional.empty() : Optional.of(map(record));
    }

    public List<ModelVersion> list() {
        return dsl.fetch(
                        "SELECT mv.*, m.code AS model_code, m.name AS model_name FROM control.model_version mv "
                                + "JOIN control.model m ON m.model_id = mv.model_id ORDER BY mv.created_at DESC")
                .map(MlModelRegistryRepository::map);
    }

    private static ModelVersion map(Record record) {
        return new ModelVersion(
                record.get("model_version_id", Long.class),
                record.get("model_id", Long.class),
                record.get("model_code", String.class),
                record.get("model_name", String.class),
                record.get("version", Integer.class),
                record.get("lifecycle_state", String.class),
                record.get("algorithm", String.class),
                record.get("artifact_uri", String.class),
                record.get("artifact_checksum", String.class),
                record.get("training_period_start", LocalDate.class),
                record.get("training_period_end", LocalDate.class),
                record.get("validation_period_start", LocalDate.class),
                record.get("validation_period_end", LocalDate.class),
                record.get("test_period_start", LocalDate.class),
                record.get("test_period_end", LocalDate.class),
                record.get("metrics", String.class),
                record.get("strategy_compatibility", String.class),
                record.get("created_at", Instant.class));
    }
}
