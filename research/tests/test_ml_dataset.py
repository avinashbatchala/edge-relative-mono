"""Training-matrix assembly from anchor exports."""

from edge_relative_research.ml.dataset import (
    build_frame,
    feature_names,
    labelled,
    to_matrix,
)


def _anchor(symbol: str, date: str, rrs: float, realized: float | None) -> dict:
    return {
        "tradingDate": date,
        "instrumentId": 1,
        "symbol": symbol,
        "direction": "LONG",
        "features": {"RRS_RAW": rrs, "ATR": 5.0},
        "label": None if realized is None else {"realizedR": realized, "mfeR": realized + 0.5},
    }


def test_builds_typed_frame_and_excludes_unlabelled_rows():
    frame = build_frame(
        [_anchor("SBIN", "2026-01-02", 0.4, 1.2), _anchor("SBIN", "2026-01-03", -0.2, None)]
    )
    assert feature_names(frame) == ["ATR", "RRS_RAW"]
    trainable = labelled(frame)
    assert trainable.height == 1


def test_matrix_keeps_missing_features_absent():
    frame = labelled(build_frame([_anchor("SBIN", "2026-01-02", 0.4, 1.2)]))
    matrix = to_matrix(frame, ["ATR", "RRS_RAW", "NOT_PRESENT"])
    assert matrix.feature_names == ["ATR", "RRS_RAW", "NOT_PRESENT"]
    assert matrix.features[0]["RRS_RAW"] == 0.4
    assert "NOT_PRESENT" not in matrix.features[0]
    assert matrix.labels == [1.2]
    assert matrix.groups == ["2026-01-02"]
