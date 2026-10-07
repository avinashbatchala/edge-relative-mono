"""Queue worker: claim an ML analysis run, train, register the model.

The worker polls the DB-backed queue through the API, exports the per-anchor training matrix from the
backtest engine, trains a grouped ranker, verifies the frozen artifact reproduces LightGBM, writes the
artifact to disk and registers a model version. It never approves a trade or writes a binding; a
survivor is promoted by an explicit operator action.
"""

from __future__ import annotations

import hashlib
import json
import os
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from edge_relative_research.ml.artifact import from_lightgbm
from edge_relative_research.ml.client import MlApiClient
from edge_relative_research.ml.dataset import build_frame, feature_names, labelled, to_matrix
from edge_relative_research.ml.train import (
    DEFAULT_PARAMS,
    DEFAULT_ROUNDS,
    date_split,
    evaluate_split,
    train_regressor,
)

# Acceptance thresholds (operator-tunable via config). A model below these is still registered but
# marked EXPERIMENT; the UI gate refuses promotion.
DEFAULT_RANK_IC = 0.05
DEFAULT_NDCG = 0.5


@dataclass(frozen=True)
class ArtifactStore:
    directory: Path

    def write(self, model_code: str, artifact: Mapping[str, Any]) -> tuple[str, str]:
        self.directory.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(artifact, indent=2, sort_keys=True).encode("utf-8")
        checksum = hashlib.sha256(payload).hexdigest()
        path = self.directory / f"{model_code}-{checksum[:12]}.json"
        path.write_bytes(payload)
        return str(path), checksum


def export_request(config: Mapping[str, Any]) -> dict[str, Any]:
    """Map an ML analysis config into the backtest per-anchor export request."""
    symbols = list(config.get("symbols", []))
    return {
        "symbols": symbols,
        "setupTimeframe": config.get("setupTimeframe", "M5"),
        "dailyTimeframe": config.get("dailyTimeframe", "D1"),
        "startDate": config.get("startDate"),
        "endDate": config.get("endDate"),
        "marketSymbol": config.get("marketSymbol", "NIFTY"),
        "startingCapital": config.get("startingCapital", 1_000_000),
        "currency": config.get("currency", "INR"),
        "strategyPreset": config.get("strategyPreset", "ER_RS_CONTINUATION_V1_RESEARCH"),
        "riskPreset": config.get("riskPreset", "RESEARCH_PERMISSIVE"),
        "contextSource": "DERIVED_RESEARCH",
        "seed": config.get("seed", 7),
    }


def analysis_metrics(config: Mapping[str, Any]) -> dict[str, float]:
    thresholds = config.get("thresholds") or {}
    return {
        "minRankIc": float(thresholds.get("rankIc", DEFAULT_RANK_IC)),
        "minNdcg": float(thresholds.get("ndcg", DEFAULT_NDCG)),
    }


def process_run(client: MlApiClient, store: ArtifactStore, run: Mapping[str, Any]) -> None:
    key = str(run["key"])
    config = run.get("config") or {}
    try:
        client.progress(key, {"stage": "export"})
        rows = client.export_anchors(export_request(config))
        frame = labelled(build_frame(rows))
        if frame.height < 20:
            client.fail(key, f"only {frame.height} labelled anchors; need at least 20 to train")
            return
        names = feature_names(frame)
        matrix = to_matrix(frame, names)
        split = date_split(matrix.groups, (0.6, 0.2, 0.2))
        client.progress(key, {"stage": "train", "rows": frame.height, "features": len(names)})

        params = {**DEFAULT_PARAMS, **(config.get("model") or {})}
        rounds = int(config.get("rounds", DEFAULT_ROUNDS))
        seed = int(config.get("seed", 7))
        booster = train_regressor(matrix, split.train, params, rounds, seed)

        validation = evaluate_split(booster, matrix, split.validation)
        out_of_sample = evaluate_split(booster, matrix, split.out_of_sample)
        thresholds = analysis_metrics(config)
        gates_pass = (
            out_of_sample.rank_ic is not None
            and out_of_sample.rank_ic >= thresholds["minRankIc"]
            and out_of_sample.ndcg is not None
            and out_of_sample.ndcg >= thresholds["minNdcg"]
        )

        client.progress(key, {"stage": "export-artifact"})
        sample = matrix.features[: min(50, len(matrix.features))]
        artifact = from_lightgbm(booster, names, params["objective"], sample).to_dict()
        uri, checksum = store.write(str(config.get("modelCode", "er-ranker")), artifact)

        metrics: dict[str, Any] = {
            "validation": {"rankIc": validation.rank_ic, "ndcg": validation.ndcg, "groups": validation.groups},
            "outOfSample": {"rankIc": out_of_sample.rank_ic, "ndcg": out_of_sample.ndcg, "groups": out_of_sample.groups},
            "thresholds": thresholds,
            "gatesPass": gates_pass,
            "rows": frame.height,
            "features": len(names),
        }
        start = str(config.get("startDate"))
        end = str(config.get("endDate"))
        registration = {
            "modelCode": config.get("modelCode", "er-ranker"),
            "modelName": config.get("modelName", "Edge Relative ranker"),
            "description": "Gradient-boosted ranker over VALID setups (advisory).",
            "algorithm": "LIGHTGBM_RANKER",
            "lifecycleState": "VALIDATED" if gates_pass else "EXPERIMENT",
            "artifactUri": uri,
            "artifactChecksum": checksum,
            "trainingPeriodStart": start,
            "trainingPeriodEnd": end,
            "validationPeriodStart": start,
            "validationPeriodEnd": end,
            "testPeriodStart": start,
            "testPeriodEnd": end,
            "metrics": metrics,
            "strategyCompatibility": ["ER_RS_CONTINUATION_V1"],
            "codeVersion": "er-ml-train-v1",
        }
        model = client.register_model(registration)
        client.complete(key, int(model["modelVersionId"]), metrics)
    except Exception as error:  # noqa: BLE001 - any training failure fails the run cleanly
        client.fail(key, f"{type(error).__name__}: {error}")


def run_forever(client: MlApiClient, store: ArtifactStore, poll_seconds: float = 3.0) -> None:
    while True:
        run = client.claim("research-runner")
        if run is None:
            time.sleep(poll_seconds)
            continue
        process_run(client, store, run)


def default_store() -> ArtifactStore:
    return ArtifactStore(Path(os.environ.get("ER_ML_ARTIFACT_DIR", "artifacts/ml")))
