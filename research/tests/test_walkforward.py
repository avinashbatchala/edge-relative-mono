from __future__ import annotations

from datetime import date

from edge_relative_research.backtest.walkforward import (
    Segment,
    chronological_folds,
    train_validation_oos,
)


def test_train_validation_oos_is_contiguous_ordered_and_complete() -> None:
    train, validation, oos = train_validation_oos(date(2023, 1, 1), date(2024, 12, 31))

    assert train.label == "TRAIN"
    assert validation.label == "VALIDATION"
    assert oos.label == "OOS"
    assert train.start == date(2023, 1, 1)
    assert oos.end == date(2024, 12, 31)
    # Non-overlapping and strictly ordered.
    assert train.end < validation.start
    assert validation.end < oos.start
    # Contiguous: each segment starts the day after the previous ends.
    assert validation.start == date.fromordinal(train.end.toordinal() + 1)
    assert oos.start == date.fromordinal(validation.end.toordinal() + 1)
    # TRAIN is the largest slice.
    assert train.days() > validation.days()
    assert train.days() > oos.days()


def test_chronological_folds_never_test_before_train() -> None:
    folds = chronological_folds(date(2023, 1, 1), date(2024, 12, 31), n_folds=3, train_fraction=0.5)

    assert len(folds) == 3
    previous_test_end: date | None = None
    for train, test in folds:
        assert isinstance(train, Segment) and isinstance(test, Segment)
        assert train.start == date(2023, 1, 1)
        assert train.end < test.start  # training strictly precedes testing
        if previous_test_end is not None:
            assert test.start > previous_test_end  # folds move forward in time
        previous_test_end = test.end
    assert folds[-1][1].end == date(2024, 12, 31)


def test_segment_as_iso_is_api_ready() -> None:
    segment = Segment("TRAIN", date(2023, 1, 1), date(2023, 6, 30))
    assert segment.as_iso() == {"startDate": "2023-01-01", "endDate": "2023-06-30"}
    assert segment.days() == 181
