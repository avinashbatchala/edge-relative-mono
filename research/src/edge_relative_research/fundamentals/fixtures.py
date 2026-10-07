"""Locate and load the shared fundamental fixtures under ``contracts/fixtures``.

The fixtures are the frozen boundary shared with the Java adapter. Research code reads
the normalised ``expected`` values; it never re-parses a provider payload as authority.
"""

from __future__ import annotations

import json
from decimal import Decimal
from pathlib import Path
from typing import Any

from ..version import repo_root

_RELATIVE = ("contracts", "fixtures", "fundamentals")


def fixtures_dir() -> Path:
    """Return the fundamentals fixtures directory."""

    root = repo_root()
    if root is not None:
        candidate = root.joinpath(*_RELATIVE)
        if candidate.is_dir():
            return candidate
    for candidate in Path(__file__).resolve().parents:
        directory = candidate.joinpath(*_RELATIVE)
        if directory.is_dir():
            return directory
    raise FileNotFoundError("contracts/fixtures/fundamentals was not found")


def load_fixture(name: str) -> dict[str, Any]:
    """Load a fixture by file name as parsed JSON."""

    return json.loads((fixtures_dir() / name).read_text(encoding="utf-8"))


def as_decimal(value: Any) -> Decimal:
    """Parse a fixture value into an exact Decimal (never via binary floating point)."""

    return Decimal(str(value))
