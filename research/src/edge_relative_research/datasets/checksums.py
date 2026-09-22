"""Deterministic canonical JSON and SHA-256 checksums.

Checksums must be stable across runs and machines. Values are normalized to a canonical
form first (sorted keys, no insignificant whitespace, explicit encodings for datetimes,
dates, UUIDs, Decimals and enums) and then hashed.
"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping
from datetime import date, datetime, timezone
from decimal import Decimal
from enum import Enum
from typing import Any
from uuid import UUID

CHECKSUM_PREFIX = "sha256:"


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _normalize(value: Any) -> Any:
    if value is None or isinstance(value, (bool, int, float, str)):
        return value
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, datetime):
        if value.tzinfo is None or value.tzinfo.utcoffset(value) is None:
            raise ValueError("naive datetime is not permitted in a checksum payload")
        return value.astimezone(timezone.utc).isoformat()
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, Enum):
        return _normalize(value.value)
    if isinstance(value, Mapping):
        return {str(key): _normalize(item) for key, item in value.items()}
    if isinstance(value, (list, tuple, set, frozenset)):
        return [_normalize(item) for item in value]
    raise TypeError(f"unsupported type in checksum payload: {type(value)!r}")


def canonical_json(payload: Any) -> bytes:
    """Return ``payload`` as UTF-8 canonical JSON bytes."""

    return json.dumps(
        _normalize(payload),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")


def checksum_payload(payload: Any) -> str:
    """Return a prefixed SHA-256 checksum of the canonical payload."""

    return CHECKSUM_PREFIX + sha256_hex(canonical_json(payload))
