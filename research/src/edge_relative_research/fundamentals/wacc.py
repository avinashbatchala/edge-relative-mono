"""Weighted average cost of capital (CAPM cost of equity, after-tax cost of debt)."""

from __future__ import annotations

from decimal import Decimal

from .models import WaccInputs, WaccResult
from .versions import WACC_VERSION


def compute_wacc(inputs: WaccInputs) -> WaccResult:
    """Compute WACC from CAPM cost of equity and after-tax cost of debt.

    ``cost_of_equity = risk_free_rate + beta * equity_risk_premium``
    ``after_tax_cost_of_debt = cost_of_debt * (1 - tax_rate)``
    ``wacc = equity_weight * cost_of_equity + debt_weight * after_tax_cost_of_debt``
    """

    cost_of_equity = inputs.risk_free_rate + inputs.beta * inputs.equity_risk_premium
    after_tax_cost_of_debt = inputs.cost_of_debt * (Decimal(1) - inputs.tax_rate)
    total = inputs.equity_value + inputs.debt_value
    equity_weight = inputs.equity_value / total
    debt_weight = inputs.debt_value / total
    wacc = equity_weight * cost_of_equity + debt_weight * after_tax_cost_of_debt
    return WaccResult(
        cost_of_equity=cost_of_equity,
        after_tax_cost_of_debt=after_tax_cost_of_debt,
        equity_weight=equity_weight,
        debt_weight=debt_weight,
        wacc=wacc,
        version=WACC_VERSION,
    )
