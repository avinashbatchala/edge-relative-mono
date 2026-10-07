package com.edgerelative.application.ml.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GbmModelEvaluatorTest {

    private final GbmModelEvaluator evaluator = new GbmModelEvaluator();

    private static GbmModelArtifact.Tree stump(String feature, double threshold, double left, double right, int missing) {
        return new GbmModelArtifact.Tree(List.of(
                new GbmModelArtifact.Node(feature, threshold, 1, 2, missing, null),
                new GbmModelArtifact.Node(null, null, null, null, null, left),
                new GbmModelArtifact.Node(null, null, null, null, null, right)));
    }

    @Test
    void sumsLeafValuesPlusBaseScore() {
        GbmModelArtifact model = new GbmModelArtifact(
                "er-gbm-v1", "regression", 0.25, List.of("RRS_RAW"),
                List.of(stump("RRS_RAW", 0.0, -0.1, 0.3, 2), stump("RRS_RAW", 0.0, -0.2, 0.4, 2)));

        double positive = evaluator.score(model, MlFeatureVector.builder().numeric("RRS_RAW", 1.0).build());
        double negative = evaluator.score(model, MlFeatureVector.builder().numeric("RRS_RAW", -1.0).build());

        assertThat(positive).isEqualTo(0.25 + 0.3 + 0.4);
        assertThat(negative).isEqualTo(0.25 - 0.1 - 0.2);
    }

    @Test
    void missingFeatureFollowsTheStoredDefaultDirection() {
        GbmModelArtifact model = new GbmModelArtifact(
                "er-gbm-v1", "regression", 0.0, List.of("RRS_RAW"),
                List.of(stump("RRS_RAW", 0.0, -0.1, 0.3, 2)));

        double score = evaluator.score(model, MlFeatureVector.builder().build());

        assertThat(score).isEqualTo(0.3);
    }

    @Test
    void emptyModelReturnsBaseScore() {
        GbmModelArtifact model = new GbmModelArtifact("er-gbm-v1", "regression", 1.5, List.of(), List.of());
        assertThat(evaluator.score(model, new MlFeatureVector(Map.of()))).isEqualTo(1.5);
    }
}
