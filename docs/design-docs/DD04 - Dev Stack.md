# Edge Relative — Development Stack & Engineering Standards

**Document:** DD-04A  
**Version:** 1.0  
**Status:** Foundational Technical Decision  
**Product:** Edge Relative  
**Scope:** Development stack, engineering standards, build tooling, persistence technology, frontend, research environment, communication protocols, testing and CI/CD  
**Related Document:** DD-01 — Product & Feature Design Specification

---

# 1. Purpose

This document defines the initial development technology stack and engineering conventions for Edge Relative.

The objective is not to select the theoretically fastest or most sophisticated technology for every subsystem.

The objective is to build a stack that is:

-   reliable;
    
-   deterministic;
    
-   maintainable;
    
-   performant;
    
-   observable;
    
-   testable;
    
-   easy to operate;
    
-   easy to hire for;
    
-   suitable for quantitative research;
    
-   suitable for low-latency trading decisions;
    
-   capable of evolving into a larger multi-user platform.
    

The stack should minimize unnecessary infrastructure complexity so engineering effort can remain concentrated on the areas that create actual product value:

-   market data;
    
-   quantitative features;
    
-   trading strategies;
    
-   risk;
    
-   execution;
    
-   broker reliability;
    
-   backtesting;
    
-   research;
    
-   machine learning;
    
-   operational safety.
    

DD-01 establishes the fundamental system separation:

```text
Measurement
    ↓
Strategy
    ↓
ML
    ↓
Risk
    ↓
Execution
```

and requires deterministic behavior, simulation/live parity, auditability and complete decision lineage.

This stack is designed around those requirements.

---

# 2. Core Technology Philosophy

The primary engineering philosophy is:

> **Use mainstream technologies for infrastructure and application development. Spend complexity only where the trading problem requires it.**

Edge Relative should avoid introducing technology merely because it may become useful at some hypothetical future scale.

The initial platform does not require:

-   hundreds of microservices;
    
-   distributed databases;
    
-   Kubernetes;
    
-   service meshes;
    
-   distributed caches;
    
-   large streaming platforms;
    
-   specialized HFT infrastructure.
    

The system initially serves one operator and actively analyzes no more than 20 stocks.

The architecture should nevertheless establish clean boundaries so individual modules can later become independent services if operational requirements justify doing so.

---

# 3. High-Level Stack

The initial stack is:

```text
                         EDGE RELATIVE

                            MONOREPO
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
      JAVA                   PYTHON                  VUE
        │                      │                      │
   Spring Boot             Quant Research       TypeScript
        │                  Statistics / ML           │
        │                                             │
 Modular Monolith                                   Vite
        │                                             │
        │                                           Pinia
        │
 Framework-light
 Trading Domain
        │
       jOOQ
        │
   PostgreSQL
        │
        └──────────────► Parquet / Object Storage
                              │
                         Research / ML
```

---

# 4. Repository Strategy

Edge Relative will use a:

> **Monorepo**

The repository contains all primary application code, research code, frontend code, infrastructure definitions, schemas and design documentation.

Conceptual structure:

```text
edge-relative/
│
├── backend/
│   ├── pom.xml
│   │
│   ├── domain/
│   ├── market-data/
│   ├── features/
│   ├── strategy/
│   ├── ml-inference/
│   ├── risk/
│   ├── portfolio/
│   ├── orders/
│   ├── execution/
│   ├── broker-api/
│   ├── broker-zerodha/
│   ├── broker-groww/
│   ├── persistence/
│   ├── backtest/
│   ├── journal/
│   └── application/
│
├── research/
│   ├── pyproject.toml
│   ├── uv.lock
│   ├── src/
│   ├── notebooks/
│   ├── experiments/
│   ├── ml/
│   └── tests/
│
├── frontend/
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── vite.config.ts
│   └── src/
│
├── contracts/
│   ├── openapi/
│   ├── proto/
│   ├── websocket/
│   └── fixtures/
│
├── infra/
│   └── terraform/
│
├── docker/
│
├── scripts/
│
├── .github/
│   └── workflows/
│
└── docs/
```

A monorepo does not mean that all code belongs to one architectural component.

It means:

> Multiple well-defined codebases share one source-control and change-management boundary.

---

# 5. Why a Monorepo

The primary benefits are:

-   atomic cross-stack changes;
    
-   synchronized backend/frontend contracts;
    
-   synchronized Java/Python feature definitions;
    
-   shared test fixtures;
    
-   simpler early-stage development;
    
-   one pull request for one conceptual change;
    
-   centralized CI/CD;
    
-   centralized architectural documentation.
    

For example, changing:

```text
RRS_V2
    ↓
RRS_V3
```

might affect:

-   Java feature implementation;
    
-   Python research implementation;
    
-   persisted schema;
    
-   API models;
    
-   frontend presentation;
    
-   tests;
    
-   documentation.
    

A monorepo allows this to remain one coordinated change.

---

# 6. Production Backend Language

The primary production language is:

> **Java**

Java will own production trading authority.

Production responsibilities include:

-   market-data processing;
    
-   canonical feature calculation;
    
-   deterministic strategy execution;
    
-   live ML inference where practical;
    
-   risk decisions;
    
-   portfolio state;
    
-   OMS;
    
-   execution;
    
-   broker integration;
    
-   reconciliation;
    
-   journal generation;
    
