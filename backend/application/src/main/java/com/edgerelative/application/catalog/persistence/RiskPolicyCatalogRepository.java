package com.edgerelative.application.catalog.persistence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Raw persistence for the risk-policy catalog. Versions are immutable; only the parent is mutable. */
@Repository
public class RiskPolicyCatalogRepository {

    private final DSLContext dsl;

    public RiskPolicyCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record RiskPolicyVersionRecord(
            long riskPolicyVersionId,
            long riskPolicyId,
            int version,
            String lifecycleState,
            String parametersJson,
            String codeVersion,
            Instant createdAt) {
    }

    public record RiskPolicyRecord(
            long riskPolicyId,
            String code,
            String name,
            String description,
            String status,
            Instant retiredAt,
            String retiredReason,
            Instant createdAt,
            List<RiskPolicyVersionRecord> versions) {
    }

    public List<RiskPolicyRecord> list(boolean includeRetired) {
        String where = includeRetired ? "" : "WHERE p.status = 'ACTIVE' ";
        return group(dsl.fetch(
                "SELECT p.risk_policy_id, p.code, p.name, p.description, p.status, p.retired_at, p.retired_reason, "
                        + "p.created_at, v.risk_policy_version_id, v.version, v.lifecycle_state, v.parameters, "
                        + "v.code_version, v.created_at AS version_created_at "
                        + "FROM control.risk_policy p "
                        + "LEFT JOIN control.risk_policy_version v ON v.risk_policy_id = p.risk_policy_id "
                        + where
                        + "ORDER BY p.code, v.version"));
    }

    public Optional<RiskPolicyRecord> find(String code) {
        return group(dsl.fetch(
                        "SELECT p.risk_policy_id, p.code, p.name, p.description, p.status, p.retired_at, p.retired_reason, "
                                + "p.created_at, v.risk_policy_version_id, v.version, v.lifecycle_state, v.parameters, "
                                + "v.code_version, v.created_at AS version_created_at "
                                + "FROM control.risk_policy p "
                                + "LEFT JOIN control.risk_policy_version v ON v.risk_policy_id = p.risk_policy_id "
                                + "WHERE p.code = ? ORDER BY v.version",
                        code))
                .stream()
                .findFirst();
    }

    public Optional<RiskPolicyVersionRecord> findVersion(long riskPolicyVersionId) {
        Record row = dsl.fetchOne(
                "SELECT risk_policy_version_id, risk_policy_id, version, lifecycle_state, parameters, code_version, "
                        + "created_at AS version_created_at FROM control.risk_policy_version "
                        + "WHERE risk_policy_version_id = ?",
                riskPolicyVersionId);
        return row == null ? Optional.empty() : Optional.of(version(row));
    }

    public boolean codeExists(String code) {
        return dsl.fetchOne("SELECT 1 FROM control.risk_policy WHERE code = ?", code) != null;
    }

    public long insertPolicy(String code, String name, String description) {
        return dsl.fetchOne(
                        "INSERT INTO control.risk_policy (risk_policy_key, code, name, description) "
                                + "VALUES (?, ?, ?, ?) RETURNING risk_policy_id",
                        UUID.nameUUIDFromBytes(("risk-policy:" + code).getBytes(StandardCharsets.UTF_8)),
                        code, name, description)
                .get("risk_policy_id", Long.class);
    }

    public int nextVersion(long riskPolicyId) {
        Integer max = dsl.fetchOne(
                        "SELECT COALESCE(MAX(version), 0) AS max_version FROM control.risk_policy_version WHERE risk_policy_id = ?",
                        riskPolicyId)
                .get("max_version", Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    public long insertVersion(long riskPolicyId, int version, String lifecycleState, String parametersJson) {
        return dsl.fetchOne(
                        "INSERT INTO control.risk_policy_version (risk_policy_id, version, lifecycle_state, parameters) "
                                + "VALUES (?, ?, ?, ?::jsonb) RETURNING risk_policy_version_id",
                        riskPolicyId, version, lifecycleState, parametersJson)
                .get("risk_policy_version_id", Long.class);
    }

    public void setStatus(String code, String status, String reason) {
        dsl.execute(
                "UPDATE control.risk_policy SET status = ?, retired_at = "
                        + (("RETIRED".equals(status)) ? "CURRENT_TIMESTAMP" : "NULL") + ", retired_reason = ? WHERE code = ?",
                status, reason, code);
    }

    private List<RiskPolicyRecord> group(List<Record> rows) {
        List<RiskPolicyRecord> result = new ArrayList<>();
        Long currentId = null;
        List<RiskPolicyVersionRecord> versions = new ArrayList<>();
        Record header = null;
        for (Record row : rows) {
            long id = row.get("risk_policy_id", Long.class);
            if (currentId == null || currentId != id) {
                if (header != null) {
                    result.add(record(header, versions));
                }
                currentId = id;
                header = row;
                versions = new ArrayList<>();
            }
            if (row.get("risk_policy_version_id", Long.class) != null) {
                versions.add(version(row));
            }
        }
        if (header != null) {
            result.add(record(header, versions));
        }
        return result;
    }

    private RiskPolicyRecord record(Record row, List<RiskPolicyVersionRecord> versions) {
        return new RiskPolicyRecord(
                row.get("risk_policy_id", Long.class),
                row.get("code", String.class),
                row.get("name", String.class),
                row.get("description", String.class),
                row.get("status", String.class),
                instant(row.get("retired_at", OffsetDateTime.class)),
                row.get("retired_reason", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                List.copyOf(versions));
    }

    private RiskPolicyVersionRecord version(Record row) {
        return new RiskPolicyVersionRecord(
                row.get("risk_policy_version_id", Long.class),
                row.get("risk_policy_id", Long.class),
                row.get("version", Integer.class),
                row.get("lifecycle_state", String.class),
                row.get("parameters", String.class),
                row.get("code_version", String.class),
                instant(row.get("version_created_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
