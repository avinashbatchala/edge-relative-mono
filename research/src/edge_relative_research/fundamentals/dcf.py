"""Discounted cash flow valuation.

A single explicit forecast window plus a Gordon terminal value, all in Decimal. The
result is deterministic and versioned; it is never used to qualify a trade.
"""

from __future__ import annotations

from decimal import Decimal

from .models import DcfAssumptions, DcfResult
from .versions import DCF_VERSION


def discounted_cash_flow(assumptions: DcfAssumptions) -> DcfResult:
    """Return the present value of explicit cash flows and the terminal value.

    The explicit window compounds ``base_free_cash_flow`` at ``growth_rate`` for
    ``years``; the terminal value is the next year's cash flow capitalised at
    ``discount_rate - terminal_growth_rate`` and discounted back.
    """

    present_value_explicit = Decimal(0)
    last_explicit_cash_flow = assumptions.base_free_cash_flow
    for year in range(1, assumptions.years + 1):
        cash_flow = assumptions.base_free_cash_flow * (1 + assumptions.growth_rate) ** year
        present_value_explicit += cash_flow / (1 + assumptions.discount_rate) ** year
        last_explicit_cash_flow = cash_flow

    terminal_cash_flow = last_explicit_cash_flow * (1 + assumptions.terminal_growth_rate)
    terminal_value = terminal_cash_flow / (
        assumptions.discount_rate - assumptions.terminal_growth_rate
    )
    present_value_terminal = terminal_value / (1 + assumptions.discount_rate) ** assumptions.years
    enterprise_value = present_value_explicit + present_value_terminal
    return DcfResult(
        present_value_explicit=present_value_explicit,
        terminal_value=terminal_value,
        present_value_terminal=present_value_terminal,
        enterprise_value=enterprise_value,
        version=DCF_VERSION,
    )
