package com.edgerelative.application.catalog.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Raw persistence for the strategy catalog. Version rows are immutable; only the parent is mutable. */
@Repository
public class StrategyCatalogRepository {

    private final DSLContext dsl;

    public StrategyCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record StrategyVersionRecord(
            long strategyVersionId,
            long strategyId,
            int version,
            String lifecycleState,
            Long primaryTimeframeId,
            String primaryTimeframeCode,
            Long featureSchemaVersionId,
            String parametersJson,
            Instant createdAt) {
    }

    public record StrategyRecord(
            long strategyId,
            String code,
            String name,
            String description,
            String setupFamily,
            String status,
            Instant retiredAt,
            String retiredReason,
            Instant createdAt,
            List<StrategyVersionRecord> versions) {
    }

    public List<StrategyRecord> list(boolean includeRetired) {
        String where = includeRetired ? "" : "WHERE s.status = 'ACTIVE' ";
        List<Record> rows = dsl.fetch(
                "SELECT s.strategy_id, s.code, s.name, s.description, s.setup_family, s.status, s.retired_at, "
                        + "s.retired_reason, s.created_at, sv.strategy_version_id, sv.version, sv.lifecycle_state, "
                        + "sv.primary_timeframe_id, tf.code AS timeframe_code, sv.feature_schema_version_id, "
                        + "sv.parameters, sv.created_at AS version_created_at "
                        + "FROM control.strategy s "
                        + "LEFT JOIN control.strategy_version sv ON sv.strategy_id = s.strategy_id "
                        + "LEFT JOIN reference.timeframe tf ON tf.timeframe_id = sv.primary_timeframe_id "
                        + where
                        + "ORDER BY s.code, sv.version");
        return group(rows);
    }

    public Optional<StrategyRecord> find(String code) {
        List<Record> rows = dsl.fetch(
                "SELECT s.strategy_id, s.code, s.name, s.description, s.setup_family, s.status, s.retired_at, "
                        + "s.retired_reason, s.created_at, sv.strategy_version_id, sv.version, sv.lifecycle_state, "
                        + "sv.primary_timeframe_id, tf.code AS timeframe_code, sv.feature_schema_version_id, "
                        + "sv.parameters, sv.created_at AS version_created_at "
                        + "FROM control.strategy s "
                        + "LEFT JOIN control.strategy_version sv ON sv.strategy_id = s.strategy_id "
                        + "LEFT JOIN reference.timeframe tf ON tf.timeframe_id = sv.primary_timeframe_id "
                        + "WHERE s.code = ? ORDER BY sv.version",
                code);
        return group(rows).stream().findFirst();
    }

    public Optional<StrategyVersionRecord> findVersion(long strategyVersionId) {
        Record row = dsl.fetchOne(
                "SELECT sv.strategy_version_id, sv.strategy_id, sv.version, sv.lifecycle_state, "
                        + "sv.primary_timeframe_id, tf.code AS timeframe_code, sv.feature_schema_version_id, "
                        + "sv.parameters, sv.created_at AS version_created_at "
                        + "FROM control.strategy_version sv "
                        + "LEFT JOIN reference.timeframe tf ON tf.timeframe_id = sv.primary_timeframe_id "
                        + "WHERE sv.strategy_version_id = ?",
                strategyVersionId);
        return row == null ? Optional.empty() : Optional.of(version(row));
    }

    public String codeForStrategyId(long strategyId) {
        Record row = dsl.fetchOne("SELECT code FROM control.strategy WHERE strategy_id = ?", strategyId);
        return row == null ? null : row.get("code", String.class);
    }

    public boolean codeExists(String code) {
        return dsl.fetchOne("SELECT 1 FROM control.strategy WHERE code = ?", code) != null;
    }

    public long insertStrategy(String code, String name, String description, String setupFamily) {
        return dsl.fetchOne(
                        "INSERT INTO control.strategy (strategy_key, code, name, description, setup_family) "
                                + "VALUES (?, ?, ?, ?, ?) RETURNING strategy_id",
                        UUID.nameUUIDFromBytes(("strategy:" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        code, name, description, setupFamily)
                .get("strategy_id", Long.class);
    }

    public int nextVersion(long strategyId) {
        Integer max = dsl.fetchOne(
                        "SELECT COALESCE(MAX(version), 0) AS max_version FROM control.strategy_version WHERE strategy_id = ?",
                        strategyId)
                .get("max_version", Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    public long insertVersion(
            long strategyId,
            int version,
            String lifecycleState,
            Long primaryTimeframeId,
            Long featureSchemaVersionId,
            String parametersJson) {
        return dsl.fetchOne(
                        "INSERT INTO control.strategy_version (strategy_id, version, lifecycle_state, "
                                + "primary_timeframe_id, feature_schema_version_id, parameters) "
                                + "VALUES (?, ?, ?, ?, ?, ?::jsonb) RETURNING strategy_version_id",
                        strategyId, version, lifecycleState, primaryTimeframeId, featureSchemaVersionId, parametersJson)
                .get("strategy_version_id", Long.class);
    }

    public void setStatus(String code, String status, String reason) {
        dsl.execute(
                "UPDATE control.strategy SET status = ?, retired_at = "
                        + (("RETIRED".equals(status)) ? "CURRENT_TIMESTAMP" : "NULL") + ", retired_reason = ? WHERE code = ?",
                status, reason, code);
    }

    public Long timeframeId(String code) {
        Record row = dsl.fetchOne("SELECT timeframe_id FROM reference.timeframe WHERE code = ?", code);
        return row == null ? null : row.get("timeframe_id", Long.class);
    }

    public Long latestFeatureSchemaVersionId() {
        Record row = dsl.fetchOne(
                "SELECT fsv.feature_schema_version_id FROM control.feature_schema fs "
                        + "JOIN control.feature_schema_version fsv ON fsv.feature_schema_id = fs.feature_schema_id "
                        + "ORDER BY fsv.version DESC LIMIT 1");
        return row == null ? null : row.get("feature_schema_version_id", Long.class);
    }

    private List<StrategyRecord> group(List<Record> rows) {
        List<StrategyRecord> result = new ArrayList<>();
        Long currentId = null;
        List<StrategyVersionRecord> versions = new ArrayList<>();
        Record header = null;
        for (Record row : rows) {
            long strategyId = row.get("strategy_id", Long.class);
            if (currentId == null || currentId != strategyId) {
                if (header != null) {
                    result.add(record(header, versions));
                }
                currentId = strategyId;
                header = row;
                versions = new ArrayList<>();
            }
            if (row.get("strategy_version_id", Long.class) != null) {
                versions.add(version(row));
            }
        }
        if (header != null) {
            result.add(record(header, versions));
        }
        return result;
    }

    private StrategyRecord record(Record row, List<StrategyVersionRecord> versions) {
        return new StrategyRecord(
                row.get("strategy_id", Long.class),
                row.get("code", String.class),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("setup_family", String.class),
                row.get("status", String.class),
                instant(row.get("retired_at", OffsetDateTime.class)),
                row.get("retired_reason", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                List.copyOf(versions));
    }

    private StrategyVersionRecord version(Record row) {
        return new StrategyVersionRecord(
                row.get("strategy_version_id", Long.class),
                row.get("strategy_id", Long.class),
                row.get("version", Integer.class),
                row.get("lifecycle_state", String.class),
                row.get("primary_timeframe_id", Long.class),
                row.get("timeframe_code", String.class),
                row.get("feature_schema_version_id", Long.class),
                row.get("parameters", String.class),
                instant(row.get("version_created_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
