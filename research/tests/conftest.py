"""Shared test fixtures: contract examples and an integration database.

The integration database starts a real PostgreSQL container, applies the backend Flyway
migrations from ``backend/application/src/main/resources/db/migration`` and seeds a small
coherent dataset from ``tests/fixtures/seed.sql``. If Docker is unavailable the database
fixtures skip rather than fail.
"""

from __future__ import annotations

import json
import time
from collections.abc import Iterator
from contextlib import suppress
from pathlib import Path
from types import SimpleNamespace

import pytest

from edge_relative_research import contracts
from edge_relative_research.datasets import DatasetCatalog, LocalDatasetStorage
from edge_relative_research.db import (
    CollectingObserver,
    DatabaseConfig,
    QueryExecutor,
    ResearchDatabase,
)
from edge_relative_research.repositories import (
    ControlRepository,
    MarketRepository,
    OperationalRepository,
    ReferenceRepository,
    ResearchRepository,
)

FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"
REPO_ROOT = Path(__file__).resolve().parents[2]
SHARED_FEATURE_FIXTURES_DIR = REPO_ROOT / "contracts" / "fixtures" / "features"
MIGRATIONS_DIR = (
    REPO_ROOT / "backend" / "application" / "src" / "main" / "resources" / "db" / "migration"
)
SEED_FILE = FIXTURES_DIR / "seed.sql"

POSTGRES_IMAGE = "postgres:18.4-bookworm"


# --- contract fixtures (Prompt 02) -------------------------------------------


def load_fixture(name: str) -> dict:
    return json.loads((FIXTURES_DIR / name).read_text(encoding="utf-8"))


def load_shared_feature_fixture(name: str) -> dict:
    return json.loads((SHARED_FEATURE_FIXTURES_DIR / name).read_text(encoding="utf-8"))


@pytest.fixture
def anchor() -> contracts.ResearchAnchor:
    return contracts.ResearchAnchor.model_validate(load_fixture("research_anchor.json"))


@pytest.fixture
def feature_context() -> contracts.FeatureContext:
    return contracts.FeatureContext.model_validate(load_fixture("feature_context.json"))


@pytest.fixture
def execution_context() -> contracts.ExecutionContext:
    return contracts.ExecutionContext.model_validate(load_fixture("execution_context.json"))


@pytest.fixture
def outcome_record() -> contracts.OutcomeRecord:
    return contracts.OutcomeRecord.model_validate(load_fixture("outcome_record.json"))


@pytest.fixture
def dataset_identity() -> contracts.DatasetIdentity:
    return contracts.DatasetIdentity.model_validate(load_fixture("dataset_identity.json"))


@pytest.fixture
def pattern_window() -> contracts.PatternWindow:
    return contracts.PatternWindow.model_validate(load_fixture("pattern_window.json"))


@pytest.fixture
def pattern_match() -> contracts.PatternMatch:
    return contracts.PatternMatch.model_validate(load_fixture("pattern_match.json"))


# --- integration database ----------------------------------------------------


def _migration_files() -> list[Path]:
    versioned = sorted(
        (path for path in MIGRATIONS_DIR.glob("V*.sql")),
        key=lambda path: int(path.name[1:4]),
    )
    repeatable = sorted(MIGRATIONS_DIR.glob("R*.sql"))
    return [*versioned, *repeatable]


def _apply_migrations(connection) -> None:
    import psycopg

    for path in _migration_files():
        with psycopg.ClientCursor(connection) as cursor:
            cursor.execute(path.read_text(encoding="utf-8"))
    connection.commit()


def _seed(connection) -> None:
    import psycopg

    with connection.transaction():
        with psycopg.ClientCursor(connection) as cursor:
            cursor.execute(SEED_FILE.read_text(encoding="utf-8"))


def _wait_for_postgres(config: DatabaseConfig, timeout: float = 60.0) -> None:
    import psycopg

    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            with psycopg.connect(**config.connect_kwargs()) as connection:
                connection.execute("SELECT 1")
            return
        except psycopg.Error as error:  # pragma: no cover - timing dependent
            last_error = error
            time.sleep(0.5)
    raise RuntimeError(f"PostgreSQL did not become ready: {last_error}")


@pytest.fixture(scope="session")
def _postgres_container() -> Iterator[DatabaseConfig]:
    try:
        from testcontainers.core.container import DockerContainer
    except ImportError:  # pragma: no cover
        pytest.skip("testcontainers is not installed")

    container = DockerContainer(POSTGRES_IMAGE)
    container.with_env("POSTGRES_USER", "research")
    container.with_env("POSTGRES_PASSWORD", "research")
    container.with_env("POSTGRES_DB", "research")
    container.with_exposed_ports(5432)
    try:
        container.start()
    except Exception as error:  # pragma: no cover - depends on Docker availability
        pytest.skip(f"Docker/Testcontainers unavailable: {error}")

    config = DatabaseConfig(
        host=container.get_container_host_ip(),
        port=int(container.get_exposed_port(5432)),
        dbname="research",
        user="research",
        password="research",
        application_name="edge-relative-research-tests",
    )
    try:
        _wait_for_postgres(config)
        import psycopg

        with psycopg.connect(**config.connect_kwargs(), autocommit=True) as connection:
            _apply_migrations(connection)
            _seed(connection)
        yield config
    finally:
        with suppress(Exception):
            container.stop()


@pytest.fixture(scope="session")
def database(_postgres_container: DatabaseConfig) -> ResearchDatabase:
    return ResearchDatabase(_postgres_container)


@pytest.fixture
def observer() -> CollectingObserver:
    return CollectingObserver()


@pytest.fixture
def repos(database: ResearchDatabase, observer: CollectingObserver) -> Iterator[SimpleNamespace]:
    with database.read_only() as connection:
        executor = QueryExecutor(connection, observer=observer)
        yield SimpleNamespace(
            reference=ReferenceRepository(executor),
            control=ControlRepository(executor),
            market=MarketRepository(executor),
            operational=OperationalRepository(executor),
            research=ResearchRepository(executor),
            observer=observer,
        )


@pytest.fixture
def research_connection(database: ResearchDatabase) -> Iterator[object]:
    with database.research_write() as connection:
        yield connection


@pytest.fixture
def catalog(research_connection: object) -> DatasetCatalog:
    return DatasetCatalog(research_connection)  # type: ignore[arg-type]


@pytest.fixture
def dataset_storage(tmp_path: Path) -> LocalDatasetStorage:
    return LocalDatasetStorage(tmp_path / "dataset-store")
