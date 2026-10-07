package com.edgerelative.application.catalog.application;

import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps a {@code control.strategy_version.parameters} JSONB document to the typed
 * {@link StrategyParameters}. {@code parameterSetId} and {@code parameterVersion} are always taken
 * from the strategy code/version so lineage cannot be mis-stated by the payload. Construction of
 * {@link StrategyParameters} performs the real validation (unknown family, missing/bad value).
 */
@Component
public class StrategyParametersJson {

    private final JsonMapper json;

    public StrategyParametersJson(JsonMapper json) {
        this.json = json;
    }

    public StrategyParameters read(String parametersJson, String code, int version) {
        ObjectNode node = parametersJson == null || parametersJson.isBlank()
                ? json.createObjectNode()
                : (ObjectNode) json.readTree(parametersJson);
        return read(node, code, version);
    }

    public StrategyParameters read(Map<String, Object> parameters, String code, int version) {
        ObjectNode node = (ObjectNode) json.readTree(json.writeValueAsString(parameters == null ? Map.of() : parameters));
        return read(node, code, version);
    }

    /**
     * Parse a fully-formed canonical parameter document (e.g. a persisted per-instrument binding),
     * keeping its own {@code parameterSetId}/{@code parameterVersion} rather than forcing lineage.
     */
    public StrategyParameters readCanonical(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            throw new IllegalArgumentException("strategy parameters document is empty");
        }
        return json.readValue(parametersJson, StrategyParameters.class);
    }

    private StrategyParameters read(ObjectNode node, String code, int version) {
        node.put("parameterSetId", code);
        node.put("parameterVersion", version);
        return json.treeToValue(node, StrategyParameters.class);
    }

    public String write(StrategyParameters parameters) {
        return json.writeValueAsString(parameters);
    }
}
