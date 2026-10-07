"""Walk-forward research CLI.

    python -m edge_relative_research.backtest.walkforward_cli --symbol SBIN --timeframe M15 \
        --start 2023-10-01 --end 2026-09-18 [--promote --instrument-id 1 --strategy-version-id 1]

Splits the window into TRAIN/VALIDATION/OOS, sweeps the grid, selects on validation and reports the
out-of-sample result. With ``--promote`` (and only if the OOS result clears ``--min-oos``) it writes
the winning configuration as a per-instrument binding through the Phase E API.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date, timedelta
from typing import Any

from edge_relative_research.backtest.cli import DEFAULT_GRID, DEFAULT_PARAMS, _load_grid
from edge_relative_research.backtest.client import BacktestApiClient
from edge_relative_research.backtest.search import grid_configs
from edge_relative_research.backtest.walkforward import train_validation_oos
from edge_relative_research.backtest.walkforward_search import (
    promotion_decision,
    run_walkforward_search,
)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Walk-forward backtest parameter search")
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
    parser.add_argument("--min-oos", type=float, default=0.0)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--poll-seconds", type=float, default=2.0)
    parser.add_argument("--timeout-seconds", type=float, default=3600.0)
    parser.add_argument("--grid", help="JSON object of parameter -> list, or @path to a JSON file")
    parser.add_argument("--promote", action="store_true")
    parser.add_argument("--instrument-id", type=int)
    parser.add_argument("--strategy-version-id", type=int)
    parser.add_argument("--promote-from", type=date.fromisoformat)
    return parser


def _base_request(args: argparse.Namespace) -> dict[str, Any]:
    return {
        "symbols": [args.symbol],
        "marketSymbol": args.market,
        "timeframe": args.timeframe,
        "dailyTimeframe": args.daily_timeframe,
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


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    grid = _load_grid(args.grid) if args.grid else DEFAULT_GRID
    configs = grid_configs(DEFAULT_PARAMS, grid)
    train, validation, oos = train_validation_oos(args.start, args.end)

    client = BacktestApiClient(args.base_url)
    print(
        f"Walk-forward {args.symbol} {args.timeframe}: TRAIN {train.start}..{train.end}, "
        f"VALIDATION {validation.start}..{validation.end}, OOS {oos.start}..{oos.end}; "
        f"{len(configs)} configs x3.",
        flush=True,
    )
    result = run_walkforward_search(
        client,
        _base_request(args),
        configs,
        train,
        validation,
        oos,
        metric=args.metric,
        poll_seconds=args.poll_seconds,
        timeout_seconds=args.timeout_seconds,
    )

    if result.best is None:
        print("No configuration produced a validation result.")
        return 1
    print(f"\nValidation winner: {dict(result.best.config)}")
    print(f"  validation {args.metric}={result.best.metrics.get(args.metric)}")
    if result.best_out_of_sample is not None:
        print(f"  out-of-sample {args.metric}={result.best_out_of_sample.metrics.get(args.metric)}")

    promote = promotion_decision(result, args.metric, args.min_oos)
    print(f"\nPromotion eligible: {promote} (OOS {args.metric} >= {args.min_oos})")
    if not args.promote:
        return 0
    if not promote:
        print("Not promoting: out-of-sample result did not clear the threshold.")
        return 2
    if args.instrument_id is None or args.strategy_version_id is None:
        print("--promote requires --instrument-id and --strategy-version-id.")
        return 3
    effective_from = args.promote_from or (oos.end + timedelta(days=1))
    response = client.create_binding(
        instrument_id=args.instrument_id,
        strategy_version_id=args.strategy_version_id,
        parameters=dict(result.best.config),
        effective_from=effective_from.isoformat(),
        lifecycle_state="VALIDATED",
        source="walkforward-search",
    )
    print(f"Promoted binding: {json.dumps(response)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
