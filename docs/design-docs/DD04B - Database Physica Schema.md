# Edge Relative — Database Physical Schema & Persistence Model

**Document:** DD-04B  
**Version:** 1.0  
**Status:** Baseline Physical Schema  
**Database:** PostgreSQL  
**Access:** jOOQ + Flyway  
**Analytical Storage:** Parquet + Object Storage

---

# 1. Purpose

This document turns the logical data model in DD-01 through DD-05 into a concrete PostgreSQL physical schema.

It intentionally separates:

```text
PostgreSQL
├── reference     global/temporal market reference data
├── control       immutable algorithm/policy/model versions
├── market        sparse relational anchors and market-data quality state
├── operational   authoritative trading workflow + mutable projections
├── research      dataset/pattern/experiment metadata
└── audit         append-only audit envelope

Object Storage / Parquet
├── raw trades / quotes / depth
├── canonical candles
├── wide feature snapshots
├── pattern sequences
├── embeddings
├── future outcome labels
└── backtest / ML datasets
```

The database is not the long-term warehouse for every market event or feature value.

---

# 2. Physical Design Decisions

## 2.1 PostgreSQL Owns Financial Authority

PostgreSQL is authoritative for:

- tenant/account state;
- strategy/risk/model deployment authority;
- setup observations used by the live trading workflow;
- risk decisions;
- trade plans;
- order intent and OMS state;
- fills;
- trade and position projections;
- risk reservations;
- reconciliation state;
- audit events;
- dataset/model lineage metadata.

## 2.2 Parquet Owns Large Analytical History

Large append-oriented data remains outside the operational database:

```text
raw tick history
quote history
market depth history
M1/M5/etc. historical candles
wide feature matrices
pattern sequence tensors
embeddings
outcome matrices
training datasets
```

PostgreSQL stores manifests and sparse join anchors, not an EAV feature warehouse.

## 2.3 Position Is a Projection, Not Financial Evidence

The authority chain is:

```text
OrderRecord
    ↓
immutable Fill ledger
    ↓
Trade projection
    ↓
Position projection
    ↓
Broker reconciliation
```

`operational.position_projection` is deliberately mutable and rebuildable from fills.

## 2.4 Risk Decision Precedes Trade Plan

Canonical lineage:

```text
MarketObservation
      ↓
SetupObservation
      ↓
ModelPrediction [optional]
      ↓
RiskDecision
      ↓
TradePlan
      ↓
Trade
      ↓
OrderRecord
      ↓
Fill
```

A rejected `RiskDecision` therefore has no `TradePlan`.

A database trigger prevents a TradePlan from being inserted unless its RiskDecision is `APPROVE` or `REDUCE` and prevents the plan from exceeding the approved quantity/risk.

## 2.5 Durable Intent Exists Before Broker Side Effects

The execution workflow must persist an internal order row with state `CREATED` before the external broker request occurs.

Typical transaction:

```text
BEGIN

RiskDecision
TradePlan
Trade
RiskReservation
OrderRecord(CREATED)
AuditEvent

COMMIT

submit order to broker
```

Broker timeouts must resolve through the durable `client_order_reference`, broker identifiers, order events, and reconciliation rather than blind retries.

## 2.6 Risk Approval Is Concurrency-Safe by Design

`operational.risk_account_state` is the per-account/per-session concurrency guard.

Risk approval should lock it:

```sql
SELECT ...
FROM operational.risk_account_state
WHERE broker_account_id = ?
  AND trading_date = ?
FOR UPDATE;
```

Inside the same transaction the application:

1. calculates current capacity;
2. emits immutable `RiskDecision`;
3. persists `TradePlan` where approved;
4. creates `RiskReservation`;
5. increments reserved risk/notional in `risk_account_state`.

This serializes capital-authority changes for one broker account while leaving market-data/feature processing concurrent.

---

# 3. Identifier Policy

## 3.1 Relational Primary Keys

Dense database primary keys use:

```text
BIGINT GENERATED ALWAYS AS IDENTITY
```

Reasons:

- compact B-tree indexes;
- efficient FK joins;
- good jOOQ ergonomics;
- high write locality.

## 3.2 Stable Cross-System Keys

Objects that cross process/storage/API boundaries also carry a UUID supplied by the application.

Examples:

```text
observation_key
setup_observation_key
decision_key
trade_plan_key
trade_key
client_order_reference
fill_key
dataset_version_key
pattern_key
run_key
```

