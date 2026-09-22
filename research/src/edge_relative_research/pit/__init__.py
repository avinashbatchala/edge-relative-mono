"""Point-in-time join framework and leakage guards.

Reusable primitives that make it difficult to introduce future-information leakage:
as-of resolution, timestamp/bar/volume/pivot/constituent/corporate-action guards,
normalization-fit boundaries, repository-backed loaders and a dataset leakage scanner.
"""

from .errors import (
    FullDayVolumeError,
    FutureConstituentError,
    FutureCorporateActionError,
    FutureDataError,
    FuturePivotError,
    IncompleteBarError,
    LeakageDetectedError,
    MissingAsOfError,
    NormalizationLeakError,
    OutcomeInFeatureError,
    PointInTimeError,
)
from .guards import (
    assert_closed_bar,
    assert_constituent_as_of,
    assert_corporate_action_known,
    assert_feature_timestamps,
    assert_is_active_member,
    assert_no_full_day_volume,
    assert_no_outcome_columns,
    assert_not_future,
    assert_pivot_confirmed_by,
    assert_rows_not_future,
    assert_volume_scope,
    ensure_as_of,
)
from .join import PointInTimeJoiner, TemporalRecord, row_to_mapping
from .loaders import PointInTimeLoader
from .normalization import (
    NormalizationBaseline,
    assert_baseline_not_fit_on_test,
    assert_fit_before_use,
    assert_fit_before_validation,
    assert_fit_within_train,
    baseline_from_row,
)
from .scan import (
    LeakageFinding,
    LeakageScanner,
    LeakageScanResult,
)
from .vocab import VolumeScope, find_outcome_columns, is_outcome_column

__all__ = [
    "FullDayVolumeError",
    "FutureConstituentError",
    "FutureCorporateActionError",
    "FutureDataError",
    "FuturePivotError",
    "IncompleteBarError",
    "LeakageDetectedError",
    "LeakageFinding",
    "LeakageScanResult",
    "LeakageScanner",
    "MissingAsOfError",
    "NormalizationBaseline",
    "NormalizationLeakError",
    "OutcomeInFeatureError",
    "PointInTimeError",
    "PointInTimeJoiner",
    "PointInTimeLoader",
    "TemporalRecord",
    "VolumeScope",
    "assert_baseline_not_fit_on_test",
    "assert_closed_bar",
    "assert_constituent_as_of",
    "assert_corporate_action_known",
    "assert_feature_timestamps",
    "assert_fit_before_use",
    "assert_fit_before_validation",
    "assert_fit_within_train",
    "assert_is_active_member",
    "assert_no_full_day_volume",
    "assert_no_outcome_columns",
    "assert_not_future",
    "assert_pivot_confirmed_by",
    "assert_rows_not_future",
    "assert_volume_scope",
    "baseline_from_row",
    "ensure_as_of",
    "find_outcome_columns",
    "is_outcome_column",
    "row_to_mapping",
]
