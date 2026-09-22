"""Attribute/mapping access shared by the point-in-time guards."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

_MISSING = object()


def _lookup(source: Any, key: str) -> Any:
    if isinstance(source, Mapping):
        return source[key] if key in source else _MISSING
    if hasattr(source, key):
        return getattr(source, key)
    return _MISSING


def get_value(source: Any, key: str, default: Any = _MISSING) -> Any:
    """Read ``key`` from a mapping or object, with an optional default."""

    value = _lookup(source, key)
    if value is _MISSING:
        if default is _MISSING:
            raise KeyError(key)
        return default
    return value


def get_optional(source: Any, *keys: str) -> Any:
    """Return the first present key among ``keys``, else ``None``."""

    for key in keys:
        value = _lookup(source, key)
        if value is not _MISSING:
            return value
    return None


def has_key(source: Any, key: str) -> bool:
    return _lookup(source, key) is not _MISSING