The application should generate time-ordered UUIDs where practical. The database does not depend on broker IDs as primary keys.

## 3.3 Broker Identity

Broker identifiers remain external references:

```text
internal_order_id     Edge Relative authority
client_order_reference Edge Relative idempotency key
broker_order_id       external broker reference
broker_fill_id        external fill reference
broker_trade_id       external trade reference
```

Unique partial indexes protect against duplicate broker orders/fills where stable IDs are supplied.

---

# 4. Numeric Policy

Authoritative prices/money use fixed-point PostgreSQL numeric types.

| Value | Physical Type |
|---|---|
| Price / stop / target | `NUMERIC(20,8)` |
| Money / risk / P&L / notional | `NUMERIC(24,8)` |
| Quantity | `BIGINT` |
| Indicator/statistical values | `DOUBLE PRECISION` |
| Probability | `DOUBLE PRECISION` with `[0,1]` check |
| Flexible parameter/config payloads | `JSONB` |

The production Java domain should use `BigDecimal` or an explicitly scaled value object for authoritative money.

Feature calculations and ML values may use `double` where DD-04A permits it.

---

# 5. Timestamp Policy

All machine instants use:

```text
TIMESTAMPTZ
```

Trading-calendar dates use:

```text
DATE
```

Exchange semantics are resolved through the exchange's configured timezone, initially `Asia/Kolkata` for NSE.

The host/server timezone must not define market behavior.

---

# 6. Temporal Reference Data

Point-in-time reference tables use half-open ranges:

```text
[valid_from, valid_to)
```

PostgreSQL generated `daterange` / `tstzrange` columns and GiST exclusion constraints prevent overlapping mappings.

This is used for:

- instrument identifiers;
- broker instrument tokens;
- instrument → sector mapping;
- sector → benchmark mapping;
- benchmark membership;
- universe membership;
- strategy → risk-policy assignment;
- strategy → model deployment.

This is necessary for historical replay and ML dataset reconstruction without look-ahead.

---

# 7. `reference` Schema

## 7.1 Tables

```text
exchange
broker
timeframe
instrument
instrument_identifier
derivative_contract
broker_instrument_mapping
sector
instrument_sector_history
benchmark
sector_benchmark_history
benchmark_constituent_history
universe
universe_membership_history
trading_calendar_day
trading_session
corporate_action
market_data_source
```

## 7.2 Instrument Identity

`reference.instrument` is the canonical economic/security identity used by trading logic.

Broker tokens never escape their mapping boundary.

Symbol history and identifiers are separate because a symbol can change while the economic instrument remains the same.

## 7.3 Derivatives

Derivative-only attributes are held in `derivative_contract` rather than adding nullable expiry/strike/option columns to every equity/index row.

## 7.4 Corporate Actions

Corporate actions are first-class reference data because historical candles, features and pattern matching must not silently cross splits/bonuses/mergers without known adjustment semantics.

---

# 8. `control` Schema

## 8.1 Tables

```text
feature_definition
feature_version
feature_version_state
feature_schema
feature_schema_version
feature_schema_member
strategy
strategy_version
risk_policy
risk_policy_version
model
model_version
```

## 8.2 Definitions vs Versions

Conceptual identity and immutable implementation are separate.

```text
Strategy
 ├─ V1
 ├─ V2
 └─ V3
```

The same applies to feature and risk-policy definitions.

A semantic/version row is immutable once written. Runtime enablement belongs to deployment/assignment tables rather than mutating the version itself.

## 8.3 Feature Schemas

A `feature_schema_version` is the exact set of compatible feature versions that forms one wide feature snapshot shape.

Example:

```text
FEATURE_SET_V7
├── RRS_V3
├── RVOL_INTERVAL_V2
├── RVE_V1
├── ATR_V1
└── VWAP_DISTANCE_V2
```

This is what a Parquet feature dataset references.

---

# 9. `market` Schema

## 9.1 Tables

```text
market_observation
market_observation_revision
market_data_incident
ingestion_checkpoint
```

## 9.2 Sparse Observation Registry

`market.market_observation` is intentionally **not** a row-for-every-tick warehouse.

It is a sparse relational registry for market points that need durable relational lineage, such as:

- live setup anchors;
- model/risk/trade decision anchors;
- selected/indexed historical pattern anchors.

The broad historical M1/M5 universe remains in Parquet.

Semantic observation identity is:

