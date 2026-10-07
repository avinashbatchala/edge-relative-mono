package com.edgerelative.application.ml.domain;

import java.util.List;

/**
 * A frozen gradient-boosted-tree model ({@code er-gbm-v1}), the portable artifact the Python trainer
 * exports and the Java backend evaluates. Trees are numeric-only (categorical inputs are one-hot
 * encoded upstream), so a split is a single {@code value <= threshold} test with an explicit
 * missing-value direction. {@code baseScore} plus the sum of leaf values is the raw prediction.
 *
 * <p>The format is intentionally small and dependency-free so scoring stays in-process and
 * deterministic; a golden fixture pins Java/Python parity.
 */
public record GbmModelArtifact(
        String format,
        String objective,
        double baseScore,
        List<String> features,
        List<Tree> trees) {

    public GbmModelArtifact {
        features = features == null ? List.of() : List.copyOf(features);
        trees = trees == null ? List.of() : List.copyOf(trees);
    }

    public record Tree(List<Node> nodes) {
        public Tree {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    /**
     * A tree node: either a leaf ({@code leaf} non-null) or a numeric split on {@code feature}. When
     * the feature is missing, {@code missing} selects the child; otherwise {@code value <= threshold}
     * selects {@code left}.
     */
    public record Node(
            String feature,
            Double threshold,
            Integer left,
            Integer right,
            Integer missing,
            Double leaf) {
    }
}
