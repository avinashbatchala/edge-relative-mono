"""Deterministic valuation operator tests (DD-06)."""

from decimal import Decimal

import pytest

from edge_relative_research.fundamentals import (
    compute_wacc,
    debt_to_equity,
    discounted_cash_flow,
    gordon_growth,
    implied_equity_value,
    multiple,
    net_margin,
    peer_median,
    price_to_book,
    price_to_earnings,
    return_on_equity,
    revenue_growth,
)
from edge_relative_research.fundamentals.models import DcfAssumptions, WaccInputs


def _wacc_inputs(**overrides: str) -> WaccInputs:
    values = {
        "risk_free_rate": "0.07",
        "equity_risk_premium": "0.06",
        "beta": "1.0",
        "cost_of_debt": "0.08",
        "tax_rate": "0.25",
        "equity_value": "600",
        "debt_value": "400",
    }
    values.update(overrides)
    return WaccInputs(**{key: Decimal(value) for key, value in values.items()})


def test_wacc_matches_hand_computation() -> None:
    result = compute_wacc(_wacc_inputs())

    assert result.cost_of_equity == Decimal("0.13")
    assert result.after_tax_cost_of_debt == Decimal("0.06")
    assert result.equity_weight == Decimal("0.6")
    assert result.debt_weight == Decimal("0.4")
    assert result.wacc == Decimal("0.102")
    assert result.version == "er-wacc-v1"


def test_dcf_matches_hand_computation() -> None:
    result = discounted_cash_flow(
        DcfAssumptions(
            base_free_cash_flow=Decimal("100"),
            growth_rate=Decimal("0.10"),
            terminal_growth_rate=Decimal("0.04"),
            discount_rate=Decimal("0.12"),
            years=3,
        )
    )

    assert round(result.present_value_explicit, 8) == Decimal("289.41269588")
    assert round(result.terminal_value, 8) == Decimal("1730.30000000")
    assert round(result.enterprise_value, 8) == Decimal("1521.00605867")
    assert result.version == "er-dcf-v1"


def test_gordon_growth() -> None:
    assert gordon_growth(Decimal("10"), Decimal("0.12"), Decimal("0.04")) == Decimal("125")


def test_comps_median_and_implied_value() -> None:
    assert peer_median([Decimal("10"), Decimal("12"), Decimal("100")]) == Decimal("12")

    result = implied_equity_value(Decimal("100"), Decimal("12"))
    assert result.implied_equity_value == Decimal("1200")
    assert result.version == "er-comps-v1"

    assert multiple(Decimal("100"), Decimal("5")) == Decimal("20")


def test_simple_ratios() -> None:
    assert net_margin(Decimal("20"), Decimal("100")) == Decimal("0.2")
    assert return_on_equity(Decimal("20"), Decimal("200")) == Decimal("0.1")
    assert debt_to_equity(Decimal("50"), Decimal("200")) == Decimal("0.25")
    assert price_to_earnings(Decimal("100"), Decimal("5")) == Decimal("20")
    assert price_to_book(Decimal("100"), Decimal("25")) == Decimal("4")
    assert revenue_growth(Decimal("110"), Decimal("100")) == Decimal("0.1")


def test_dcf_rejects_discount_not_above_terminal_growth() -> None:
    with pytest.raises(ValueError):
        DcfAssumptions(
            base_free_cash_flow=Decimal("100"),
            growth_rate=Decimal("0.10"),
            terminal_growth_rate=Decimal("0.05"),
            discount_rate=Decimal("0.05"),
            years=3,
        )


def test_wacc_rejects_zero_capital() -> None:
    with pytest.raises(ValueError):
        _wacc_inputs(equity_value="0", debt_value="0")


def test_ratios_reject_zero_denominator() -> None:
    with pytest.raises(ValueError):
        price_to_earnings(Decimal("100"), Decimal("0"))
