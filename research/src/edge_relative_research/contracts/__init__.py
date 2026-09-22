"""Canonical, typed research data contracts.

Contracts are split by the feature/outcome boundary:

- inputs: :mod:`anchor`, :mod:`features`, :mod:`execution`
- lineage: :class:`DatasetIdentity`, :mod:`patterns`
- labels: :mod:`outcomes` (the only place future data may live)

Importing a feature contract never imports the outcome contract.
"""

from .anchor import ResearchAnchor
from .base import FrozenModel, UtcInstant
from .common import FeatureVersionStamp, InstrumentRef, compute_parameter_hash
from .dataset import DatasetIdentity
from .enums import (
    AmbiguityPolicy,
    Cohort,
    ContextKind,
    DatasetStatus,
    DatasetType,
    Direction,
    ExitReason,
    FeatureAvailability,
    FeatureQuality,
    LabelState,
    SetupInitialization,
    SetupState,
    Side,
    Timeframe,
    ValueKind,
    worst_quality,
)
from .execution import CostBreakdown, ExecutionContext, Fill
from .features import ContextBlock, FeatureContext, FeatureValue, TimeOfDayContext
from .outcomes import FuturePathMetadata, HorizonReturn, OutcomeRecord
from .patterns import PatternMatch, PatternWindow

__all__ = [
    "AmbiguityPolicy",
    "Cohort",
    "ContextBlock",
    "ContextKind",
    "CostBreakdown",
    "DatasetIdentity",
    "DatasetStatus",
    "DatasetType",
    "Direction",
    "ExecutionContext",
    "ExitReason",
    "FeatureAvailability",
    "FeatureContext",
    "FeatureQuality",
    "FeatureValue",
    "FeatureVersionStamp",
    "Fill",
    "FrozenModel",
    "FuturePathMetadata",
    "HorizonReturn",
    "InstrumentRef",
    "LabelState",
    "OutcomeRecord",
    "PatternMatch",
    "PatternWindow",
    "ResearchAnchor",
    "SetupInitialization",
    "SetupState",
    "Side",
    "TimeOfDayContext",
    "Timeframe",
    "UtcInstant",
    "ValueKind",
    "compute_parameter_hash",
    "worst_quality",
]
