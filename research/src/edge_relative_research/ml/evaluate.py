"""Out-of-sample ranking evaluation.

Ranking quality is measured per group (a session's VALID candidates) and averaged, so the metric
reflects the model's ability to order the options actually on the board. Labels are graded realized
R (from the managed trade); the grading is explicit and never invented from a probability.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence


@dataclass(frozen=True)
class Group:
    """One ranking group: parallel score/label lists."""

    scores: Sequence[float]
    labels: Sequence[float]


def grade(reward_r: float) -> int:
    """Deterministic grading of realized R into a relevance level (0..3)."""
    if reward_r is None:
        return 0
    if reward_r <= -1.0:
        return 0
    if reward_r < 0.0:
        return 1
    if reward_r < 1.0:
        return 2
    return 3


def _average_ranks(values: Sequence[float]) -> list[float]:
    order = sorted(range(len(values)), key=lambda i: values[i])
    ranks = [0.0] * len(values)
    index = 0
    while index < len(order):
        end = index
        while end + 1 < len(order) and values[order[end + 1]] == values[order[index]]:
            end += 1
        average = (index + end) / 2.0 + 1.0
        for position in range(index, end + 1):
            ranks[order[position]] = average
        index = end + 1
    return ranks


def _pearson(left: Sequence[float], right: Sequence[float]) -> float | None:
    n = len(left)
    if n < 2:
        return None
    mean_left = sum(left) / n
    mean_right = sum(right) / n
    cov = sum((left[i] - mean_left) * (right[i] - mean_right) for i in range(n))
    var_left = sum((value - mean_left) ** 2 for value in left)
    var_right = sum((value - mean_right) ** 2 for value in right)
    denominator = (var_left * var_right) ** 0.5
    return None if denominator == 0 else cov / denominator


def spearman_ic(scores: Sequence[float], labels: Sequence[float]) -> float | None:
    """Spearman rank correlation between scores and labels; None when undefined."""
    if len(scores) < 2:
        return None
    return _pearson(_average_ranks(scores), _average_ranks(labels))


def _dcg(relevances: Sequence[int]) -> float:
    total = 0.0
    for rank, relevance in enumerate(relevances, start=1):
        total += (2**relevance - 1) / _log2(rank + 1)
    return total


def _log2(value: float) -> float:
    import math

    return math.log2(value)


def ndcg_at_k(scores: Sequence[float], labels: Sequence[float], k: int) -> float | None:
    """NDCG@k using graded relevance from realized R; None when no relevant item exists."""
    if not scores:
        return None
    relevances = [grade(label) for label in labels]
    ideal = sorted(relevances, reverse=True)
    if sum(ideal) == 0:
        return None
    ranked = [relevances[i] for i in sorted(range(len(scores)), key=lambda i: -scores[i])]
    ideal_dcg = _dcg(ideal[:k])
    if ideal_dcg == 0:
        return None
    return _dcg(ranked[:k]) / ideal_dcg


def _mean(values: list[float]) -> float | None:
    return None if not values else sum(values) / len(values)


def mean_spearman_ic(groups: Sequence[Group]) -> tuple[float | None, int]:
    values = [
        value for group in groups if (value := spearman_ic(group.scores, group.labels)) is not None
    ]
    return _mean(values), len(values)


def mean_ndcg_at_k(groups: Sequence[Group], k: int) -> tuple[float | None, int]:
    values = [
        value for group in groups if (value := ndcg_at_k(group.scores, group.labels, k)) is not None
    ]
    return _mean(values), len(values)