-   production-grade backtesting;
    
-   system health and recovery.
    

---

# 7. Java Runtime

Target runtime:

> **Java 25 LTS**

All important environments should use the same major JDK:

```text
Development
CI
Backtest
Paper
Shadow
Production
```

Runtime differences between validation and production should be minimized.

---

# 8. Application Framework

The primary Java application framework is:

> **Spring Boot**

Spring Boot will provide:

-   dependency injection;
    
-   application lifecycle;
    
-   externalized configuration;
    
-   REST APIs;
    
-   security;
    
-   transaction management;
    
-   database integration;
    
-   metrics;
    
-   health checks;
    
-   observability;
    
-   scheduling;
    
-   external service integration.
    

Spring Boot was selected primarily because it provides:

-   mature ecosystem support;
    
-   broad developer familiarity;
    
-   a large hiring pool;
    
-   strong maintainability;
    
-   extensive integration capabilities;
    
-   long-term organizational scalability.
    

---

# 9. Spring Does Not Own the Trading Domain

The trading domain should remain as framework-independent as practical.

Core classes should not require:

```text
Spring MVC
JPA
ApplicationContext
HTTP
JSON
Kafka
WebSocket
```

to perform trading calculations.

For example:

```java
public interface FeatureEngine {
    FeatureSnapshot update(MarketEvent event);
}

public interface StrategyEngine {
    Setup evaluate(FeatureSnapshot snapshot);
}

public interface RiskEngine {
    RiskDecision evaluate(TradeCandidate candidate);
}
```

The objective is:

> **Spring operates the application. Ordinary Java operates the trading algorithm.**

---

# 10. Backend Architecture

The initial backend architecture is:

> **Modular monolith**

There is initially one primary deployable trading application.

Modules remain strongly separated.

Conceptually:

```text
Spring Boot Application

├── Market Data
├── Features
├── Strategy
├── ML Inference
├── Risk
├── Portfolio
├── Orders / OMS
├── Execution
├── Brokers
├── Persistence
├── Backtesting
├── Journal
└── API
```

A module may later become an independent service if a concrete operational requirement appears.

---

# 11. Module Separation

Examples of prohibited dependencies:

```text
strategy
    X→ Zerodha

features
    X→ execution

risk
    X→ Spring MVC

domain
    X→ PostgreSQL-specific implementation

broker adapter
    X→ strategy rules
```

Desired dependency direction:

```text
Infrastructure
      ↓
Application
      ↓
Domain
```

rather than:

```text
Domain
      ↓
Framework
```

---

# 12. Architecture Enforcement

Architecture will not rely solely on documentation.

Module dependencies should be enforced through:

-   Maven module dependencies;
    
-   ArchUnit tests;
    
-   package rules;
    
-   CI validation.
    

A violation such as:

```text
strategy → broker-zerodha
```

should fail the build.

---

# 13. Hot Trading Path

The latency-sensitive decision path should remain in process.

Conceptually:

```text
Market Event
     ↓
FeatureEngine.update()
     ↓
StrategyEngine.evaluate()
     ↓
MLScorer.score()
     ↓
RiskEngine.evaluate()
     ↓
TradePlan
     ↓
ExecutionEngine
```

These are ordinary Java method calls.

No unnecessary:

-   HTTP;
    
-   gRPC;
    
-   WebSocket;
    
-   database round-trip;
    
-   serialization;
    
-   external queue.
    

This is essential for both latency and deterministic behavior.

---

# 14. Reactive Programming

Project Reactor remains part of the technology toolbox.

It should be used selectively.

Good use cases include:

-   broker WebSocket feeds;
    
-   market-data streams;
    
-   asynchronous external I/O;
    
-   bounded stream transformations;
    
-   event ingestion.
    

Reactor should not automatically propagate through every trading-domain method.

Avoid architectures where ordinary deterministic calculations unnecessarily become:

```java
Mono<FeatureSnapshot>
    .flatMap(...)
    .flatMap(...)
```

The core trading path should remain explicit and easy to profile.

---

# 15. Concurrency Philosophy

The system should favor:

> **Explicit concurrency over ubiquitous concurrency.**

Important mutable trading state should preferably have a controlled ownership model.

A single-writer/event-loop architecture should be investigated for major state domains.

Conceptually:

```text
Incoming Events
      ↓
Bounded Queue
      ↓
Trading Event Loop
      ↓
Market State
Features
Strategy
Risk
Portfolio
```

Benefits include:

-   deterministic ordering;
    
-   fewer races;
    
-   fewer locks;
    
-   easier replay;
    
-   easier debugging;
    
-   predictable latency.
    

---

# 16. Virtual Threads

Virtual threads may be used for naturally blocking I/O such as:

-   REST calls;
    
-   broker APIs;
    
-   historical-data requests;
    
-   administrative operations;
    
-   database access where appropriate.
    

They are not considered a replacement for carefully controlled low-latency event processing.

---

# 17. Persistence Technology

Primary operational database:

> **PostgreSQL**

PostgreSQL will own authoritative operational and transactional state.

Examples include:

-   users;
    
-   broker accounts;
    
-   instruments;
    
-   strategies;
    
-   feature definitions;
    
-   risk policies;
    
-   trade plans;
    
-   risk decisions;
    
-   orders;
    
-   fills;
    
-   positions;
    
-   journals;
    
-   model registry metadata;
    
