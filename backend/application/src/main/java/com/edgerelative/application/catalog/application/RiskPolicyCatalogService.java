package com.edgerelative.application.catalog.application;

import com.edgerelative.application.catalog.persistence.RiskPolicyCatalogRepository;
import com.edgerelative.application.risk.domain.RiskPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catalog management for risk policies. Versions are immutable, so "update" appends a new version and
 * "delete" retires the mutable parent. The existing {@code RiskPolicyRepository} resolves versions.
 */
@Service
public class RiskPolicyCatalogService {

    private static final Set<String> LIFECYCLE_STATES = Set.of(
            "EXPERIMENTAL", "VALIDATED", "PAPER", "LIVE_LIMITED", "PRODUCTION");

    private final RiskPolicyCatalogRepository repository;
    private final JsonMapper json;

    public RiskPolicyCatalogService(RiskPolicyCatalogRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public record RiskPolicyVersionView(
            long riskPolicyVersionId,
            int version,
            String lifecycleState,
            RiskPolicy parameters,
            String parametersError,
            Instant createdAt) {
    }

    public record RiskPolicyView(
            long riskPolicyId,
            String code,
            String name,
            String description,
            String status,
            Instant retiredAt,
            String retiredReason,
            Instant createdAt,
            List<RiskPolicyVersionView> versions) {
    }

    public record CreateRiskPolicyRequest(
            String code, String name, String description, String lifecycleState, Map<String, Object> parameters) {
    }

    public record AddRiskPolicyVersionRequest(String lifecycleState, Map<String, Object> parameters) {
    }

    public List<RiskPolicyView> list(boolean includeRetired) {
        return repository.list(includeRetired).stream().map(this::view).toList();
    }

    public RiskPolicyView get(String code) {
        return repository.find(code).map(this::view)
                .orElseThrow(() -> new CatalogNotFoundException("Risk policy '" + code + "' not found."));
    }

    @Transactional
    public RiskPolicyView create(CreateRiskPolicyRequest request) {
        requireText(request.code(), "Risk policy code is required.");
        requireText(request.name(), "Risk policy name is required.");
        if (repository.codeExists(request.code())) {
            throw new CatalogValidationException("Risk policy code '" + request.code() + "' already exists.");
        }
        String lifecycle = lifecycle(request.lifecycleState());
        long policyId = repository.insertPolicy(request.code(), request.name(), request.description());
        appendVersion(policyId, request.code(), lifecycle, request.parameters(), 1);
        return get(request.code());
    }

    @Transactional
    public RiskPolicyView addVersion(String code, AddRiskPolicyVersionRequest request) {
        var policy = repository.find(code)
                .orElseThrow(() -> new CatalogNotFoundException("Risk policy '" + code + "' not found."));
        if ("RETIRED".equals(policy.status())) {
            throw new CatalogValidationException(
                    "Risk policy '" + code + "' is retired; restore it before adding versions.");
        }
        int version = repository.nextVersion(policy.riskPolicyId());
        appendVersion(policy.riskPolicyId(), code, lifecycle(request.lifecycleState()), request.parameters(), version);
        return get(code);
    }

    @Transactional
    public RiskPolicyView setStatus(String code, String status, String reason) {
        if (!repository.codeExists(code)) {
            throw new CatalogNotFoundException("Risk policy '" + code + "' not found.");
        }
        repository.setStatus(code, status, reason);
        return get(code);
    }

    private void appendVersion(
            long policyId, String code, String lifecycle, Map<String, Object> parameters, int version) {
        validateParameters(parameters, code, version, lifecycle);
        repository.insertVersion(policyId, version, lifecycle, write(parameters));
    }

    private void validateParameters(
            Map<String, Object> parameters, String code, int version, String lifecycle) {
        try {
            ObjectNode node = (ObjectNode) json.readTree(write(parameters));
            node.put("code", code);
            node.put("version", version);
            node.put("lifecycleState", lifecycle);
            json.treeToValue(node, RiskPolicy.class);
        } catch (RuntimeException failure) {
            throw new CatalogValidationException(
                    "Invalid risk policy parameters: "
                            + (failure.getMessage() == null ? "validation failed" : failure.getMessage()));
        }
    }

    private String write(Map<String, Object> parameters) {
        return json.writeValueAsString(parameters == null ? Map.of() : parameters);
    }

    private String lifecycle(String requested) {
        String state = requested == null || requested.isBlank() ? "EXPERIMENTAL" : requested.trim().toUpperCase();
        if (!LIFECYCLE_STATES.contains(state)) {
            throw new CatalogValidationException("Unknown risk-policy lifecycle state '" + requested + "'.");
        }
        return state;
    }

    private RiskPolicyView view(RiskPolicyCatalogRepository.RiskPolicyRecord record) {
        List<RiskPolicyVersionView> versions = record.versions().stream().map(version -> {
            RiskPolicy parsed = null;
            String error = null;
            try {
                ObjectNode node = (ObjectNode) json.readTree(
                        version.parametersJson() == null || version.parametersJson().isBlank()
                                ? "{}" : version.parametersJson());
                node.put("code", record.code());
                node.put("version", version.version());
                node.put("lifecycleState", version.lifecycleState());
                parsed = json.treeToValue(node, RiskPolicy.class);
            } catch (RuntimeException failure) {
                error = failure.getMessage();
            }
            return new RiskPolicyVersionView(
                    version.riskPolicyVersionId(), version.version(), version.lifecycleState(), parsed, error,
                    version.createdAt());
        }).toList();
        return new RiskPolicyView(
                record.riskPolicyId(), record.code(), record.name(), record.description(), record.status(),
                record.retiredAt(), record.retiredReason(), record.createdAt(), versions);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CatalogValidationException(message);
        }
    }
}
