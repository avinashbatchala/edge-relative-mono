"""Read adapters over the existing Edge Relative schema.

Production data (``reference``, ``control``, ``market``, ``operational``) is read-only;
``research`` is read here and will be written by the dataset stage.
"""

from .base import RepositoryBase, where_clause
from .control import ControlRepository
from .market import MarketRepository
from .operational import OperationalRepository
from .reference import ReferenceRepository
from .research import ResearchRepository

__all__ = [
    "ControlRepository",
    "MarketRepository",
    "OperationalRepository",
    "ReferenceRepository",
    "RepositoryBase",
    "ResearchRepository",
    "where_clause",
]
