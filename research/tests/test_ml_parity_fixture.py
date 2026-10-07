"""Cross-language parity against the shared er-gbm-v1 fixture.

The same fixture is evaluated by the Java GbmModelEvaluator; both must reproduce every expected
value.
"""

import json
from pathlib import Path

from edge_relative_research.ml.artifact import GbmArtifact, evaluate


def _fixture() -> Path:
    working = Path(__file__).resolve()
    for candidate in [working, *working.parents]:
        path = candidate / "contracts" / "fixtures" / "ml" / "gbm_parity.json"
        if path.is_file():
            return path
    raise AssertionError("contracts/fixtures/ml/gbm_parity.json not found")


def test_python_evaluator_matches_shared_fixture():
    payload = json.loads(_fixture().read_text())
    model = GbmArtifact.from_dict(payload["model"])
    for case in payload["cases"]:
        actual = evaluate(model, case["features"])
        assert abs(actual - case["expected"]) < 1e-9