```text
instrument_id
+ timeframe_id
+ bar_close_timestamp
```

`observation_key` provides a stable cross-store key.

## 9.3 Corrections

The base observation is immutable.

Corrections/quality changes create `market_observation_revision` rows. Exactly one revision may be marked canonical.

---

# 10. `operational` Schema

## 10.1 Identity and Accounts

```text
tenant
app_user
broker_account
watchlist
watchlist_item
```

The watchlist's `slot` is constrained to `1..20` and unique per watchlist, enforcing the product's 20-stock maximum without a counting trigger.

Broker secrets are not stored directly. `secret_reference` points to the external secret-management mechanism.

## 10.2 Deployment Authority

```text
strategy_deployment
risk_policy_assignment
model_deployment
```

Model authority is constrained to:

```text
OBSERVER
RANKER
FILTER
RISK_REDUCER
```

There is intentionally no model authority that can exceed deterministic risk limits.

## 10.3 Decision Lineage

```text
setup_observation
model_prediction
risk_context_snapshot
risk_decision
risk_decision_reason
trade_plan
```

`setup_observation` is append-only: a setup changing from `FORMING` to `NEAR_TRIGGER` to `VALID` produces new observations rather than rewriting history.

`risk_decision` is immutable and carries the key approved/requested values necessary for explanation.

Multiple structured reason rows are supported so a decision can say, for example:

```text
approved from base 140 shares
reduced by sector concentration
reduced by remaining daily capacity
final 95 shares
```

## 10.4 Risk Context and Capacity

```text
portfolio_snapshot
portfolio_position_snapshot
risk_account_state
risk_context_snapshot
risk_reservation
```

Snapshots are immutable evidence.

`risk_account_state` is mutable, compact current state used for concurrency control and low-latency risk evaluation.

`risk_reservation` prevents two simultaneously approved orders from both consuming the same remaining risk budget.

## 10.5 Execution

```text
trade
order_record
order_event
fill
position_projection
```

`order_record` is the mutable OMS projection.

`order_event` and `fill` are append-only evidence.

The database validates that:

- orders belong to the specified trade/plan/account/instrument;
- fills belong to the specified order/trade/account/instrument/side;
- TradePlans do not exceed their RiskDecision.

## 10.6 Reconciliation and Control

```text
reconciliation_run
reconciliation_item
trading_control_state
```

Reconciliation mismatch is structured operational state rather than only a log message.

`trading_control_state` is the current projection for kill-switch/new-entry/automation authority; every material change should also emit an `audit_event`.

---

# 11. `research` Schema

## 11.1 Dataset Catalog

```text
dataset
dataset_version
dataset_version_input
```

A dataset version records:

- immutable version identity;
- schema version;
- point-in-time cutoff;
- storage URI;
- partition manifest URI;
- row count;
- checksum;
- code version;
- exact parent dataset versions.

A dataset that reaches `COMMITTED` cannot have its contents silently changed. It may later become `RETIRED`.

## 11.2 Pattern Metadata

```text
pattern_schema
pattern_schema_version
pattern_window_metadata
similarity_index_metadata
```

Large sequence tensors remain in Parquet/object storage.

`pattern_window_metadata` is for materialized/indexed windows and contains Stage-1 filter columns such as:

```text
instrument
sector as of anchor
market regime
sector regime
timeframe
minutes since open
anchor timestamp
```

This supports:

```text
millions of patterns
    ↓ metadata filter
thousands of candidates
    ↓ ANN / vector search
dozens of neighbors
    ↓ exact sequence re-ranking
best historical analogues
```

`similarity_index_metadata` records the corpus cutoff and checksum so a historical replay cannot accidentally query future patterns.

## 11.3 Outcome Labels

```text
outcome_schema
outcome_schema_version
outcome_definition
outcome_dataset
```

Feature and outcome datasets remain logically and physically separate.

## 11.4 Experiments

```text
experiment
experiment_run
backtest_run
training_run
model_candidate
```

Python may write research results/candidates, but production authority comes only from a promoted `control.model_version` plus an `operational.model_deployment`.

---

# 12. `audit` Schema

`audit.audit_event` is an append-only envelope containing:

```text
event_type
event_version
aggregate_type
aggregate_reference
actor
correlation_id
causation_id
event_timestamp
payload
```

Important financial workflows should share a correlation ID from setup/risk through execution.

The audit log is not used as a replacement for the OMS event ledger. `order_event` remains the execution-specific event history.

---

