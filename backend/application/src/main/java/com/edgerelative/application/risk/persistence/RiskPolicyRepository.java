package com.edgerelative.application.risk.persistence;

import com.edgerelative.application.risk.domain.RiskPolicy;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves immutable, versioned risk policy rows. Parameters are stored as JSONB and mapped to the
 * typed {@link RiskPolicy}; no numeric default is invented here — absent JSON fields stay null and
 * the evaluator fails closed.
 */
@Repository
public class RiskPolicyRepository {

    private final DSLContext dsl;
    private final JsonMapper json;

    public RiskPolicyRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    public record ResolvedPolicy(RiskPolicy policy, long riskPolicyVersionId) {
    }

    /** Latest non-retired version for a policy code. */
    public Optional<ResolvedPolicy> resolve(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Record record = dsl.fetchOne(
                "SELECT rpv.risk_policy_version_id, rpv.version, rpv.lifecycle_state, rpv.parameters, rp.code "
                        + "FROM control.risk_policy_version rpv "
                        + "JOIN control.risk_policy rp ON rp.risk_policy_id = rpv.risk_policy_id "
                        + "WHERE rp.code = ? AND rpv.lifecycle_state <> 'RETIRED' "
                        + "ORDER BY rpv.version DESC LIMIT 1",
                code);
        return Optional.ofNullable(record).map(this::map);
    }

    /** Resolves one specific immutable policy version by id (reproducible reference). */
    public Optional<ResolvedPolicy> resolveById(long riskPolicyVersionId) {
        Record record = dsl.fetchOne(
                "SELECT rpv.risk_policy_version_id, rpv.version, rpv.lifecycle_state, rpv.parameters, rp.code "
                        + "FROM control.risk_policy_version rpv "
                        + "JOIN control.risk_policy rp ON rp.risk_policy_id = rpv.risk_policy_id "
                        + "WHERE rpv.risk_policy_version_id = ?",
                riskPolicyVersionId);
        return Optional.ofNullable(record).map(this::map);
    }

    private ResolvedPolicy map(Record record) {
        String policyCode = record.get("code", String.class);
        int version = record.get("version", Integer.class);
        String lifecycle = record.get("lifecycle_state", String.class);
        ObjectNode parameters = (ObjectNode) json.readTree(record.get("parameters", String.class));
        parameters.put("code", policyCode);
        parameters.put("version", version);
        parameters.put("lifecycleState", lifecycle);
        RiskPolicy policy = json.treeToValue(parameters, RiskPolicy.class);
        return new ResolvedPolicy(policy, record.get("risk_policy_version_id", Long.class));
    }
}
