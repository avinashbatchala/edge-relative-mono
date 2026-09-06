# Edge Relative

An algorithmic trading platform for Indian equities, initially focused on NSE and
a single operator. This repository is an engineering scaffold, not a trading system.
It has no market feed, strategy implementation, risk engine, broker integration,
database, or live order capability.

## Structure

- `backend/`: Java 25 Maven reactor with a framework-free `domain` module and a
  Spring Boot `application` module. The domain is intentionally empty.
- `frontend/`: Vue 3 / TypeScript / Vite operator-workstation placeholder.
- `research/`: Python src-layout package managed with uv, without trading logic.
- `design-docs/`: product, strategy, risk, stack, and data specifications.
- `.github/workflows/ci.yml`: independent backend, frontend, and research checks.

Add further modules, contracts, SQL Flyway migrations, jOOQ adapters, and deployment
infrastructure when their first concrete use case is implemented. The larger
directory tree in DD-04A is conceptual, not a requirement to create empty modules.

## Prerequisites

- JDK 25; set `JAVA_HOME` accordingly. Other major JDKs are rejected by the build.
- Node 24 LTS (at least 24.15.0) and pnpm 10.34.5.
- uv 0.12.10 and Python 3.13 (uv can provision Python).

Maven 3.9.11 is downloaded by the checked-in Maven Wrapper. First-time dependency
installation requires network access. Docker and PostgreSQL are not needed yet.

## Backend

Run from `backend/`:

```sh
./mvnw verify
java -jar application/target/application-0.1.0-SNAPSHOT.jar
```

The application binds to `127.0.0.1:8080`. Its only exposed Actuator endpoint is
`http://127.0.0.1:8080/actuator/health`. `UP` means the scaffold is running, not that
trading is permitted. Tests cover HTTP health and the domain dependency boundary;
the architecture rule permits the empty domain until implementation is added.

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
Keep secrets out of Git. Local `.env` files are ignored but are not automatically
loaded by these applications.
