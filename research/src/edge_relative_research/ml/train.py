"""Ranker training and walk-forward evaluation.

LightGBM and numpy are imported lazily so the artifact evaluator and metrics remain importable
without the optional ML toolchain. Training is grouped by session; a model is only accepted when its
frozen artifact reproduces the booster's own predictions (see :func:`from_lightgbm`).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping, Sequence

from edge_relative_research.ml.dataset import TrainingMatrix
from edge_relative_research.ml.evaluate import Group, mean_ndcg_at_k, mean_spearman_ic

DEFAULT_PARAMS: dict[str, Any] = {
    "objective": "regression",
    "learning_rate": 0.05,
    "num_leaves": 31,
    "min_data_in_leaf": 20,
    "feature_fraction": 0.8,
    "bagging_fraction": 0.8,
    "bagging_freq": 1,
    "lambda_l2": 1.0,
    "verbose": -1,
}
DEFAULT_ROUNDS = 200


@dataclass
class Split:
    train: list[int]
    validation: list[int]
    out_of_sample: list[int]


def group_ranges(groups: Sequence[str]) -> list[tuple[str, int, int]]:
    """Contiguous (name, start, end) ranges; rows are expected pre-sorted by group."""
    ranges: list[tuple[str, int, int]] = []
    start = 0
    for index in range(1, len(groups) + 1):
        if index == len(groups) or groups[index] != groups[start]:
            ranges.append((groups[start], start, index))
            start = index
    return ranges


def date_split(groups: Sequence[str], fractions: tuple[float, float, float]) -> Split:
    """Split by ordered unique group (session) dates into train/validation/OOS index lists."""
    unique = sorted(set(groups))
    if len(unique) < 3:
        # Not enough sessions to split: train on everything, validate/OOS empty (metrics undefined).
        return Split(train=list(range(len(groups))), validation=[], out_of_sample=[])
    train_end = max(1, int(len(unique) * fractions[0]))
    validation_end = max(train_end + 1, int(len(unique) * (fractions[0] + fractions[1])))
    train_dates = set(unique[:train_end])
    validation_dates = set(unique[train_end:validation_end])
    oos_dates = set(unique[validation_end:])
    return Split(
        train=[i for i, g in enumerate(groups) if g in train_dates],
        validation=[i for i, g in enumerate(groups) if g in validation_dates],
        out_of_sample=[i for i, g in enumerate(groups) if g in oos_dates],
    )


def _matrix(matrix: TrainingMatrix) -> Any:
    import numpy as np

    return np.array(
        [[row.get(name, float("nan")) for name in matrix.feature_names] for row in matrix.features],
        dtype=float,
    )


def train_regressor(
    matrix: TrainingMatrix,
    indices: Sequence[int],
    params: Mapping[str, Any] | None = None,
    rounds: int = DEFAULT_ROUNDS,
    seed: int = 7,
) -> Any:
    import lightgbm as lgb

    import numpy as np

    features = _matrix(matrix)
    labels = np.array([matrix.labels[i] for i in indices], dtype=float)
    data = features[indices]
    merged = dict(DEFAULT_PARAMS if params is None else params)
    merged["seed"] = seed
    merged["feature_fraction_seed"] = seed
    merged["bagging_seed"] = seed
    dataset = lgb.Dataset(data, label=labels, feature_name=list(matrix.feature_names))
    return lgb.train(dict(merged), dataset, num_boost_round=rounds)


def predict(booster: Any, matrix: TrainingMatrix, indices: Sequence[int]) -> list[float]:
    features = _matrix(matrix)
    return [float(value) for value in booster.predict(features[indices])]


def groups_for(matrix: TrainingMatrix, indices: Sequence[int], scores: Sequence[float]) -> list[Group]:
    buckets: dict[str, tuple[list[float], list[float]]] = {}
    for position, index in enumerate(indices):
        name = matrix.groups[index]
        entry = buckets.setdefault(name, ([], []))
        entry[0].append(scores[position])
        entry[1].append(matrix.labels[index])
    return [Group(tuple(scores), tuple(labels)) for scores, labels in buckets.values()]


@dataclass
class Evaluation:
    rank_ic: float | None = None
    ndcg: float | None = None
    groups: int = 0
    details: dict[str, Any] = field(default_factory=dict)


def evaluate_split(
    booster: Any, matrix: TrainingMatrix, indices: Sequence[int], k: int = 3
) -> Evaluation:
    if not indices:
        return Evaluation()
    scores = predict(booster, matrix, indices)
    groups = groups_for(matrix, indices, scores)
    ic, ic_groups = mean_spearman_ic(groups)
    ndcg, ndcg_groups = mean_ndcg_at_k(groups, k)
    return Evaluation(rank_ic=ic, ndcg=ndcg, groups=len(groups), details={"icGroups": ic_groups, "ndcgGroups": ndcg_groups})