# 13. Immutability Classification

| Entity | Physical Semantics |
|---|---|
| `feature_version` | Immutable |
| `feature_schema_version` | Immutable |
| `strategy_version` | Immutable |
| `risk_policy_version` | Immutable |
| `model_version` | Immutable |
| `market_observation` | Immutable identity |
| `market_observation_revision` | Append-only revision history |
| `portfolio_snapshot` | Immutable |
| `setup_observation` | Append-only |
| `model_prediction` | Immutable |
| `risk_context_snapshot` | Immutable |
| `risk_decision` | Immutable |
| `trade_plan` | Immutable |
| `order_event` | Append-only |
| `fill` | Append-only |
| `audit_event` | Append-only |
| `order_record` | Mutable projection |
| `trade` | Mutable projection |
| `position_projection` | Mutable derived projection |
| `risk_account_state` | Mutable current state / lock target |
| `risk_reservation` | Mutable reservation state |
| `reconciliation_run/item` | Mutable until resolution |
| `dataset_version` | Mutable while building; immutable after commit |

Database triggers enforce the most important append-only/immutable cases.

---

# 14. Key Indexes

The baseline DDL includes indexes for the known operational queries:

```text
active instruments by exchange/status
broker token resolution
historical sector/universe mappings
market observation by instrument/time
setup history by strategy/instrument
valid setups
risk decisions by account/session
trade plans by account/instrument
open trades by account
active risk reservations
orders by account/status
orders by trade/plan
fills by order/trade
non-flat positions
reconciliation runs/items
pattern lookup by stock/regime/sector/time-of-day
ready similarity indexes
experiments/model candidates
aggregate/correlation audit lookup
```

Do not add generic indexes to every column. Additional indexes should be justified by measured query plans.

---

# 15. PostgreSQL Partitioning

The baseline intentionally does **not** partition the operational tables yet.

Reasons:

- operational row counts are initially modest;
- premature partitioning makes uniqueness/FK/index management harder;
- high-volume market history already belongs in Parquet;
- DD-05 requires representative benchmarks before fixing physical partition strategy.

If `setup_observation`, `audit_event`, or selected pattern metadata later becomes large enough to require partitioning, introduce it through a measured migration.

---

# 16. Parquet Physical Boundaries

Recommended logical datasets:

```text
market/raw/trade
market/raw/quote
market/raw/depth
market/candle
feature/snapshot
pattern/window
pattern/embedding
label/outcome
backtest/output
ml/training
```

Do not derive the permanent partition key from today's 20-stock watchlist.

Partition/bucketing must be benchmarked with the DD-05 query suite before being frozen.

---

# 17. Critical Transaction Boundaries

## 17.1 Risk Approval + Reservation

```text
BEGIN
LOCK risk_account_state
create risk_context_snapshot
create risk_decision
create trade_plan (if approved)
create risk_reservation
update risk_account_state
create audit event
COMMIT
```

## 17.2 Broker Submission

```text
BEGIN
create trade if needed
create order_record(CREATED)
create audit event
COMMIT

call broker

BEGIN
append order_event
update order_record projection
COMMIT
```

## 17.3 Fill Processing

```text
BEGIN
insert immutable fill
append order_event
update order projection
update trade projection
update position projection
update/release risk reservation
update risk_account_state
create audit event
COMMIT
```

## 17.4 Reconciliation

```text
BEGIN
create reconciliation_run/items
update control/risk state if mismatch is unsafe
create audit event
COMMIT
```

---

# 18. Query/Write Ownership

Recommended database identities remain:

```text
edge_migrations
edge_java
edge_python
```

### Java

Read/write:

```text
operational
market runtime metadata
audit append
```

Read:

```text
reference
control
research manifests/results
```

### Python

Read:

```text
reference
control
market
operational observations
```

Read/write:

```text
research
```

Python must not receive write authority over production orders, fills, risk decisions, trade plans, position state, or deployment authority.

A grants template is included with the migrations but intentionally commented because roles should be provisioned by infrastructure/IAM.

---

# 19. Tables Deliberately Not Created

The baseline does **not** create relational tables for:

```text
one row per raw tick
one row per quote update
one row per feature per observation
one row per pattern sequence step
one row per historical outcome observation
```

Those belong in Parquet/object storage.

It also does not introduce:

```text
Cassandra
TimescaleDB
pgvector
FAISS-specific tables
```

Those remain benchmark-driven additions.

---

