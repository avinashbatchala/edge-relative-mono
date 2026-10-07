"""ML ops CLI.

    # enqueue an analysis run
    python -m edge_relative_research.ml.cli enqueue --symbol SBIN --timeframe M5 \
        --start 2023-10-01 --end 2026-09-18

    # run the queue worker (blocks)
    python -m edge_relative_research.ml.cli worker --base-url http://127.0.0.1:8090
"""

from __future__ import annotations

import argparse
import sys
from typing import Any

from edge_relative_research.ml.client import MlApiClient
from edge_relative_research.ml.worker import default_store, run_forever


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Edge Relative ML ops")
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--base-url", default="http://127.0.0.1:8090")
    sub = parser.add_subparsers(dest="command", required=True)

    enqueue = sub.add_parser("enqueue", parents=[common], help="enqueue an analysis run")
    enqueue.add_argument("--symbol", action="append", required=True)
    enqueue.add_argument("--timeframe", default="M5")
    enqueue.add_argument("--daily-timeframe", default="D1")
    enqueue.add_argument("--start", required=True)
    enqueue.add_argument("--end", required=True)
    enqueue.add_argument("--market", default="NIFTY")
    enqueue.add_argument("--seed", type=int, default=7)
    enqueue.add_argument("--model-code", default="er-ranker")
    enqueue.add_argument("--requested-by", default="cli")

    sub.add_parser("worker", parents=[common], help="poll and process the analysis queue")
    return parser


def _config(args: argparse.Namespace) -> dict[str, Any]:
    return {
        "symbols": args.symbol,
        "setupTimeframe": args.timeframe,
        "dailyTimeframe": args.daily_timeframe,
        "startDate": args.start,
        "endDate": args.end,
        "marketSymbol": args.market,
        "seed": args.seed,
        "modelCode": args.model_code,
    }


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    client = MlApiClient(args.base_url)
    if args.command == "enqueue":
        run = client.enqueue(_config(args), args.requested_by)
        print(f"enqueued {run['key']} status={run['status']}", flush=True)
        return 0
    run_forever(client, default_store())
    return 0


if __name__ == "__main__":
    sys.exit(main())
