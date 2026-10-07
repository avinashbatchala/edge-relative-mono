"""Shared fundamental fixtures are the Java/Python boundary (DD-06, ADR-006)."""

from decimal import Decimal

from edge_relative_research.fundamentals import compute_wacc, discounted_cash_flow
from edge_relative_research.fundamentals.fixtures import as_decimal, load_fixture
from edge_relative_research.fundamentals.models import DcfAssumptions, WaccInputs


def test_valuation_fixture_matches() -> None:
    fixture = load_fixture("valuation-dcf-wacc-v1.json")
    expected = fixture["expected"]
    tolerance = as_decimal(fixture["tolerance"])

    wacc_inputs = fixture["waccInputs"]
    wacc = compute_wacc(
        WaccInputs(
            risk_free_rate=as_decimal(wacc_inputs["riskFreeRate"]),
            equity_risk_premium=as_decimal(wacc_inputs["equityRiskPremium"]),
            beta=as_decimal(wacc_inputs["beta"]),
            cost_of_debt=as_decimal(wacc_inputs["costOfDebt"]),
            tax_rate=as_decimal(wacc_inputs["taxRate"]),
            equity_value=as_decimal(wacc_inputs["equityValue"]),
            debt_value=as_decimal(wacc_inputs["debtValue"]),
        )
    )
    assert abs(wacc.cost_of_equity - as_decimal(expected["costOfEquity"])) <= tolerance
    _assert_close(wacc.after_tax_cost_of_debt, expected["afterTaxCostOfDebt"], tolerance)
    assert abs(wacc.equity_weight - as_decimal(expected["equityWeight"])) <= tolerance
    assert abs(wacc.debt_weight - as_decimal(expected["debtWeight"])) <= tolerance
    assert abs(wacc.wacc - as_decimal(expected["wacc"])) <= tolerance
    assert wacc.version == fixture["operators"]["wacc"]

    dcf_inputs = fixture["dcfAssumptions"]
    dcf = discounted_cash_flow(
        DcfAssumptions(
            base_free_cash_flow=as_decimal(dcf_inputs["baseFreeCashFlow"]),
            growth_rate=as_decimal(dcf_inputs["growthRate"]),
            terminal_growth_rate=as_decimal(dcf_inputs["terminalGrowthRate"]),
            discount_rate=as_decimal(dcf_inputs["discountRate"]),
            years=dcf_inputs["years"],
        )
    )
    assert (
        abs(dcf.present_value_explicit - as_decimal(expected["presentValueExplicit"])) <= tolerance
    )
    assert abs(dcf.terminal_value - as_decimal(expected["terminalValue"])) <= tolerance
    _assert_close(dcf.present_value_terminal, expected["presentValueTerminal"], tolerance)
    assert abs(dcf.enterprise_value - as_decimal(expected["enterpriseValue"])) <= tolerance
    assert dcf.version == fixture["operators"]["dcf"]


def _assert_close(actual: Decimal, expected_value: object, tolerance: Decimal) -> None:
    assert abs(actual - as_decimal(expected_value)) <= tolerance


def test_normalised_fixture_is_point_in_time_and_decimal() -> None:
    fixture = load_fixture("yahoo-reliance-annual-v1.json")
    expected = fixture["expected"]

    # An observation-time source records the request instant as the filing time.
    assert expected["filedAt"] == fixture["asOf"]

    # Every monetary/ratio value is an exact decimal string, never binary floating point.
    for statement in expected["statements"]:
        assert isinstance(as_decimal(statement["value"]), Decimal)
    for metric in expected["metrics"]:
        assert isinstance(as_decimal(metric["value"]), Decimal)
