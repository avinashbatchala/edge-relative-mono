"""Ranking metric behaviour."""

from edge_relative_research.ml.evaluate import (
    Group,
    grade,
    mean_ndcg_at_k,
    mean_spearman_ic,
    ndcg_at_k,
    spearman_ic,
)


def test_grade_buckets_realized_r():
    assert grade(-2.0) == 0
    assert grade(-0.5) == 1
    assert grade(0.5) == 2
    assert grade(2.0) == 3


def test_spearman_perfect_and_inverse():
    assert spearman_ic([1, 2, 3], [10, 20, 30]) == 1.0
    assert spearman_ic([1, 2, 3], [30, 20, 10]) == -1.0


def test_ndcg_prefers_correct_ordering():
    labels = [2.0, 1.0, -1.0]
    good = ndcg_at_k([3.0, 2.0, 1.0], labels, k=3)
    bad = ndcg_at_k([1.0, 2.0, 3.0], labels, k=3)
    assert good == 1.0
    assert bad is not None and bad < good


def test_means_skip_undefined_groups():
    groups = [Group((1.0, 2.0), (1.0, 2.0)), Group((1.0,), (1.0,))]
    ic, count = mean_spearman_ic(groups)
    assert ic == 1.0
    assert count == 1
    ndcg, ndcg_count = mean_ndcg_at_k(groups, 3)
    assert ndcg is not None
    assert ndcg_count == 2
