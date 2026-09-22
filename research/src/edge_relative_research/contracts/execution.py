"""Execution inputs and derived execution arithmetic.

This contract describes an *actual or simulated* trade. Observed facts (fills, their
prices, quantities and timestamps) are separated from derived arithmetic (weighted
prices, itemized costs, realized P&L and realized R). Authoritative money uses
``Decimal``; quantities are integers. Future outcome labels are not present here; they
belong in :mod:`edge_relative_research.contracts.outcomes`.
"""

from __future__ import annotations

from decimal import Decimal
from uuid import UUID

from pydantic import Field, model_validator

from .base import FrozenModel, UtcInstant
from .common import InstrumentRef
from .enums import Direction, ExitReason, Side, ValueKind


class Fill(FrozenModel):
    """One observed (or simulated) execution against an order."""

    side: Side
    quantity: int = Field(gt=0)
    price: Decimal = Field(ge=0)
    value_kind: ValueKind = ValueKind.OBSERVED

    fill_key: UUID | None = None
    order_key: UUID | None = None
    gross_value: Decimal | None = Field(default=None, ge=0)
    fees: Decimal | None = Field(default=None, ge=0)
    exchange_timestamp: UtcInstant | None = None
    received_timestamp: UtcInstant | None = None

    @model_validator(mode="after")
    def _observed_only(self) -> Fill:
        if self.value_kind is not ValueKind.OBSERVED:
            raise ValueError("a fill is an observed fact and must be OBSERVED")
        return self


class CostBreakdown(FrozenModel):
    """Itemized transaction costs. Every component defaults to an explicit zero."""

    brokerage: Decimal = Decimal("0")
    exchange: Decimal = Decimal("0")
    stt: Decimal = Decimal("0")
    gst: Decimal = Decimal("0")
    sebi: Decimal = Decimal("0")
    stamp_duty: Decimal = Decimal("0")
    other: Decimal = Decimal("0")
    value_kind: ValueKind = ValueKind.DERIVED

    @property
    def total(self) -> Decimal:
        return (
            self.brokerage
            + self.exchange
            + self.stt
            + self.gst
            + self.sebi
            + self.stamp_duty
            + self.other
        )


class ExecutionContext(FrozenModel):
    """The execution half of a research row.

    ``entry_reference_price`` is the signal/plan reference; ``average_entry_price`` is
    the actual weighted fill. They are kept distinct so slippage is visible and never
    conflated.
    """

    trade_key: UUID
    direction: Direction
    instrument: InstrumentRef

    planned_entry_low: Decimal | None = Field(default=None, ge=0)
    planned_entry_high: Decimal | None = Field(default=None, ge=0)
    structural_invalidation: Decimal | None = Field(default=None, ge=0)
    protective_stop: Decimal | None = Field(default=None, ge=0)
    target_reference: Decimal | None = Field(default=None, ge=0)
    entry_reference_price: Decimal | None = Field(default=None, ge=0)
    planned_slippage: Decimal | None = Field(default=None, ge=0)

    planned_quantity: int | None = Field(default=None, gt=0)
    filled_entry_quantity: int | None = Field(default=None, ge=0)
    filled_exit_quantity: int | None = Field(default=None, ge=0)

    average_entry_price: Decimal | None = Field(default=None, ge=0)
    average_exit_price: Decimal | None = Field(default=None, ge=0)
    actual_slippage: Decimal | None = None
    entry_fills: tuple[Fill, ...] = ()
    exit_fills: tuple[Fill, ...] = ()

    realized_gross_pnl: Decimal | None = None
    explicit_costs: CostBreakdown | None = None
    net_pnl: Decimal | None = None
    realized_r: Decimal | None = None
    holding_seconds: int | None = Field(default=None, ge=0)
    exit_reason: ExitReason | None = None

    value_kind: ValueKind = ValueKind.DERIVED

    @model_validator(mode="after")
    def _no_labels(self) -> ExecutionContext:
        if self.value_kind is ValueKind.LABELED:
            raise ValueError("an execution context may never be LABELED")
        return self

    @model_validator(mode="after")
    def _fill_sides(self) -> ExecutionContext:
        if any(fill.side is not Side.BUY for fill in self.entry_fills):
            raise ValueError("entry fills must be BUY")
        if any(fill.side is not Side.SELL for fill in self.exit_fills):
            raise ValueError("exit fills must be SELL")
        return self

    @model_validator(mode="after")
    def _quantity_consistency(self) -> ExecutionContext:
        entry_filled = sum(fill.quantity for fill in self.entry_fills)
        exit_filled = sum(fill.quantity for fill in self.exit_fills)
        if self.filled_entry_quantity is not None and entry_filled:
            if entry_filled != self.filled_entry_quantity:
                raise ValueError("entry fill quantity does not match filled_entry_quantity")
        if self.filled_exit_quantity is not None and exit_filled:
            if exit_filled != self.filled_exit_quantity:
                raise ValueError("exit fill quantity does not match filled_exit_quantity")
        return self

    @property
    def exit_timestamp(self) -> UtcInstant | None:
        if not self.exit_fills:
            return None
        stamps = [fill.exchange_timestamp for fill in self.exit_fills if fill.exchange_timestamp]
        return max(stamps) if stamps else None

    @property
    def entry_timestamp(self) -> UtcInstant | None:
        if not self.entry_fills:
            return None
        stamps = [fill.exchange_timestamp for fill in self.entry_fills if fill.exchange_timestamp]
        return min(stamps) if stamps else None
