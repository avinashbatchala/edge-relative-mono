"""Comparable-company multiples."""

from __future__ import annotations

from decimal import Decimal
from statistics import median

from .models import CompsResult
from .versions import COMPS_VERSION


def multiple(price: Decimal, metric: Decimal) -> Decimal:
    """A price multiple, rejecting a zero or non-finite denominator."""

    if not price.is_finite() or not metric.is_finite():
        raise ValueError("price and metric must be finite")
    if metric == 0:
        raise ValueError("metric must not be zero")
    return price / metric


def peer_median(multiples: list[Decimal]) -> Decimal:
    """Median peer multiple. An empty set is not a number."""

    if not multiples:
        raise ValueError("at least one peer multiple is required")
    return Decimal(median(multiples))


def implied_equity_value(fundamental_metric: Decimal, peer_multiple: Decimal) -> CompsResult:
    """Apply a peer multiple to a fundamental metric to imply a value."""

    if not fundamental_metric.is_finite() or not peer_multiple.is_finite():
        raise ValueError("fundamental_metric and peer_multiple must be finite")
    return CompsResult(
        peer_multiple=peer_multiple,
        implied_equity_value=fundamental_metric * peer_multiple,
        version=COMPS_VERSION,
    )