# 20. Migration Layout

```text
db/migration/
├── V001__extensions_and_schemas.sql
├── V002__reference.sql
├── V003__control.sql
├── V004__market_catalog.sql
├── V005__operational.sql
├── V006__research.sql
├── V007__audit_and_cross_schema_constraints.sql
└── R__grants_template.sql
```

Flyway executes the versioned migrations in order. jOOQ generation should run only after Flyway has built the schema used for code generation.

---

# 21. First Development Sequence After Schema Approval

Once this physical model is accepted:

```text
1. Run migrations in Testcontainers PostgreSQL
2. Generate jOOQ classes
3. Add schema-level integration tests
4. Seed NSE exchange/broker/timeframes
5. Implement Instrument + Calendar repositories
6. Implement market observation registry
7. Implement historical/canonical data pipeline
8. Implement feature schema/version registry
9. Continue replay/features/strategy development
```

Before adding the first trading feature, integration tests should verify the critical database invariants:

```text
no overlapping sector mappings
no overlapping broker-token mappings
max 20 watchlist slots
rejected risk decision cannot create trade plan
trade plan cannot exceed approved risk/quantity
order lineage cannot cross account/trade/instrument
fill lineage cannot cross order/trade/account/instrument
fills cannot be updated/deleted
risk decisions cannot be updated/deleted
committed datasets cannot be silently rewritten
```

---

# 22. Items Intentionally Left for Benchmarking

The physical schema fixes the relational model, but these remain measured decisions:

- exact Parquet partition/bucketing layout;
- target Parquet file size;
- whether observation IDs in historical matrices use UUID, bigint, or both;
- whether broad historical `market_observation` rows should ever be fully materialized in PostgreSQL;
- whether setup/audit/pattern metadata later needs PostgreSQL partitioning;
- vector/ANN technology;
- specialized read replicas;
- exact retention/archival policy for high-volume operational telemetry.

---

# 23. Foundational Persistence Invariant

> **Immutable evidence is never replaced by a mutable projection; every capital-changing action is traceable to the exact market observation, strategy version, risk policy, decision, trade intent, order and fill that produced it; large analytical history remains reconstructable and point-in-time correct without turning the operational PostgreSQL database into the market-data warehouse.**

---

# 24. Amendment: Canonical Candle Storage (Scoped)

> **Amendment date:** 2026-09-20. See `docs/design-docs/dev/ADR-001-candle-storage.md`.

For the current single-operator, <50-instrument scope, **PostgreSQL is the
authoritative canonical candle store**. Only M1 is persisted; higher timeframes
are derived in process by the shared deterministic aggregator. This is a
deliberate, bounded exception to §2.2/§16 ("M1/M5/... candles in Parquet"), with
documented deferral triggers in ADR-001. The DD-05 candle contract and lineage
rules (§91–§106) still apply logically and must be modelled in the PostgreSQL
store so a later move to Parquet is physical, not semantic.

Migrations added since the original list in §21:

```text
db/migration/
├── V008__history_candles.sql
├── V009__drop_non_m1_persisted_history.sql
├── V010__candle_contract_and_revisions.sql
├── V011__timeframe_registry_and_ingestion.sql
├── V012__purge_misclocked_m1_history.sql
└── R__grants_template.sql
```

The `market` schema additionally contains two ingestion-support tables not
enumerated in §13, documented here as intended schema:

```text
market.candle_coverage   durable per (instrument, timeframe, range) backfill unit
market.ingestion_run     requested backfill range, progress and outcome
```

Both reference `reference.market_data_source`. Data-quality failures may be
recorded in the existing `market.market_data_incident`.

# 25. Amendment: Feature Snapshot Storage (Scoped)

> **Amendment date:** 2026-09-20. See `docs/design-docs/dev/ADR-002-feature-snapshot-storage.md`.

For the current single-operator, <50-instrument scope, derived feature snapshots are
persisted in PostgreSQL as a bounded exception to §19. Migration
`V013__feature_engine.sql` adds:

```text
market.feature_snapshot         one row per instrument/timeframe/anchor/schema/calculation version
market.feature_snapshot_value   one row per feature value (quality, availability, parameter hash)
```

Rows are append-only (`BEFORE UPDATE OR DELETE` triggers reject mutation) and are
rebuildable from canonical candles by the deterministic feature engine. The
calculation path never reads these tables; broad feature matrices still belong in
Parquet/object storage under the §19 triggers.
