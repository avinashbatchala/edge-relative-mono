"""Code-version helpers for dataset and experiment lineage.

A research artifact must be traceable to the code that produced it. The version is
the git commit when the package runs inside the repository, otherwise the package
version. This is metadata only; it never grants production authority.
"""

from __future__ import annotations

import subprocess
from functools import lru_cache
from pathlib import Path

from . import __version__

_PACKAGE_DIR = Path(__file__).resolve().parent


@lru_cache(maxsize=1)
def repo_root() -> Path | None:
    """Return the enclosing git work tree, or ``None`` if not in a checkout."""

    for candidate in (_PACKAGE_DIR, *_PACKAGE_DIR.parents):
        if (candidate / ".git").exists():
            return candidate
    return None


@lru_cache(maxsize=1)
def code_version() -> str:
    """Return a stable identifier for the running code."""

    root = repo_root()
    if root is not None:
        try:
            result = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=root,
                capture_output=True,
                text=True,
                check=True,
                timeout=5,
            )
        except (OSError, subprocess.SubprocessError):
            pass
        else:
            revision = result.stdout.strip()
            if revision:
                return revision
    return f"package:{__version__}"
