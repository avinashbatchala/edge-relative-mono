package com.edgerelative.application.ml.domain;

/**
 * Pure-Java evaluator for a frozen {@link GbmModelArtifact}. It performs no I/O and no lookup; the
 * same artifact and vector always yield the same score. Missing features follow the node's stored
 * default direction rather than being treated as zero.
 */
public final class GbmModelEvaluator {

    private static final int MAX_DEPTH = 10_000;

    public double score(GbmModelArtifact model, MlFeatureVector vector) {
        double sum = model.baseScore();
        for (GbmModelArtifact.Tree tree : model.trees()) {
            sum += evaluate(tree, vector);
        }
        return sum;
    }

    private double evaluate(GbmModelArtifact.Tree tree, MlFeatureVector vector) {
        java.util.List<GbmModelArtifact.Node> nodes = tree.nodes();
        if (nodes.isEmpty()) {
            return 0.0;
        }
        int index = 0;
        for (int guard = 0; guard < MAX_DEPTH; guard++) {
            GbmModelArtifact.Node node = nodes.get(index);
            Double leaf = node.leaf();
            if (leaf != null) {
                return leaf;
            }
            Double value = vector.values().get(node.feature());
            int next;
            if (value == null) {
                next = node.missing() != null ? node.missing()
                        : (node.right() != null ? node.right() : node.left());
            } else if (node.threshold() != null && value <= node.threshold()) {
                next = node.left();
            } else {
                next = node.right();
            }
            if (next < 0 || next >= nodes.size()) {
                throw new IllegalStateException("model node points outside its tree");
            }
            index = next;
        }
        throw new IllegalStateException("model tree exceeds the maximum evaluable depth");
    }
}
