"""Dataset lifecycle errors."""

from __future__ import annotations


class DatasetError(RuntimeError):
    """Base class for dataset reproducibility failures."""


class DatasetNotFoundError(DatasetError):
    """A referenced dataset or version does not exist."""


class DatasetAlreadyCommittedError(DatasetError):
    """A COMMITTED (or RETIRED) version cannot be rebuilt or overwritten."""


class DatasetBuildInProgressError(DatasetError):
    """A BUILDING version for the same identity already exists."""


class InvalidParentLineageError(DatasetError):
    """Parent lineage is missing, not committed, or later than the child cutoff."""


class ChecksumMismatchError(DatasetError):
    """Stored content does not match its recorded checksum."""
