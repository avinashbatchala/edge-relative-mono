"""Simple fundamental ratios.

These are thin, deterministic helpers over normalised fundamentals. They are advisory
context and never feed setup qualification, sizing, or risk.
"""

from __future__ import annotations

from decimal import Decimal


def _ratio(numerator: Decimal, denominator: Decimal, name: str) -> Decimal:
    if not numerator.is_finite() or not denominator.is_finite():
        raise ValueError(f"{name} inputs must be finite")
    if denominator == 0:
        raise ValueError(f"{name} denominator must not be zero")
    return numerator / denominator


def net_margin(net_income: Decimal, revenue: Decimal) -> Decimal:
    return _ratio(net_income, revenue, "net_margin")


def return_on_equity(net_income: Decimal, equity: Decimal) -> Decimal:
    return _ratio(net_income, equity, "return_on_equity")


def debt_to_equity(total_debt: Decimal, equity: Decimal) -> Decimal:
    return _ratio(total_debt, equity, "debt_to_equity")


def price_to_earnings(price: Decimal, earnings_per_share: Decimal) -> Decimal:
    return _ratio(price, earnings_per_share, "price_to_earnings")


def price_to_book(price: Decimal, book_value_per_share: Decimal) -> Decimal:
    return _ratio(price, book_value_per_share, "price_to_book")


def revenue_growth(current_revenue: Decimal, prior_revenue: Decimal) -> Decimal:
    """Year-over-year revenue growth."""
    return _ratio(current_revenue - prior_revenue, prior_revenue, "revenue_growth")