-   audit events;
    
-   configuration.
    

---

# 18. Data Access

The standard persistence abstraction is:

> **jOOQ**

JPA/Hibernate will not be introduced initially.

Architecture:

```text
PostgreSQL
    ↓
Flyway
    ↓
jOOQ generated schema
    ↓
Persistence adapters
    ↓
Domain/application ports
```

jOOQ is preferred because Edge Relative is expected to contain significant:

-   relational querying;
    
-   aggregation;
    
-   temporal querying;
    
-   execution analysis;
    
-   research extraction;
    
-   analytical SQL;
    
-   batch persistence;
    
-   PostgreSQL-specific functionality.
    

The system intentionally embraces SQL rather than hiding SQL behind ORM semantics.

---

# 19. Domain Objects Are Not Database Objects

A domain object such as:

```java
TradePlan
```

does not become a database entity merely because it must be persisted.

The domain should expose an interface:

```java
interface TradePlanRepository {
    void save(TradePlan plan);
    Optional<TradePlan> find(TradePlanId id);
}
```

and the persistence module implements it using jOOQ.

This protects the trading domain from database implementation details.

---

# 20. Plain JDBC

Plain JDBC is allowed only when justified.

Typical potential use cases:

-   extremely high-volume batch ingestion;
    
-   `COPY` operations;
    
-   specialized PostgreSQL functionality;
    
-   measured jOOQ overhead in a proven hot persistence path.
    

Plain JDBC should not become the default merely for theoretical performance.

---

# 21. Transactions

Spring transaction management remains available.

jOOQ does not remove ACID transactional behavior.

Example:

```text
BEGIN

RiskDecision
TradePlan
AuditEvent

COMMIT
```

may execute within one transaction.

Financial state transitions should use transactions deliberately.

---

# 22. Database Migration

Schema migration tool:

> **Flyway**

Migrations should primarily be SQL.

Example:

```text
db/migration/

V001__instrument.sql
V002__strategy.sql
V003__feature_definition.sql
V004__risk_policy.sql
V005__trade_plan.sql
```

The database schema itself is treated as version-controlled product code.

---

# 23. Database Ownership Between Java and Python

Java and Python may initially share the same PostgreSQL infrastructure.

They do not share unrestricted write ownership.

Recommended schemas:

```text
operational
reference
research
```

Java owns production trading state.

Python may read production observations but should not mutate authoritative trading state.

Example:

```text
Java:
operational.orders → READ/WRITE

Python:
operational.orders → READ
```

Python owns research-oriented tables such as:

-   experiments;
    
-   training runs;
    
-   research results;
    
-   candidate models;
    
-   analysis outputs.
    

---

# 24. Separate Database Credentials

Use separate identities:

```text
edge_java
edge_python
edge_migrations
```

Permissions should enforce architecture rather than relying solely on developer discipline.

---

# 25. Historical and Analytical Storage

PostgreSQL should not become the long-term warehouse for every market event.

Large historical and ML-oriented datasets should gradually move toward:

> **Parquet + object storage**

Examples:

-   historical candles;
    
-   historical ticks;
    
-   feature datasets;
    
-   ML training datasets;
    
-   outcome datasets;
    
-   backtest exports.
    

Conceptually:

```text
PostgreSQL
    → operational state

Parquet/Object Storage
    → historical analytical state
```

---

# 26. Cassandra

Cassandra is not part of the initial stack.

It may be reconsidered only if a future append-heavy dataset demonstrates requirements such as:

-   billions of events per day;
    
-   massive horizontal write scale;
    
-   multi-region ingestion;
    
-   known partition-key access patterns;
    
-   PostgreSQL demonstrably becoming unsuitable.
    

Cassandra would supplement PostgreSQL for a specialized workload rather than automatically replace PostgreSQL.

---

# 27. Redis

Redis is not part of the initial stack.

It may be introduced if a demonstrated requirement appears for:

-   distributed caching;
    
-   shared ephemeral state;
    
-   rate limiting;
    
-   distributed coordination;
    
-   short-lived high-speed lookup.
    

The initial modular monolith does not require distributed cache infrastructure.

---

# 28. Messaging Infrastructure

Kafka, Redpanda, Pulsar, RabbitMQ and similar systems are not initial dependencies.

Within the modular monolith:

```text
direct method call
```

is preferred when synchronous communication is sufficient.

Internal events may initially use:

-   in-process queues;
    
-   persisted event records;
    
-   explicit asynchronous workers.
    

A distributed message broker should be introduced only when actual independent processes need durable asynchronous communication.

---

# 29. Persistence as a Side Effect

For reconstructable feature data, persistence should usually remain outside the hot decision path.

Example:

```text
Market Event
    ↓
Calculate RRS
    ↓
Return/use RRS immediately
    ↓
Strategy continues

             └──→ async persistence queue
                      ↓
                 batch writer
                      ↓
                   database
```

This applies to data such as:

-   RRS;
    
-   RVOL;
    
-   RVE;
    
-   intermediate feature observations;
    
-   telemetry;
    
-   many feature snapshots.
    

---

# 30. Persistence Durability Levels

Persistence should be classified.

## Ephemeral

May not need persistence.

Examples:

-   temporary UI-derived values;
    
-   transient intermediate calculations.
    

## Recoverable

May be persisted asynchronously.

Examples:

-   feature snapshots;
    
-   statistical observations;
    
