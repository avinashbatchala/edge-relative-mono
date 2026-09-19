# Edge Relative

An algorithmic trading platform for Indian equities, initially focused on NSE and
a single operator. This repository is an engineering scaffold, not a trading system.
It has no market feed, strategy implementation, risk engine, broker integration,
trading persistence, or live order capability.

## Structure

- `backend/`: Java 25 Maven reactor with a framework-free `domain` module and a
  Spring Boot `application` module with PostgreSQL connectivity through jOOQ. The
  domain is intentionally empty.
- `frontend/`: Vue 3 / TypeScript / Vite operator-workstation placeholder.
- `research/`: Python src-layout package managed with uv, without trading logic.
- `compose.yaml`: local PostgreSQL with persistent storage.
- `docs/design-docs`: product, strategy, risk, stack, and data specifications.
- `.github/workflows/ci.yml`: independent backend, frontend, and research checks.

Add further modules, contracts, SQL Flyway migrations, jOOQ adapters, and deployment
infrastructure when their first concrete use case is implemented. The larger
directory tree in DD-04A is conceptual, not a requirement to create empty modules.

## Prerequisites

- JDK 25; set `JAVA_HOME` accordingly. Other major JDKs are rejected by the build.
- Node 24 LTS (at least 24.15.0) and pnpm 10.34.5.
- uv 0.12.10 and Python 3.13 (uv can provision Python).
- Docker with Compose v2 or newer for local PostgreSQL and backend integration tests.

Maven 3.9.11 is downloaded by the checked-in Maven Wrapper. First-time dependency
installation requires network access. Backend integration tests use Testcontainers
with a temporary PostgreSQL database; Docker must be running, but the Compose
service and a local `.env` file are not required for tests.

## Local PostgreSQL

Run from the repository root:

```sh
cp .env.example .env
# Set POSTGRES_PASSWORD in .env before starting.
docker compose up -d --wait postgres
docker compose ps
```

Compose reads the root `.env` file automatically; it is ignored by Git. The
password is required and has no default. PostgreSQL 18.4 listens only on
`127.0.0.1:5432`, with database and local administrator user `edge_relative` by
default. Set `POSTGRES_PORT` in `.env` if port 5432 is already in use. Database
sessions and logs default to UTC.

The default JDBC URL is `jdbc:postgresql://127.0.0.1:5432/edge_relative`. Spring Boot
uses `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and
`POSTGRES_PASSWORD` from its environment. With the `local` profile, host, port,
database, and user have the same defaults as `.env.example`; the backend also
retains the development password fallback `edge_relative` when the password
variable is absent. Compose still requires an explicit password, which must match
the backend's value. Without the `local` profile, all five variables are required.
Application connections explicitly use UTC. Flyway migrations, generated jOOQ classes, and
persistence adapters will be added with the first database schema.
The Compose user is a local development administrator, not a production app role.

Open a SQL shell or stop the service:

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose down
```

Data survives container recreation and `docker compose down` in the named
`postgres_data` volume. Initialization variables (database, user, and password)
only take effect on an empty volume; editing `.env` does not change an existing
database's credentials. `docker compose down --volumes` deletes the local database
and should only be used when deliberately resetting disposable development data.

## Backend

Run from `backend/`:

```sh
./mvnw verify
```

Start PostgreSQL using the root Compose instructions above, then launch the backend
from `backend/`, exporting your local configuration in a subshell:

```sh
(
  set -a
  . ../.env
  set +a
  java -jar application/target/application-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
)
```

Keep `.env` values shell-compatible; single-quote passwords containing special
characters. When launching `EdgeRelativeApplication` from IntelliJ, supply these
same environment variables and set the active profile to `local` in the run
configuration (or set `SPRING_PROFILES_ACTIVE=local`). Spring Boot does not load
`.env` automatically. Shared settings live in `application.yaml`; local database
defaults live in `application-local.yaml`.

The application binds to `127.0.0.1:8080`. Its only exposed Actuator endpoint is
`http://127.0.0.1:8080/actuator/health`. Health includes database connectivity and
returns HTTP 503 / `DOWN` when the database is unavailable; connection details
remain hidden. `UP` does not mean trading is permitted. Tests cover PostgreSQL
configuration, jOOQ connectivity, UTC sessions, HTTP health, and the domain
dependency boundary; the architecture rule permits the empty domain until
implementation is added.

## Frontend

Run from `frontend/`:

```sh
pnpm install --frozen-lockfile
pnpm dev
```

Use the local URL printed by Vite. The page is static and explicitly not connected;
starting the backend does not connect the frontend to it.

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

`pnpm format` applies Prettier formatting. See `frontend/README.md` for details.

## Research

Run from `research/`:

```sh
uv sync --locked
uv run pytest
uv run ruff check .
uv run ruff format --check .
```

The package currently contains only an import smoke test. Python never owns
authoritative production trading state.

## Development Rules

Read `AGENTS.md` and the relevant design sections before implementing features.
Preserve measurement, strategy, ML, risk, and execution boundaries. Research
examples are not production defaults; no scaffold setting grants trading authority.
Keep secrets out of Git. Local `.env` files are ignored and loaded by Docker
Compose, but are not automatically loaded by the backend, frontend, or research
application.
