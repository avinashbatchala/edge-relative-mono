"""One-shot research search CLI.

    python -m edge_relative_research.backtest.cli --symbol SBIN --timeframe M15 \
        --start 2023-10-01 --end 2026-09-18

Drives the Java backtester over HTTP, sweeps a parameter grid, and prints the ranked configurations.
It is a research tool: it never promotes a parameter set and writes nothing to production state.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from typing import Any

from edge_relative_research.backtest.client import BacktestApiClient
from edge_relative_research.backtest.search import grid_configs, rank, run_search

# The research preset field set; every required StrategyParameters component must be present.
DEFAULT_PARAMS: dict[str, Any] = {
    "enabledFamilies": ["M5_3_8_CONFIRMATION"],
    "rrsM5PersistenceLongMin": 0.0,
    "rrsM5PersistenceShortMin": 0.0,
    "minRvolDaily": 1.0,
    "minRvolInterval": 1.0,
    "minRvolCumulative": 1.0,
    "minLiquidityMedianTradedValue": 5_000_000,
    "minTechnicalVoidAtr": 0.10,
    "maxEntryExtensionAtr": 0.50,
    "nearTriggerDistanceAtr": 0.25,
    "triggerBufferAtrFraction": 0.0,
    "triggerBufferTicks": 1,
    "maxBarsSinceTrigger": 3,
    "maxBarsInState": 12,
    "openingBlackoutMinutes": 15,
    "entryCutoffMinutesBeforeClose": 15,
    "dataStalenessSeconds": 900.0,
    "neutralMarketPolicy": "BLOCK",
    "neutralRrsPersistenceExtra": 0.0,
}

DEFAULT_GRID: dict[str, list[Any]] = {
    "rrsM5PersistenceLongMin": [0.0, 0.4],
    "rrsM5PersistenceShortMin": [0.0, 0.4],
    "minRvolInterval": [1.0, 1.5],
}


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="One-shot backtest parameter search")
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--market", default="NIFTY")
    parser.add_argument("--timeframe", default="M15")
    parser.add_argument("--daily-timeframe", default="D1")
    parser.add_argument("--start", required=True, type=date.fromisoformat)
    parser.add_argument("--end", required=True, type=date.fromisoformat)
    parser.add_argument("--capital", type=float, default=1_000_000)
    parser.add_argument("--currency", default="INR")
    parser.add_argument("--risk-preset", default="RESEARCH_PERMISSIVE")
    parser.add_argument("--target-r", type=float, default=2.0)
    parser.add_argument("--min-stop-atr", type=float, default=1.5)
    parser.add_argument("--metric", default="netPnl")
    parser.add_argument("--top", type=int, default=10)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--poll-seconds", type=float, default=2.0)
    parser.add_argument("--timeout-seconds", type=float, default=3600.0)
    parser.add_argument("--grid", help="JSON object of parameter -> list, or @path to a JSON file")
    return parser


def _load_grid(value: str | None) -> dict[str, list[Any]]:
    if not value:
        return DEFAULT_GRID
    if value.startswith("@"):
        with open(value[1:], encoding="utf-8") as handle:
            return json.load(handle)
    return json.loads(value)


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    grid = _load_grid(args.grid)
    configs = grid_configs(DEFAULT_PARAMS, grid)

    base_request: dict[str, Any] = {
        "symbols": [args.symbol],
        "marketSymbol": args.market,
        "timeframe": args.timeframe,
        "dailyTimeframe": args.daily_timeframe,
        "startDate": args.start.isoformat(),
        "endDate": args.end.isoformat(),
        "startingCapital": args.capital,
        "currency": args.currency,
        "strategyPreset": "ER_RS_CONTINUATION_V1_RESEARCH",
        "riskPreset": args.risk_preset,
        "contextSource": "DERIVED_RESEARCH",
        "warmupSessions": 60,
        "seed": args.seed,
        "endOfRun": "MARK_TO_MARKET",
        "execution": {
            "targetMethod": "R_MULTIPLE",
            "targetR": args.target_r,
            "minStopAtr": args.min_stop_atr,
        },
    }

    client = BacktestApiClient(args.base_url)
    print(
        f"Sweeping {len(configs)} configurations on {args.symbol} {args.timeframe} ...", flush=True
    )
    results = run_search(
        client,
        base_request,
        configs,
        poll_seconds=args.poll_seconds,
        timeout_seconds=args.timeout_seconds,
        on_result=lambda result: print(f"  {result.run_key[:8]} {result.status}", flush=True),
    )

    print(f"\nRanked by {args.metric} (top {args.top}):")
    for result in rank(results, args.metric)[: args.top]:
        varied = {key: result.config[key] for key in sorted(grid)}
        print(
            f"  {result.metrics.get(args.metric)} | trades={result.metrics.get('completedTrades')} "
            f"win%={result.metrics.get('winRatePct')} | {varied}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