-   opportunity scores.
    

## Critical

Must use explicit durability guarantees.

Examples:

-   risk approval;
    
-   order intent;
    
-   broker submission;
    
-   fills;
    
-   position state;
    
-   kill-switch changes;
    
-   manual overrides.
    

---

# 31. External Financial Side Effects

The system should not submit irreversible financial actions before required authoritative intent has been recorded.

Avoid:

```text
Send BUY
    ↓
persist intent later
```

Preferred model:

```text
Create intent
    ↓
Durably record intent
    ↓
Submit broker request
    ↓
Record response
    ↓
Reconcile
```

Correctness takes priority over microseconds around external financial actions.

---

# 32. Queue Policy

Asynchronous persistence queues must be bounded.

Never assume:

```text
database will always keep up
```

Every queue needs an explicit overflow policy.

For low-value observations:

```text
drop
+
metric
+
alert
```

may be acceptable.

For critical trading state:

```text
DO NOT DROP
```

and inability to persist may require disabling new trading.

---

# 33. Numeric Representation

Financial and statistical values require explicit representation rules.

Recommended policy:

Value

Representation

Account money

BigDecimal or scaled integer

Fees/taxes

BigDecimal or scaled integer

Quantity

integer/long

Hot-path prices

investigate scaled integer/ticks

Statistical indicators

double

ML features

double

Probabilities

double

PostgreSQL financial values

NUMERIC or minor-unit integer

Do not casually represent authoritative account money as floating-point `double`.

---

# 34. Time Representation

Machine timestamps should use:

> **UTC / Instant**

Exchange-calendar semantics should explicitly use:

> **Asia/Kolkata**

Examples:

```java
Instant exchangeTimestamp;
Instant brokerTimestamp;
Instant receivedTimestamp;
Instant processedTimestamp;
```

Exchange logic uses:

```java
ZoneId.of("Asia/Kolkata")
```

The host server timezone must never implicitly determine trading behavior.

DD-01 explicitly requires exchange, broker, received and processed timestamps.

---

# 35. Clock Abstraction

Trading-domain code should not directly call:

```java
Instant.now()
```

throughout the codebase.

Instead use an injected:

```text
Clock
```

or trading-clock abstraction.

Live mode:

```text
SystemClock
```

Backtest/replay:

```text
ReplayClock
```

This allows the same trading logic to run against real and historical time.

---

# 36. Research Language

Research and machine learning use:

> **Python**

Python is not the authoritative production trading runtime.

Its primary responsibilities are:

-   exploratory quantitative research;
    
-   statistics;
    
-   feature research;
    
-   strategy experimentation;
    
-   Monte Carlo;
    
-   data analysis;
    
-   ML training;
    
-   ML evaluation;
    
-   visualization;
    
-   notebooks.
    

---

# 37. Python Tooling

Python package/environment management:

> **uv**

Recommended stack:

```text
Python
├── uv
├── NumPy
├── Polars
├── Pandas where useful
├── SciPy
├── statsmodels
├── scikit-learn
├── matplotlib
├── Jupyter
├── pytest
├── Hypothesis
└── Ruff
```

Additional ML libraries may be introduced when specific models require them.

---

# 38. Python Research vs Production Logic

Python may explore candidate trading rules rapidly.

Canonical production rules should ultimately be implemented in Java.

The system should avoid maintaining two divergent production strategy definitions.

Desired process:

```text
Python hypothesis
    ↓
Research
    ↓
Formal strategy specification
    ↓
Java canonical implementation
    ↓
Production-grade backtest
    ↓
Paper
    ↓
Live
```

---

# 39. Shared Java/Python Fixtures

Important feature definitions should have canonical test fixtures.

Example:

```text
contracts/fixtures/rrs-v2/
```

Both Java and Python implementations should produce equivalent outputs for shared test cases within defined numerical tolerances.

This reduces research/production drift.

---

# 40. Production Backtesting

The canonical production-grade backtester should be Java-based and reuse production domain logic.

Example:

```text
Historical MarketEvent
        ↓
Production Feature Engine
        ↓
Production Strategy
        ↓
Production Risk Engine
        ↓
Simulation Execution Adapter
```

This supports the simulation/live parity required by DD-01.

Python remains suitable for fast exploratory backtesting and statistical analysis.

---

# 41. Machine Learning

ML training primarily occurs in Python.

Production inference should preferably run inside Java when the selected model can be safely and efficiently deployed there.

Preferred:

```text
Python training
      ↓
model artifact
      ↓
Java inference
```

A separate Python inference service should only be introduced when model/runtime constraints justify the additional network boundary.

---

# 42. Frontend Framework

The frontend framework is:

> **Vue 3**

Vue was selected intentionally over React.

Frontend stack:

```text
Vue 3
TypeScript
Composition API
<script setup>
Vite
Pinia
pnpm
```

The frontend is an operator workstation, not part of the trading authority boundary.

---

# 43. Frontend State Separation

Frontend state should be classified.

## Server State

Examples:

-   strategies;
    
-   journal;
    
-   historical trades;
    
-   broker configuration;
    
-   watchlist configuration.
    

Use:

> TanStack Query where useful.

## Real-Time State

Examples:

-   LTP;
    
-   RRS;
    
-   RVOL;
    
-   RVE;
    
-   P&L;
    
-   position state;
    
-   order updates;
    
