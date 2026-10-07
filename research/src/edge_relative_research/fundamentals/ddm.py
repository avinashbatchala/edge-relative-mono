"""Dividend discount model (Gordon growth)."""

from __future__ import annotations

from decimal import Decimal


def gordon_growth(
    dividend_next_year: Decimal, required_return: Decimal, growth_rate: Decimal
) -> Decimal:
    """Constant-growth dividend discount value ``D1 / (r - g)``."""

    for name, value in (
        ("dividend_next_year", dividend_next_year),
        ("required_return", required_return),
        ("growth_rate", growth_rate),
    ):
        if not value.is_finite():
            raise ValueError(f"{name} must be finite")
    if required_return <= growth_rate:
        raise ValueError("required_return must exceed growth_rate")
    return dividend_next_year / (required_return - growth_rate)
