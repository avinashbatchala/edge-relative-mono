"""Typed database configuration.

Connection details come from the environment. ``RESEARCH_DB_*`` overrides are accepted
so the research layer can point at a dedicated read-only role, falling back to the
backend's ``POSTGRES_*`` variables for local development.
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any

_ENV_KEYS = {
    "host": ("RESEARCH_DB_HOST", "POSTGRES_HOST"),
    "port": ("RESEARCH_DB_PORT", "POSTGRES_PORT"),
    "dbname": ("RESEARCH_DB_NAME", "POSTGRES_DB"),
    "user": ("RESEARCH_DB_USER", "POSTGRES_USER"),
    "password": ("RESEARCH_DB_PASSWORD", "POSTGRES_PASSWORD"),
}


def _first(env: Mapping[str, str], names: tuple[str, ...]) -> str | None:
    for name in names:
        value = env.get(name)
        if value not in (None, ""):
            return value
    return None


@dataclass(frozen=True, slots=True)
class DatabaseConfig:
    """Immutable connection settings."""

    host: str
    port: int
    dbname: str
    user: str
    password: str | None = None
    application_name: str = "edge-relative-research"
    connect_timeout: int = 10

    def connect_kwargs(self) -> dict[str, Any]:
        kwargs: dict[str, Any] = {
            "host": self.host,
            "port": self.port,
            "dbname": self.dbname,
            "user": self.user,
            "application_name": self.application_name,
            "connect_timeout": self.connect_timeout,
        }
        if self.password is not None:
            kwargs["password"] = self.password
        return kwargs

    def dsn(self) -> str:
        """Return a libpq conninfo string (kept for logs/tests; never secrets in logs)."""

        parts = [
            f"host={self.host}",
            f"port={self.port}",
            f"dbname={self.dbname}",
            f"user={self.user}",
            f"application_name={self.application_name}",
        ]
        return " ".join(parts)

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> DatabaseConfig:
        source = os.environ if env is None else env
        host = _first(source, _ENV_KEYS["host"])
        dbname = _first(source, _ENV_KEYS["dbname"])
        user = _first(source, _ENV_KEYS["user"])
        if host is None or dbname is None or user is None:
            missing = [
                names[0]
                for key, names in _ENV_KEYS.items()
                if _first(source, names) is None and key in {"host", "dbname", "user"}
            ]
            raise ValueError(f"database configuration missing: {', '.join(missing)}")
        port_raw = _first(source, _ENV_KEYS["port"]) or "5432"
        return cls(
            host=host,
            port=int(port_raw),
            dbname=dbname,
            user=user,
            password=_first(source, _ENV_KEYS["password"]),
        )