-   broker health;
    
-   opportunity ranking.
    

Use:

> Pinia / dedicated realtime store.

## Local UI State

Examples:

-   selected stock;
    
-   modal state;
    
-   active tab;
    
-   chart timeframe.
    

Use normal Vue component/reactive state.

---

# 44. Real-Time Frontend Updates

Raw broker event frequency should not automatically equal browser rendering frequency.

Conceptually:

```text
Broker feed
    ↓
Trading engine processes required events
    ↓
Backend consolidates UI state
    ↓
WebSocket updates
    ↓
Vue
```

The browser should receive enough updates to feel live without processing unnecessary raw market traffic.

---

# 45. Frontend Tables

The active watchlist initially contains no more than 20 stocks.

A lightweight table solution is sufficient.

Avoid introducing large enterprise-grid complexity until actual requirements justify it.

---

# 46. Financial Charts

Preferred initial financial chart library:

> **TradingView Lightweight Charts**

Use cases include:

-   candlesticks;
    
-   volume;
    
-   RRS;
    
-   RVOL;
    
-   RVE;
    
-   overlays;
    
-   multiple panes.
    

Licensing/attribution requirements must be respected.

---

# 47. Frontend Authority

The frontend must never become authoritative for:

-   position size;
    
-   valid strategy state;
    
-   permitted risk;
    
-   order completion;
    
-   broker position truth;
    
-   kill-switch enforcement.
    

Example:

```text
Browser:
Request EXIT

Backend:
Validate
Execute
Track fills
Reconcile
Confirm flat

Browser:
Display authoritative result
```

---

# 48. HTTP API

External/browser request-response APIs use:

> **HTTPS + JSON**

Examples:

```text
GET /api/watchlist
GET /api/positions

POST /api/watchlist
POST /api/positions/{id}/exit
POST /api/autopilot/stop
```

---

# 49. REST Contract

HTTP APIs should use:

> **OpenAPI**

TypeScript API clients/types should be generated where appropriate.

Backend implementation objects should not automatically become frontend contracts.

Explicit API DTOs are preferred.

---

# 50. WebSocket

WebSocket is used for:

-   browser live state;
    
-   broker live market feeds when provided;
    
-   broker order-update streams when provided.
    

It is not the default communication mechanism between internal application modules.

---

# 51. WebSocket Message Contracts

WebSocket payloads should have explicit schemas.

Example:

```json
{
  "type": "position.update",
  "version": 1,
  "sequence": 88234,
  "payload": {}
}
```

Messages should not become undocumented arbitrary JSON.

---

# 52. WebSocket Sequence Handling

Where live-state correctness matters, messages should include ordering information.

For example:

```text
100
101
104
```

allows the client to detect that messages were missed.

Recovery may then request a fresh authoritative state snapshot.

---

# 53. Internal Communication

Within the modular monolith:

> **Direct Java calls**

No serialization or network protocol should be introduced between:

```text
Features
Strategy
ML
Risk
Execution
```

unless one of those components becomes a separate process for a concrete reason.

---

# 54. Future Service-to-Service Communication

If modules later become separate processes:

### Synchronous RPC

Prefer:

> **gRPC + Protobuf**

### Asynchronous events

Use an event broker appropriate to the actual future requirements.

WebSocket should not become an improvised general-purpose internal RPC framework.

---

# 55. JSON vs Protobuf

Use JSON where human interaction and debugging matter.

Use Protobuf where machine-to-machine strongly typed communication matters.

Recommended:

Boundary

Format

Browser REST

JSON

Browser WebSocket

JSON initially

Broker

Broker-defined

Internal Java

Native objects

Future gRPC

Protobuf

Java↔Python RPC

Protobuf

Large analytical datasets

Parquet

---

# 56. Protobuf Contracts

The monorepo may contain:

```text
contracts/proto/
```

for future cross-language contracts such as:

-   MarketEvent;
    
-   FeatureSnapshot;
    
-   ModelPrediction;
    
-   RiskDecision;
    
-   OrderEvent.
    

Protobuf-generated classes should not automatically become the internal domain model.

Translate at boundaries.

---

# 57. Build System — Java

Java build system:

> **Maven**

Use:

> **Maven Wrapper**

Example:

```bash
./mvnw verify
```

A Maven multi-module project will enforce Java module structure.

Maven is preferred primarily for:

-   Spring ecosystem familiarity;
    
-   team maintainability;
    
-   predictable conventions;
    
-   future Java hiring.
    

---

# 58. Build System — Python

Python build/package tool:

> **uv**

Typical commands:

```bash
uv sync
uv run pytest
uv run ruff check .
```

---

# 59. Build System — Frontend

Frontend package manager:

> **pnpm**

Build tooling:

> **Vite**

Typical commands:

```bash
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

---

# 60. Monorepo Orchestration

Do not initially introduce:

-   Nx;
    
-   Turborepo;
    
-   Bazel.
    

Each ecosystem should retain its natural tooling.

CI coordinates them.

Additional orchestration tooling may be introduced only when the repository becomes sufficiently large to justify it.

---

# 61. Containers

Use:

> **Docker**

Application builds should produce immutable OCI container images.

Use Docker primarily for:

-   deployment artifacts;
    
-   local infrastructure;
    
-   integration testing;
    
-   repeatable environments.
    

---

# 62. Local Development

Use:

> **Docker Compose**

for supporting infrastructure.

Typical local workflow:

```bash
docker compose up -d

