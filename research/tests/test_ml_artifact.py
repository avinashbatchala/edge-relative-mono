"""Frozen artifact format and evaluator parity (dependency-free)."""

from edge_relative_research.ml.artifact import GbmArtifact, evaluate

MODEL = {
    "format": "er-gbm-v1",
    "objective": "regression",
    "baseScore": 0.25,
    "features": ["RRS_RAW"],
    "trees": [
        {
            "nodes": [
                {"feature": "RRS_RAW", "threshold": 0.0, "left": 1, "right": 2, "missing": 2},
                {"leaf": -0.1},
                {"leaf": 0.3},
            ]
        },
        {
            "nodes": [
                {"feature": "RRS_RAW", "threshold": 0.5, "left": 1, "right": 2, "missing": 2},
                {"leaf": -0.2},
                {"leaf": 0.4},
            ]
        },
    ],
}


def test_sums_leaf_values_plus_base_score():
    artifact = GbmArtifact.from_dict(MODEL)
    assert evaluate(artifact, {"RRS_RAW": 1.0}) == 0.25 + 0.3 + 0.4
    assert evaluate(artifact, {"RRS_RAW": -1.0}) == 0.25 - 0.1 - 0.2


def test_missing_feature_follows_stored_default():
    artifact = GbmArtifact.from_dict(MODEL)
    # Both trees default missing to the right child (0.3 and 0.4).
    assert evaluate(artifact, {}) == 0.25 + 0.3 + 0.4


def test_round_trips_through_dict():
    artifact = GbmArtifact.from_dict(MODEL)
    assert GbmArtifact.from_dict(artifact.to_dict()) == artifact
