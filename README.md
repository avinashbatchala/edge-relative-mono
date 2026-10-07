# Edge Relative

An algorithmic trading platform for Indian equities, initially focused on NSE and
a single operator. This repository is an engineering scaffold, not a trading system.
It has no market feed, strategy implementation, risk engine, or live order capability.

## Structure

- `backend/`: Java 25 Maven reactor with a framework-free `domain` module, a
  framework-free `broker-api` module (broker-neutral ports and models), a Spring Boot
  `broker-groww` adapter, and a Spring Boot `application` module with PostgreSQL
  connectivity through jOOQ. The domain is intentionally empty.
- `broker-groww`: implements Groww read-only capabilities. All broker-side mutations are
  exposed as Edge Relative contracts but return `BROKER_OPERATION_NOT_ENABLED` and make
  zero downstream requests. See `docs/design-docs/dev/groww-endpoint-matrix.md`.
- `llm-api` / `llm-deepseek`: framework-free advisory LLM port plus the DeepSeek adapter.
  The Java backend is the only process that holds the key; `/api/v1/llm/narration` returns
  advisory text and never carries trading authority.
- `fundamentals-api` / `fundamentals-nse`: framework-free fundamental ports plus the Yahoo
  NSE/BSE (`.NS`/`.BO`) provider, stored point-in-time and served by `/api/v1/fundamentals`.
  Advisory only; see `docs/design-docs/DD06 - Fundamental Analysis.md`.
- `frontend/`: Vue 3 / TypeScript / Vite operator-workstation placeholder.
- `research/`: Python src-layout package managed with uv, without trading logic.
- `compose.yaml`: local PostgreSQL with persistent storage.
- `docs/design-docs`: product, strategy, risk, stack, data, and fundamental specifications,
  with accepted ADRs (including candle/feature storage, fundamentals, and LLM).
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
service and a local `secrets.properties` file are not required for tests.

## Local PostgreSQL

Run from the repository root:

```sh
cp secrets.properties.example secrets.properties
# Set POSTGRES_PASSWORD (and Groww credentials) in secrets.properties.
docker compose --env-file secrets.properties up -d --wait postgres
docker compose --env-file secrets.properties ps
```

Compose loads the git-ignored `secrets.properties` via `--env-file`; Spring imports
the same file. The password is required and has no default. PostgreSQL 18.4 listens
only on `127.0.0.1:5432`, with database and local administrator user `edge_relative`
by default. Set `POSTGRES_PORT` in `secrets.properties` if port 5432 is already in
use. Database sessions and logs default to UTC.

The default JDBC URL is `jdbc:postgresql://127.0.0.1:5432/edge_relative`. Spring Boot
uses `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and
`POSTGRES_PASSWORD` from its environment. With the `local` profile, host, port,
database, and user have the same defaults as `secrets.properties.example`; the backend also
retains the development password fallback `edge_relative` when the password
variable is absent. Compose still requires an explicit password, which must match
the backend's value. Without the `local` profile, all five variables are required.
Application connections explicitly use UTC. Flyway migrations, generated jOOQ classes, and
persistence adapters will be added with the first database schema.
The Compose user is a local development administrator, not a production app role.

Open a SQL shell or stop the service:

```sh
docker compose --env-file secrets.properties exec postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose --env-file secrets.properties down
```

Data survives container recreation and `docker compose down` in the named
`postgres_data` volume. Initialization variables (database, user, and password)
only take effect on an empty volume; editing `secrets.properties` does not change an
existing database's credentials. `docker compose --env-file secrets.properties down
--volumes` deletes the local database and should only be used when deliberately
resetting disposable development data.

## Backend

Run from `backend/`:

```sh
./mvnw verify
```

Start PostgreSQL using the root Compose instructions above, then launch the backend
from `backend/`:

```sh
java -jar application/target/application-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Spring Boot imports the git-ignored `secrets.properties` directly through
`spring.config.import`, searching the working directory and its parents, so no shell
`source` is required. Exported environment variables override it, which is useful for
CI and containers. An optional import means the app still starts without the file.
When launching `EdgeRelativeApplication` from IntelliJ, set the active profile to
`local` (or set `SPRING_PROFILES_ACTIVE=local`); the repo-root `secrets.properties`
is found in a parent directory. Shared settings live in `application.yaml`; local
database defaults live in `application-local.yaml`.

## Groww credentials

Put Groww credentials in a git-ignored `secrets.properties` at the repository root
(copy `secrets.properties.example`). The adapter boots without credentials; read-only
Groww endpoints remain unavailable until valid ones are supplied, but no secret is
required to start the application. `GROWW_AUTH_MODE` defaults to `AUTO`, which uses
whichever credentials are set, so the key/secret flow needs only two lines:

```properties
GROWW_AUTH_MODE=AUTO
GROWW_API_KEY=...
GROWW_API_SECRET=...
```

Other flows: `GROWW_ACCESS_TOKEN` for a static token (expires 06:00 IST), or
`GROWW_API_KEY` plus `GROWW_TOTP_CODE` for TOTP. Setting `GROWW_AUTH_MODE`
explicitly overrides `AUTO` when its credential is present; a stale `ACCESS_TOKEN`
mode is ignored if only an API key/secret are configured.

Never commit real credentials. `secrets.properties` is git-ignored and holds both
local database and Groww secrets; Spring imports it and Compose reads it via
`--env-file`. `application.yaml` uses empty placeholders so a missing secret cannot
leak or block startup. Broker-side mutations return `BROKER_OPERATION_NOT_ENABLED`
and never call Groww.

The same file holds the advisory LLM key (`LLM_API_KEY`, with `DEEPSEEK_*` settings)
and the fundamentals source selector (`FUNDAMENTALS_*`). The Java backend is the only
process that holds the LLM key: it is never placed in a frontend `VITE_*` variable or
bundled into an app. The application boots without a key and reports narration as
unavailable rather than failing.

The application binds to `127.0.0.1:8080`. Interactive API docs are available at
`http://127.0.0.1:8080/swagger-ui/index.html` and the raw spec at
`http://127.0.0.1:8080/v3/api-docs`. The Swagger UI lets you exercise the read-only
Groww endpoints directly; mutation endpoints are listed but return
`501 BROKER_OPERATION_NOT_ENABLED`. Credentials come from the environment (see
below), so a page refresh cannot leak them. Its only exposed Actuator endpoint is
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

The package contains the point-in-time research layer and deterministic fundamental
valuation operators (WACC, DCF, DDM, comparable multiples, ratios) under
`src/edge_relative_research/fundamentals/`, sharing frozen fixtures with Java in
`contracts/fixtures/fundamentals`. Python never owns authoritative production trading state.

## Development Rules

Read `AGENTS.md` and the relevant design sections before implementing features.
Preserve measurement, strategy, ML, risk, and execution boundaries. Research
examples are not production defaults; no scaffold setting grants trading authority.
Keep secrets out of Git. Local `secrets.properties` is git-ignored, imported by the
backend at startup, and used by Docker Compose via `--env-file`; it is not read by
the frontend or research application.