./mvnw spring-boot:run

pnpm dev
```

Python remains independently available through:

```bash
uv sync
```

Local development should closely resemble CI behavior.

---

# 63. Infrastructure as Code

Infrastructure provisioning uses:

> **Terraform**

Cloud-provider selection remains a separate infrastructure decision.

Infrastructure should not be configured manually where reproducible Terraform is practical.

---

# 64. CI/CD Platform

CI/CD platform:

> **GitHub Actions**

The monorepo should use path-aware CI while respecting shared dependencies.

Example:

```text
backend/**
    → Java CI

research/**
    → Python CI

frontend/**
    → Vue CI

contracts/**
    → affected consumers
```

---

# 65. Java CI

Typical Java CI pipeline:

```text
Compile
    ↓
Formatting/static analysis
    ↓
Unit tests
    ↓
Architecture tests
    ↓
Integration tests
    ↓
Database migration tests
    ↓
Trading replay tests
    ↓
Build artifact
```

---

# 66. Python CI

Typical Python CI:

```text
Dependency sync
    ↓
Lint
    ↓
Type checks where used
    ↓
Unit tests
    ↓
Property tests
```

Research notebooks are not substitutes for tested Python packages.

---

# 67. Frontend CI

Typical frontend CI:

```text
Install
    ↓
Lint
    ↓
Typecheck
    ↓
Unit/component tests
    ↓
Build
    ↓
E2E tests where applicable
```

---

# 68. Build Once, Promote

Production artifacts must follow:

```text
Commit
    ↓
Build
    ↓
Test
    ↓
Immutable container image
    ↓
Paper
    ↓
Shadow
    ↓
Live
```

The same artifact should be promoted between environments.

Do not rebuild different binaries separately for paper and production.

---

# 69. Deployment Identity

Deployment should ultimately identify images by immutable digest:

```text
sha256:...
```

Convenience tags may exist, but production should know the exact artifact running.

---

# 70. Live Deployment Policy

Live deployment is not treated like ordinary web SaaS deployment.

Production deployment should be a controlled trading-system operation.

Initially:

```text
Paper
    → automated deployment acceptable

Live
    → protected/manual approval
```

Avoid casual deployment during active NSE trading unless operational procedures explicitly permit it.

---

# 71. Single Execution Authority

Do not deploy multiple simultaneously active execution engines without explicit distributed fencing.

Until a robust leader/fencing model exists:

> **Exactly one process owns authority to place live orders.**

Running two live replicas must never result in:

```text
Instance A → BUY

Instance B → BUY
```

for the same intent.

---

# 72. Restart/Deployment Recovery

A live process should follow a recovery lifecycle similar to:

```text
Trading Disabled
    ↓
Authenticate Broker
    ↓
Load State
    ↓
Restore Market Data
    ↓
Warm JVM
    ↓
Read Broker Orders
    ↓
Read Broker Positions
    ↓
Reconcile
    ↓
Restore Protection
    ↓
Run Health Checks
    ↓
Enable Trading
```

This aligns with the restart-recovery requirements in DD-01.

---

# 73. Java Testing Stack

Recommended:

```text
JUnit 5
AssertJ
Mockito where appropriate
Testcontainers
ArchUnit
JMH
jqwik where useful
WireMock or broker simulator
```

---

# 74. Integration Testing

Integration tests should use real PostgreSQL through Testcontainers.

Do not substitute H2 for PostgreSQL behavior where database semantics matter.

Broker integration tests should use controllable broker simulators capable of producing:

-   timeouts;
    
-   rejection;
    
-   partial fills;
    
-   duplicate callbacks;
    
-   delayed responses;
    
-   stale feeds;
    
-   disconnections.
    

---

# 75. Property-Based Testing

Financial rules are especially suitable for property testing.

Example invariant:

```text
Final permitted risk
    <=
Configured maximum risk
```

should hold across generated scenarios.

Other examples:

-   position quantity cannot become negative unless shorting rules permit it;
    
-   executed quantity cannot exceed order quantity;
    
-   portfolio exposure cannot exceed hard limits;
    
-   invalid setups never become live trades due solely to ML.
    

---

# 76. Performance Testing

Use:

> **JMH**

for Java microbenchmarks.

Candidate benchmarks:

-   feature update;
    
-   RRS calculation;
    
-   RVOL calculation;
    
-   strategy evaluation;
    
-   risk evaluation;
    
-   position sizing.
    

Metrics should include:

-   execution time;
    
-   allocation rate;
    
-   throughput.
    

Larger end-to-end replay tests should measure latency distributions such as:

```text
p50
p95
p99
p99.9
```

---

# 77. Deterministic Replay Tests

Canonical test streams should prove:

```text
Same events
+
same feature version
+
same strategy version
+
same parameters

=

same decisions
```

This is a core Edge Relative invariant.

---

# 78. Frontend Testing

Recommended:

```text
Vitest
Vue Testing Library
Playwright
```

Critical operator workflows need E2E testing, including:

-   trade approval;
    
-   manual exit;
    
-   cancel order;
    
-   kill switch;
    
-   broker disconnected state;
    
-   partial fill;
    
-   order rejection;
    
-   stale market data;
    
-   position mismatch.
    

---

# 79. Static Analysis — Java

Initial tooling may include:

-   Spotless;
    
-   Maven Enforcer;
    
-   compiler warnings;
    
-   ArchUnit.
    

Additional tools such as NullAway/Error Prone may be evaluated later.

---

# 80. Static Analysis — Python

Use:

-   Ruff;
    
-   type checker such as Pyright or mypy where useful;
    
-   pytest;
    
-   Hypothesis.
    

---

# 81. Static Analysis — Frontend

Use:

-   ESLint;
    
-   Prettier;
    
-   vue-tsc;
    
-   TypeScript strict mode where practical.
    

Formatting and linting should be CI-enforced.

---

# 82. Observability

Application observability should use vendor-neutral instrumentation.

Recommended foundation:

```text
Micrometer
+
OpenTelemetry
+
Structured Logs
```

Potential backend stack:

```text
Prometheus → metrics
Grafana    → dashboards
OpenTelemetry → tracing
Loki/equivalent → logs
```

Exact observability hosting may evolve independently.

---

# 83. Trading Metrics

Important metrics include:

-   market-data latency;
    
-   feature latency;
    
-   strategy latency;
    
-   ML latency;
    
-   risk latency;
    
-   broker request latency;
    
-   broker acknowledgement latency;
    
-   persistence queue depth;
    
-   stale-feed age;
    
-   WebSocket reconnects;
    
-   order rejection rate;
    
-   reconciliation failures.
    

These align with the observability requirements in DD-01.

---

# 84. Operational Logs vs Audit Events

Operational logs and trading audit events must remain separate concepts.

Operational log:

```text
broker connection timeout
```

Audit event:

```text
RISK_APPROVED
ORDER_SUBMITTED
ORDER_FILLED
KILL_SWITCH_ACTIVATED
```

Trading auditability must not depend solely on log retention.

---

# 85. Configuration

Spring configuration should primarily use:

```java
@ConfigurationProperties
```

rather than scattering raw configuration access throughout the codebase.

Separate categories include:

-   infrastructure configuration;
    
-   broker configuration;
    
-   strategy configuration;
    
-   risk configuration;
    
-   secrets.
    

---

# 86. Strategy Configuration

Trading configuration affects historical meaning.

Therefore strategy/risk parameters should be:

-   versioned;
    
-   auditable;
    
-   immutable for historical decisions.
    

Examples:

```text
strategy_version
risk_policy_version
feature_version
model_version
```

should be associated with generated trade decisions.

---

# 87. Secrets

Broker credentials and other secrets must not be stored in Git.

Local development may use ignored local environment configuration.

Production should use:

-   cloud secret manager;
    
-   Vault-equivalent;
    
-   managed secure secret storage.
    

Secret access should follow least privilege.

---

# 88. Dependency Locking

Use explicit lock/version mechanisms:

```text
Maven Wrapper
Spring Boot dependency management
uv.lock
pnpm-lock.yaml
```

Avoid uncontrolled floating dependency versions.

---

# 89. Automated Dependency Updates

Use:

-   Renovate;
    
-   or Dependabot.
    

Automated updates should still pass full CI before merging.

Critical production dependencies should not be updated blindly.

---

# 90. Supply-Chain Security

CI should gradually include:

-   secret scanning;
    
-   dependency vulnerability scanning;
    
-   container scanning;
    
-   SBOM generation;
    
-   artifact provenance/attestation.
    

This is particularly relevant because the application controls financial accounts.

---

# 91. Security Boundary

The backend is the authoritative security and trading boundary.

The frontend should never be trusted to enforce:

-   maximum risk;
    
-   position limits;
    
-   broker permissions;
    
-   strategy eligibility;
    
-   kill-switch state;
    
-   trade validity.
    

Every enforcement decision is repeated authoritatively on the backend.

---

# 92. Technologies Explicitly Deferred

The following are intentionally not part of V1 unless a concrete requirement appears:

```text
Kubernetes
Kafka
Redis
Cassandra
Elasticsearch
Service Mesh
API Gateway
Distributed Cache
Separate Feature Store Product
Workflow Engine
Microservices
Native-image trading runtime
```

The absence of these technologies is intentional.

---

# 93. Kubernetes Policy

Kubernetes may become appropriate when Edge Relative requires:

-   many independently deployed services;
    
-   substantial orchestration;
    
-   autoscaling;
    
-   complex multi-tenant workloads;
    
-   high-availability service fleets.
    

A single-user modular monolith does not currently justify Kubernetes.

---

# 94. Microservice Extraction Policy

Modules should become separate services only when there is a concrete reason.

Valid reasons include:

-   independent scaling;
    
-   independent failure isolation;
    
-   independent deployment lifecycle;
    
-   different runtime/language requirement;
    
-   dedicated resource requirements;
    
-   multiple independent consumers.
    

Invalid reason:

> "This is a separate domain, therefore it must be a microservice."

---

# 95. Potential Future Service Candidates

Likely future extraction candidates include:

### Market Data

If data volume or number of consumers grows substantially.

### ML Inference

If models require a Python-specific runtime.

### Broker Connectivity

If multiple brokers/accounts need independently managed connection lifecycles.

### Historical Data Platform

If ingestion/research becomes operationally independent.

### Execution

Only when strong fencing and authority controls are mature.

---

# 96. Engineering Optimization Order

When performance issues arise, optimize in this order:

```text
Measure
    ↓
Identify bottleneck
    ↓
Fix architecture/data movement
    ↓
Fix algorithm/data structure
    ↓
Fix allocations/concurrency
    ↓
Tune JVM
    ↓
Consider specialized technology
```

Do not begin by replacing frameworks or languages without measurements.

---

# 97. Latency Philosophy

Edge Relative is latency-sensitive but is not initially an HFT platform. DD-01 explicitly lists HFT as a V1 non-goal.

The primary latency target should be:

> **Fast and predictable internal decisions with strong tail-latency control.**

Track:

```text
MarketEvent → normalized event
MarketEvent → feature update
MarketEvent → strategy decision
MarketEvent → risk decision
Risk approval → broker submission
Broker submission → acknowledgement
```

---

# 98. Resilience Over Micro-Optimization

A trading system that evaluates a signal in 20 μs but occasionally duplicates orders is inferior to one that evaluates it in 100 μs and maintains perfect order authority.

Priority:

```text
Correctness
    ↓
Resilience
    ↓
Determinism
    ↓
Observability
    ↓
Latency
    ↓
Throughput
```

while remaining within actual trading requirements.

---

# 99. Final Technology Stack

The initial approved stack is:

Layer

Technology

Repository

Monorepo

Production language

Java 25 LTS

Application framework

Spring Boot

Architecture

Modular monolith

Trading core

Framework-light Java

Reactive I/O

Project Reactor selectively

Persistence abstraction

jOOQ

Operational DB

PostgreSQL

DB migration

Flyway

Research language

Python

Python tooling

uv

Analytical format

Parquet

Analytical storage

Object storage

Frontend

Vue 3

Frontend language

TypeScript

Vue style

Composition API + `<script setup>`

Frontend state

Pinia

HTTP server state

TanStack Query where useful

Frontend build

Vite

Frontend package manager

pnpm

HTTP API

HTTPS + JSON

HTTP schema

OpenAPI

Realtime frontend

WebSocket + JSON

Future internal RPC

gRPC

Future RPC schema

Protobuf

Java build

Maven Wrapper

Containers

Docker

Local infra

Docker Compose

Infrastructure as code

Terraform

CI/CD

GitHub Actions

Java tests

JUnit 5 / AssertJ / Testcontainers

Architecture testing

ArchUnit

Java benchmarking

JMH

Python testing

pytest / Hypothesis

Frontend testing

Vitest / Playwright

Metrics

Micrometer

Tracing

OpenTelemetry

Logging

Structured logging

---

# 100. Overall Architecture Summary

The complete initial development environment can be represented as:

```text
                         EDGE RELATIVE
                              │
                         MONOREPO
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
       BACKEND             RESEARCH            FRONTEND
          │                   │                   │
        Java                Python               Vue 3
          │                   │                   │
     Spring Boot              uv              TypeScript
          │                   │                   │
          │              Statistics / ML          Vite
          │                                       │
   Modular Monolith                              Pinia
          │
          │
   Framework-light
    Trading Core
          │
          ▼
 Market → Features → Strategy → ML → Risk → Execution
          │
          │
          ├───────────────► Async observations
          │
          ▼
        jOOQ
          │
      PostgreSQL
          │
          └───────────────► Parquet/Object Storage
                                  │
                               Python
```

Frontend communication:

```text
Vue
 │
 ├── HTTPS + JSON ──► commands/queries
 │
 └── WebSocket ─────► live state
```

Broker communication:

```text
Java Broker Adapter
 │
 ├── HTTPS ──────► broker commands
 │
 └── WebSocket ─► broker live data/order updates
```

Internal trading communication:

```text
Feature
  ↓
Strategy
  ↓
ML
  ↓
Risk
  ↓
Execution

Direct Java calls
```

---

# 101. Final Stack Principle

The development stack can be summarized as:

> **Mainstream technologies at the edges.  
> Explicit Java in the trading core.  
> PostgreSQL for authoritative state.  
> Python for research and ML.  
> Vue for the operator workstation.  
> Networks only where real process boundaries exist.  
> Asynchronous persistence where data is reconstructable.  
> Strong durability where money is affected.  
> Complexity introduced only when measured requirements demand it.**

This stack should remain stable through the initial research, workstation, assisted-live and guarded-autopilot phases unless concrete engineering evidence demonstrates that one of the selected technologies cannot meet the required reliability, maintainability or performance characteristics.

# Architecture Componentes

DD-04 SYSTEM ARCHITECTURE │ ├── Context ├── Components ├── Module Boundaries ├── Dependency Direction ├── State Ownership ├── Runtime Processing Model ├── Concurrency Model ├── Commands ├── Events ├── Internal Contracts ├── External APIs ├── Transaction Boundaries ├── Consistency Guarantees ├── Idempotency ├── State Machines ├── Failure Semantics ├── Recovery ├── Reconciliation ├── Security Boundaries ├── Deployment Topology └── Logical Data Model │ ▼ DD-04B OPERATIONAL DATA MODEL & DATABASE DESIGN │ ├── PostgreSQL Schemas ├── Tables ├── Complete DDL ├── PK/FK Strategy ├── Constraints ├── Unique Keys ├── Indexes ├── Optimistic Locking ├── Transaction Mapping ├── Event Ledger Tables ├── Outbox Tables ├── Audit Tables ├── Retention ├── Partitioning ├── Permissions ├── Migration Rules └── Flyway Organization