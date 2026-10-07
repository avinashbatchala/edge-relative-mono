"""Typed inputs and outputs for the valuation operators.

Money and rates are :class:`decimal.Decimal`. Models are frozen and validating; an
invalid input fails loudly rather than producing a plausible-looking number.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _finite(value: Decimal, name: str) -> None:
    _require(value.is_finite(), f"{name} must be finite")


def _in_unit_interval(value: Decimal, name: str) -> None:
    _finite(value, name)
    _require(Decimal(0) <= value <= Decimal(1), f"{name} must be within [0, 1]")


@dataclass(frozen=True, slots=True)
class WaccInputs:
    risk_free_rate: Decimal
    equity_risk_premium: Decimal
    beta: Decimal
    cost_of_debt: Decimal
    tax_rate: Decimal
    equity_value: Decimal
    debt_value: Decimal

    def __post_init__(self) -> None:
        for name in (
            "risk_free_rate",
            "equity_risk_premium",
            "beta",
            "cost_of_debt",
            "tax_rate",
            "equity_value",
            "debt_value",
        ):
            _finite(getattr(self, name), name)
        _in_unit_interval(self.tax_rate, "tax_rate")
        _require(self.equity_value >= 0, "equity_value must not be negative")
        _require(self.debt_value >= 0, "debt_value must not be negative")
        _require(
            self.equity_value + self.debt_value > 0,
            "equity_value + debt_value must be positive",
        )


@dataclass(frozen=True, slots=True)
class WaccResult:
    cost_of_equity: Decimal
    after_tax_cost_of_debt: Decimal
    equity_weight: Decimal
    debt_weight: Decimal
    wacc: Decimal
    version: str


@dataclass(frozen=True, slots=True)
class DcfAssumptions:
    base_free_cash_flow: Decimal
    growth_rate: Decimal
    terminal_growth_rate: Decimal
    discount_rate: Decimal
    years: int

    def __post_init__(self) -> None:
        for name in ("base_free_cash_flow", "growth_rate", "terminal_growth_rate", "discount_rate"):
            _finite(getattr(self, name), name)
        _require(self.years >= 1, "years must be >= 1")
        _require(self.growth_rate > Decimal(-1), "growth_rate must be > -1")
        _require(
            self.discount_rate > self.terminal_growth_rate,
            "discount_rate must exceed terminal_growth_rate",
        )


@dataclass(frozen=True, slots=True)
class DcfResult:
    present_value_explicit: Decimal
    terminal_value: Decimal
    present_value_terminal: Decimal
    enterprise_value: Decimal
    version: str


@dataclass(frozen=True, slots=True)
class CompsResult:
    peer_multiple: Decimal
    implied_equity_value: Decimal
    version: str
