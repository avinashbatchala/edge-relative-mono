"""Frozen gradient-boosted-tree artifact (``er-gbm-v1``).

The artifact is a small, dependency-free JSON document that both the Python trainer and the Java
backend evaluate identically: numeric splits only (categorical inputs are one-hot encoded upstream),
with an explicit missing-value direction per node. A missing feature follows the node's stored
default; it is never coerced to zero.

The Python evaluator here is the reference used to (a) build golden parity fixtures and (b) verify,
inside :func:`from_lightgbm`, that the frozen artifact reproduces the LightGBM booster's own
predictions before the model is registered.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Sequence

FORMAT = "er-gbm-v1"
_MAX_DEPTH = 10_000


@dataclass(frozen=True)
class Node:
    feature: str | None = None
    threshold: float | None = None
    left: int | None = None
    right: int | None = None
    missing: int | None = None
    leaf: float | None = None


@dataclass(frozen=True)
class Tree:
    nodes: tuple[Node, ...]


@dataclass(frozen=True)
class GbmArtifact:
    base_score: float
    trees: tuple[Tree, ...]
    features: tuple[str, ...] = ()
    objective: str = "regression"
    format: str = FORMAT

    def to_dict(self) -> dict[str, Any]:
        def node(value: Node) -> dict[str, Any]:
            if value.leaf is not None:
                return {"leaf": value.leaf}
            return {
                "feature": value.feature,
                "threshold": value.threshold,
                "left": value.left,
                "right": value.right,
                "missing": value.missing,
            }

        return {
            "format": self.format,
            "objective": self.objective,
            "baseScore": self.base_score,
            "features": list(self.features),
            "trees": [{"nodes": [node(n) for n in tree.nodes]} for tree in self.trees],
        }

    @staticmethod
    def from_dict(payload: Mapping[str, Any]) -> GbmArtifact:
        trees: list[Tree] = []
        for tree in payload.get("trees", []):
            nodes = [
                Node(
                    feature=n.get("feature"),
                    threshold=n.get("threshold"),
                    left=n.get("left"),
                    right=n.get("right"),
                    missing=n.get("missing"),
                    leaf=n.get("leaf"),
                )
                for n in tree.get("nodes", [])
            ]
            trees.append(Tree(tuple(nodes)))
        return GbmArtifact(
            base_score=float(payload.get("baseScore", 0.0)),
            trees=tuple(trees),
            features=tuple(payload.get("features", [])),
            objective=payload.get("objective", "regression"),
            format=payload.get("format", FORMAT),
        )


def evaluate(artifact: GbmArtifact, features: Mapping[str, float]) -> float:
    """Score one feature map; mirrors the Java ``GbmModelEvaluator`` exactly."""
    total = artifact.base_score
    for tree in artifact.trees:
        total += _evaluate_tree(tree, features)
    return total


def _evaluate_tree(tree: Tree, features: Mapping[str, float]) -> float:
    nodes = tree.nodes
    if not nodes:
        return 0.0
    index = 0
    for _ in range(_MAX_DEPTH):
        node = nodes[index]
        if node.leaf is not None:
            return node.leaf
        value = features.get(node.feature) if node.feature is not None else None
        if value is None:
            nxt = node.missing if node.missing is not None else node.right
        elif node.threshold is not None and value <= node.threshold:
            nxt = node.left
        else:
            nxt = node.right
        if nxt is None or nxt < 0 or nxt >= len(nodes):
            raise ValueError("artifact node points outside its tree")
        index = nxt
    raise ValueError("artifact tree exceeds the maximum evaluable depth")


def from_lightgbm(
    booster: Any,
    feature_names: Sequence[str],
    objective: str = "regression",
    sample_features: Sequence[Mapping[str, float]] | None = None,
    tolerance: float = 1e-6,
) -> GbmArtifact:
    """Convert a trained LightGBM booster into a frozen artifact.

    LightGBM applies shrinkage when it builds leaves, so ``leaf_value`` values already include the
    learning rate; the base score is LightGBM's init score. When ``sample_features`` is supplied the
    frozen artifact is checked against ``booster.predict`` and a mismatch raises — a model whose
    frozen form does not reproduce LightGBM is never registered.
    """
    dump = booster.dump_model()
    trees: list[Tree] = []
    for tree_info in dump["tree_info"]:
        nodes: list[Node] = []
        _flatten(tree_info["tree_structure"], nodes, list(feature_names))
        trees.append(Tree(tuple(nodes)))
    base_score = _base_score(dump)
    artifact = GbmArtifact(
        base_score=base_score,
        trees=tuple(trees),
        features=tuple(feature_names),
        objective=objective,
    )

    if sample_features:
        matrix = [
            [row.get(name, float("nan")) for name in feature_names] for row in sample_features
        ]
        expected = list(booster.predict(matrix))
        for row, want in zip(sample_features, expected, strict=True):
            got = evaluate(artifact, row)
            if abs(got - float(want)) > tolerance:
                raise ValueError(
                    f"frozen artifact does not reproduce LightGBM: got {got}, expected {want}"
                )
    return artifact


def _flatten(structure: Mapping[str, Any], nodes: list[Node], feature_names: list[str]) -> int:
    """Depth-first flatten of a LightGBM tree structure; returns this node's index.

    LightGBM stores ``decision_type`` as the string ``"<="`` for numeric splits and ``"=="`` for
    categorical ones, and ``split_feature`` as a feature *index*. Only numeric splits are supported;
    categorical inputs must be one-hot encoded upstream (matching the Java evaluator).
    """
    index = len(nodes)
    nodes.append(Node())
    if "leaf_value" in structure:
        nodes[index] = Node(leaf=float(structure["leaf_value"]))
        return index
    decision_type = structure.get("decision_type")
    if decision_type is not None and decision_type != "<=":
        raise ValueError(
            "only numeric '<=' LightGBM splits are supported; one-hot encode categoricals"
        )
    feature_index = int(structure["split_feature"])
    if feature_index < 0 or feature_index >= len(feature_names):
        raise ValueError(f"LightGBM split_feature {feature_index} is outside the feature list")
    default_left = bool(structure.get("default_left", True))
    left_index = _flatten(structure["left_child"], nodes, feature_names)
    right_index = _flatten(structure["right_child"], nodes, feature_names)
    nodes[index] = Node(
        feature=feature_names[feature_index],
        threshold=float(structure["threshold"]),
        left=left_index,
        right=right_index,
        missing=left_index if default_left else right_index,
    )
    return index


def _base_score(dump: Mapping[str, Any]) -> float:
    value = dump.get("average_output")
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0
