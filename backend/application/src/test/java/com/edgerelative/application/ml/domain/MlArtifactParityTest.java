package com.edgerelative.application.ml.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cross-language parity: the Java evaluator must reproduce the frozen {@code er-gbm-v1} predictions in
 * the shared fixture that the Python reference also evaluates. This pins the artifact format, not the
 * LightGBM export (which self-checks at training time).
 */
class MlArtifactParityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void javaEvaluatorMatchesSharedGoldenFixture() throws Exception {
        JsonNode fixture = JSON.readTree(Files.readString(fixture()));
        GbmModelArtifact model = JSON.treeToValue(fixture.get("model"), GbmModelArtifact.class);
        GbmModelEvaluator evaluator = new GbmModelEvaluator();

        assertThat(fixture.get("cases").isArray()).isTrue();
        for (JsonNode testCase : fixture.get("cases")) {
            Map<String, Double> values = new LinkedHashMap<>();
            testCase.get("features").properties().forEach(entry ->
                    values.put(entry.getKey(), entry.getValue().asDouble()));
            double expected = testCase.get("expected").asDouble();
            double actual = evaluator.score(model, new MlFeatureVector(values));
            assertThat(actual).as("case %s", values).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    private static Path fixture() {
        Path working = Path.of(System.getProperty("user.dir"));
        for (Path candidate = working; candidate != null; candidate = candidate.getParent()) {
            Path path = candidate.resolve("contracts").resolve("fixtures").resolve("ml").resolve("gbm_parity.json");
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException("contracts/fixtures/ml/gbm_parity.json not found from " + working);
    }
}
