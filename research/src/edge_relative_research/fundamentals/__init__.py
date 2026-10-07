"""Deterministic fundamental valuation operators (DD-06).

Numbers are code-computed with :class:`decimal.Decimal`, never binary floating point.
The LLM never produces these values; it only narrates them. These operators live in the
research layer only and carry no trading authority.
"""

from .comps import implied_equity_value, multiple, peer_median
from .dcf import discounted_cash_flow
from .ddm import gordon_growth
from .ratios import (
    debt_to_equity,
    net_margin,
    price_to_book,
    price_to_earnings,
    return_on_equity,
    revenue_growth,
)
from .versions import COMPS_VERSION, DCF_VERSION, DDM_VERSION, WACC_VERSION
from .wacc import compute_wacc

__all__ = [
    "COMPS_VERSION",
    "DCF_VERSION",
    "DDM_VERSION",
    "WACC_VERSION",
    "compute_wacc",
    "discounted_cash_flow",
    "gordon_growth",
    "implied_equity_value",
    "multiple",
    "peer_median",
    "debt_to_equity",
    "net_margin",
    "price_to_book",
    "price_to_earnings",
    "return_on_equity",
    "revenue_growth",
]
