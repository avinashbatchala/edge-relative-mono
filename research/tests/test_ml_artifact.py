"""Frozen artifact format and evaluator parity (dependency-free)."""

from edge_relative_research.ml.artifact import GbmArtifact, evaluate, from_lightgbm

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


class _FakeBooster:
    def __init__(self, dump):
        self._dump = dump

    def dump_model(self):
        return self._dump


def test_from_lightgbm_maps_split_index_to_feature_name():
    dump = {
        "average_output": 0.5,
        "tree_info": [
            {
                "tree_structure": {
                    "split_feature": 1,
                    "threshold": 0.0,
                    "decision_type": "<=",
                    "default_left": True,
                    "left_child": {"leaf_value": -0.1},
                    "right_child": {"leaf_value": 0.3},
                }
            }
        ],
    }
    artifact = from_lightgbm(_FakeBooster(dump), ["ATR", "RRS_RAW"])
    assert artifact.base_score == 0.5
    assert artifact.trees[0].nodes[0].feature == "RRS_RAW"
    assert evaluate(artifact, {"RRS_RAW": 1.0}) == 0.5 + 0.3
    assert evaluate(artifact, {"RRS_RAW": -1.0}) == 0.5 - 0.1


def test_from_lightgbm_rejects_categorical_splits():
    dump = {
        "tree_info": [
            {
                "tree_structure": {
                    "split_feature": 0,
                    "threshold": "1||2",
                    "decision_type": "==",
                    "default_left": True,
                    "left_child": {"leaf_value": -0.1},
                    "right_child": {"leaf_value": 0.3},
                }
            }
        ]
    }
    try:
        from_lightgbm(_FakeBooster(dump), ["SECTOR"])
    except ValueError as error:
        assert "numeric" in str(error)
    else:
        raise AssertionError("categorical splits must be rejected")
