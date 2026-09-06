The overall separation is right:

```text
PostgreSQL
├── reference/configuration
├── transactional trading state
├── research/catalog metadata
└── audit/lineage

Object Storage / Parquet
├── raw market history
├── canonical time series
├── feature matrices
├── pattern sequences
└── future outcome labels
```

The Parquet decision, avoidance of EAV, point-in-time mappings, immutable versions, and durable financial intent are particularly good. They align with the PostgreSQL+jOOQ/Parquet architecture we already chose.

There are, however, several things I would change before DDL.

---

# 1. `tenant / user / broker_account` do not belong in `reference`

This is the first structural problem.

Your description says:

> reference = slowly changing, read-heavy, highly cacheable global reference data.

But these are not global reference objects:

```text
tenant
app_user
broker_account
```

They are mutable, security-sensitive, tenant-owned operational entities.

In particular, `broker_account` changes state:

```text
authentication
token status
connection status
permissions
account status
margin configuration
```

I would use:

```text
reference
    exchange
    broker
    instrument
    sector
    benchmark
    calendar
    corporate actions
    temporal mappings

operational
    tenant
    app_user
    broker_account
```

And importantly:

> **Do not store broker secrets themselves in `broker_account`.**

Store something like:

```text
secret_reference
```

pointing to the secret manager.

---

# 2. Separate definitions from versions

Instead of:

```text
strategy_definition
-------------------
strategy_id
version
parameters
deployment_status
```

I strongly recommend:

```text
strategy
--------
strategy_id
name
description

strategy_version
----------------
strategy_version_id
strategy_id
version
parameters
created_at
code_version
```

Likewise:

```text
feature_definition
    ↓
feature_version
```

and:

```text
risk_policy
    ↓
risk_policy_version
```

The distinction is important.

`RS_CONTINUATION` is the conceptual strategy.

```text
RS_CONTINUATION_V1
RS_CONTINUATION_V2
RS_CONTINUATION_V3
```

are immutable implementations.

Deployment state should probably be separate again:

```text
strategy_deployment
-------------------
strategy_version_id
environment
state
enabled_from
enabled_until
```

Otherwise changing:

```text
PAPER → LIVE
```

mutates what is supposed to be an immutable historical definition.

---

# 3. We need a `feature_schema` / `feature_set_version`

You currently have:

```text
feature_definition
feature_version
calculation_version
```

That's necessary, but DD-05 requires another concept.

A historical feature row may represent:

```text
RRS_V3
RVOL_V2
ATR_V1
VWAP_DISTANCE_V2
...
```

We need to know the **exact collection of feature versions represented by a Parquet dataset**.

I would introduce:

```text
feature_schema
feature_schema_version
feature_schema_member
```

Conceptually:

```text
FEATURE_SET_V7

RRS              V3
RVOL             V2
RVE              V1
ATR               V1
VWAP_DISTANCE     V2
SECTOR_RRS        V1
...
```

Then:

```text
feature/snapshot/
    feature_schema_version = V7
```

has precise meaning.

This will become extremely important for ML reproducibility.

---

# 4. Your `risk_decision → trade_plan` relationship is backwards

This is the most important relational correction.

You currently have:

```text
risk_decision
    trade_plan_id
```

But our architecture is:

```text
SetupObservation
        ↓
RiskDecision
        ↓
TradePlan
        ↓
Order
```

DD-01 explicitly establishes this lineage.

A rejected risk decision has **no TradePlan**.

Therefore:

```text
risk_decision
-------------
risk_decision_id
setup_observation_id
risk_policy_version_id
decision
approved_risk
approved_quantity
reason
...
```

then:

```text
trade_plan
----------
trade_plan_id
risk_decision_id
...
```

with something like:

```text
UNIQUE(risk_decision_id)
```

for the simple V1 model.

So:

```text
Setup
  │
  ▼
RiskDecision: REJECT
```

ends there.

Whereas:

```text
Setup
  │
  ▼
RiskDecision: APPROVE
  │
  ▼
TradePlan
```

continues.

---

# 5. `trade_plan` should preserve much more lineage

Your current fields are good but insufficient.

I would expect something closer to:

```text
trade_plan
----------
trade_plan_id

tenant_id
broker_account_id
instrument_id

setup_observation_id
strategy_version_id
risk_decision_id

direction
instrument_type

entry_method
entry_reference

structural_invalidation
protective_stop

target_method
target_reference

planned_quantity

maximum_planned_loss
expected_cost
expected_slippage

market_observation_id

created_at
activated_at
```

The original TradePlan should become effectively immutable once execution starts.

---

# 6. `position` should NOT be authoritative

This is the second major correction.

Your schema says:

> `position` — Authoritative portfolio state.

I would change this.

The authority hierarchy should be:

```text
Immutable Fills
      ↓
Internal Position Projection
      ↓
Broker Reconciliation
```

So:

```text
fill
```

is authoritative evidence of what Edge Relative believes executed.

```text
position
```

is a **derived operational projection**.

Otherwise a bug that accidentally runs:

```sql
UPDATE position
SET quantity = 0;
```

could destroy the historical basis for why we think we're flat.

Instead:

```text
Order O1
   ↓
Fill +30
Fill +20
   ↓
Position Projection = +50
```

and later:

```text
Exit Order O2
   ↓
Fill -50
   ↓
Position Projection = 0
```

The fill ledger remains intact.

This also makes reconciliation much cleaner:

```text
Position derived from fills
             vs
Broker-reported position
```

---

# 7. Add explicit reconciliation tables

Reconciliation is safety-critical enough that I would not leave it buried inside `audit_event`.

Add something like:

```text
reconciliation_run
------------------
id
broker_account_id
started_at
completed_at
status

reconciliation_item
-------------------
run_id
instrument_id
internal_quantity
broker_quantity
difference
status
resolution
```

Potentially also compare:

```text
orders
fills
cash
margin
```

later.

A mismatch must be queryable as structured state, not only discovered by parsing logs.

---

# 8. `order_record` needs an append-only event companion

Keeping a mutable current-state row is correct:

```text
order_record
------------
status = PARTIAL
filled_quantity = 70
```

But I would also retain:

```text
order_event
-----------
order_event_id
internal_order_id
sequence_no
event_type
broker_timestamp
received_timestamp
payload
```

For example:

```text
CREATED
SUBMITTED
ACKNOWLEDGED
PARTIAL_FILL
PARTIAL_FILL
FILLED
```

Then:

```text
order_record
```

is the fast projection, while:

```text
order_event
```

preserves the history.

I would **not rely on the generic `audit_event` table to reconstruct the OMS**.

---

# 9. Durable order intent is missing

DD-04A has an important safety invariant:

```text
Create intent
    ↓
durably record intent
    ↓
submit to broker
```



We therefore need either:

```text
order_intent
```

or make:

```text
order_record(status = CREATED)
```

serve that role.

I favor the latter initially.

The critical transaction becomes:

```text
BEGIN

RiskDecision
TradePlan
OrderRecord(CREATED)
AuditEvent

COMMIT
```

Then:

```text
Broker submission
```

occurs.

Not merely:

```text
RiskDecision
TradePlan
AuditEvent
COMMIT
↓
construct order later
```

The actual broker-side effect should have a durable internal identity before submission.

---

# 10. Add idempotency columns and constraints

For financial writes, this is essential.

For example:

```text
order_record
------------
internal_order_id
client_order_reference UNIQUE
broker_account_id
broker_order_id
```

and:

```sql
UNIQUE (broker_account_id, broker_order_id)
```

where appropriate.

Likewise fills:

```sql
UNIQUE (broker_account_id, broker_trade_id)
```

if the broker provides stable fill/trade identifiers.

Database constraints should participate in duplicate protection.

Not just:

```java
if (!repository.exists(...))
```

because that is race-prone.

---

# 11. `audit_event(payload)` needs a stronger envelope

A JSONB payload is fine.

A table that's only:

```text
event_type
timestamp
payload
```

is too weak.

At minimum:

```text
audit_event
-----------
audit_event_id
tenant_id

event_type
event_version

aggregate_type
aggregate_id

actor_type
actor_id

correlation_id
causation_id

event_timestamp

payload JSONB
```

Potentially:

```text
sequence_number
```

when events belong to a particular aggregate.

This gives you traceability such as:

```text
SetupObservation
    correlation_id = C123

RiskDecision
    correlation_id = C123

TradePlan
    correlation_id = C123

Order
    correlation_id = C123
```

which will be extremely useful operationally.

---

# 12. Move `audit_event` to its own schema

I would probably use:

```text
audit.audit_event
```

rather than:

```text
operational.audit_event
```

because its lifecycle and access semantics are different.

Conceptually:

```text
reference
operational
research
audit
```

Four schemas is still simple.

---

# 13. `market_observation` is conceptually right but physically dangerous

This deserves careful treatment because of the market-memory architecture.

Suppose the learning universe becomes:

```text
2,000 stocks
×
75 M5 bars/day
×
250 trading days
```

That's approximately:

```text
37.5 million M5 observations/year
```

Before:

* M1;
* market indices;
* sector indices;
* multiple timeframes.

Five years could easily mean hundreds of millions of rows.

Therefore I **would not put `market_observation` in the live transactional `operational` schema**.

Either:

```text
research.market_observation
```

or better:

```text
market.market_observation
```

if we're willing to introduce a dedicated market catalog schema.

---

# 14. Observation identity should not depend on dataset identity

This is subtle but important.

You currently have:

```text
observation_id
instrument_id
timeframe_id
bar_close_timestamp
canonical_dataset_id
```

The logical observation:

> RELIANCE M5 at 10:25 on 2028-06-15

should remain the **same observation** even if we later correct/rebuild its underlying dataset.

Therefore:

```text
Observation identity
=
instrument
+
timeframe
+
bar close timestamp
```

Dataset/revision is lineage **about the observation**, not part of the observation's semantic identity.

Otherwise:

```text
dataset V1
```

and:

```text
dataset V2
```

would create different identities for the same point in market history.

I would separate:

```text
market_observation
------------------
observation_id
instrument_id
timeframe_id
bar_close_timestamp
```

from:

```text
observation_revision
--------------------
observation_id
dataset_version_id
quality_state
checksum
...
```

Or potentially make `observation_id` deterministic and avoid materializing every observation in PostgreSQL at all.

---

# 15. Consider deterministic `observation_id`

Because of the volume involved, this is worth investigating.

Instead of inserting a PostgreSQL row merely to obtain:

```text
BIGSERIAL observation_id
```

we could derive the identity deterministically from:

```text
instrument_id
timeframe
bar_close_timestamp
```

Then Parquet data and PatternWindows can independently compute the same identity.

Conceptually:

```text
ObservationKey(
    RELIANCE,
    M5,
    2028-05-13T10:25:00+05:30
)
```

→ stable 128-bit identifier.

I'm **not yet saying we should definitely use a hash/UUID scheme**, but DD-04B should benchmark this because it may let us avoid a huge PostgreSQL observation-registry table.

---

# 16. Pattern metadata is currently too thin for efficient retrieval

You have:

```text
pattern_window_id
anchor_observation_id
pattern_schema_version
```

For the similarity system we discussed, we also need filterable metadata.

Something closer to:

```text
pattern_window_metadata
-----------------------
pattern_window_id

anchor_observation_id

instrument_id
sector_id_as_of

timeframe
window_length

pattern_schema_version
feature_schema_version
normalization_version

anchor_timestamp
minutes_since_open

market_regime
sector_regime

directional_state

sequence_storage_reference
```

Not necessarily every one of these must become a PostgreSQL column, but anything frequently used for **Stage-1 candidate filtering** should be readily indexable.

Recall the retrieval architecture:

```text
Millions
   ↓
metadata filter
   ↓
Thousands
   ↓
ANN/vector search
   ↓
Dozens
```

The schema has to support the first arrow efficiently.

---

# 17. We also need an index manifest

The future similarity index itself is derived and rebuildable.

Add something conceptually like:

```text
pattern_index_manifest
----------------------
index_id

pattern_schema_version
embedding_model_version
distance_metric

universe
start_timestamp
end_timestamp
as_of_cutoff

storage_uri
record_count
checksum

created_at
status
```

Then we know exactly which historical corpus a similarity result came from.

---

# 18. Add label definitions, not just label files

`label/outcome` in Parquet is correct.

But PostgreSQL should know what those columns mean.

For example:

```text
outcome_schema
outcome_schema_version
outcome_definition
```

Because:

```text
MFE_60M
```

could change meaning depending on:

* price basis;
* transaction-cost treatment;
* high/low interpolation;
* bar semantics;
* stop methodology.

Labels need versioning just as much as features.

---

# 19. Dataset manifests should be immutable versions

Your:

```text
dataset_manifest
```

is exactly the right idea.

I'd probably formalize it as:

```text
dataset
-------
dataset_id
dataset_type
name

dataset_version
---------------
dataset_version_id
dataset_id

schema_version
source

as_of_timestamp
min_timestamp
max_timestamp

storage_uri
partition_manifest_uri

row_count
checksum

parent_dataset_version_id

created_at
code_version
```

Then experiment reproducibility becomes:

```text
ExperimentRun
    ↓
DatasetVersion 71
```

rather than:

```text
"whatever currently lives under this S3 directory"
```

---

# 20. `model_registry` should not be Python-owned once models affect capital

This part of your ownership description needs adjustment.

You say:

> Python manages the research schema, Java primarily reads it.

Fine for:

```text
experiments
training runs
candidate models
research results
```

But:

```text
deployment_state = PRODUCTION
```

is production authority.

Python research code should **not** be able to promote itself to live trading authority.

I would separate:

```text
research.training_run
research.model_candidate
```

from something Java/admin-controlled such as:

```text
operational/model_control.model_version
model_deployment
```

or use DB permissions so Python can write metadata/artifacts but **cannot mutate production deployment authority**.

That preserves the DD-01 rule that ML authority is deliberately controlled.

---

# 21. Corporate actions are missing

This is critical for DD-05.

We need:

```text
reference.corporate_action
```

covering at least:

```text
split
bonus
dividend
rights
merger
symbol change
delisting
```

Potentially also:

```text
adjustment_factor
```

or an explicit historical adjustment dataset.

Without it, historical pattern matching and backtesting can produce nonsense across splits and similar events.

---

# 22. Index/benchmark membership history is missing

You have sector mappings, but historical market context also needs temporal index membership.

For example:

```text
benchmark
benchmark_constituent_history
```

with:

```text
valid_from
valid_to
```

Otherwise a 2026 backtest could accidentally use the 2030 NIFTY composition.

That's classic point-in-time leakage.

---

# 23. Temporal tables need overlap protection

Using:

```text
valid_from
valid_to
```

is good.

But the database should prevent:

```text
HDFCBANK → BANK sector
2027-01-01 → 2028-01-01

HDFCBANK → IT sector
2027-06-01 → 2027-12-01
```

unless overlapping mappings are explicitly allowed.

PostgreSQL can enforce this elegantly with range/exclusion constraints.

Conceptually:

```text
EXCLUDE:
instrument_id equal
AND validity ranges overlap
```

This is much stronger than relying on application validation.

---

# 24. `instrument` should anticipate derivatives without becoming a nullable mess

Your current core is good:

```text
instrument_id
exchange
segment
symbol
tick_size
lot_size
trading_status
```

But DD-01 eventually requires:

```text
expiry
strike
option_type
underlying
```



I would probably use:

```text
instrument
```

for common identity and:

```text
derivative_contract
-------------------
instrument_id
underlying_instrument_id
expiry
strike
option_type
```

rather than stuffing nullable option fields into every equity/index row.

---

# 25. Add broker and instrument identifiers separately

Rather than treating `symbol` as permanent identity:

```text
instrument
    ↓
instrument_identifier
```

could hold:

```text
ISIN
exchange symbol
historical symbol
```

with validity periods where appropriate.

Then:

```text
broker_instrument_mapping
```

handles broker-specific tokenization.

That gives us:

```text
Economic instrument identity
        ↓
Exchange identity
        ↓
Broker identity
```

as three distinct concepts.

---

# 26. Add portfolio/account snapshots

DD-03 needs historical risk state such as:

```text
equity
available cash
margin
gross exposure
net exposure
open risk
daily realized P&L
```

We therefore need something like:

```text
portfolio_snapshot
```

or:

```text
account_snapshot
```

at meaningful decision intervals.

Some of this can be Parquet later, but risk decisions need to reference the state they evaluated.

Potentially:

```text
risk_decision.portfolio_snapshot_id
```

That gives us exact answers later to:

> Why was this trade approved for 120 shares rather than 160?

---

# 27. Add risk decision inputs or snapshot reference

A `risk_decision` should not merely record:

```text
APPROVE
120 shares
```

It should be reproducible.

Either include key immutable values:

```text
risk_reference_equity
portfolio_open_risk
sector_open_risk
daily_drawdown
available_margin
requested_risk
approved_risk
```

or reference an immutable:

```text
risk_context_snapshot
```

I favor a hybrid: preserve important decision numbers directly and reference the full snapshot.

---

# 28. Avoid putting everything in JSONB

JSONB is useful for:

```text
strategy parameters
feature parameters
broker raw response
event payload
```

But values that participate in:

```text
risk enforcement
joins
filters
uniqueness
indexes
```

should generally be typed columns.

Don't end up with:

```json
{
  "approvedRisk": 1250,
  "approvedQuantity": 110
}
```

when those are core financial values the database should understand.

---

# Revised logical schema

I would now move toward:

```text
reference
├── exchange
├── broker
├── instrument
├── instrument_identifier
├── derivative_contract
├── broker_instrument_mapping
├── sector
├── instrument_sector_history
├── benchmark
├── benchmark_constituent_history
├── trading_calendar
├── trading_session
└── corporate_action


control / configuration
├── feature_definition
├── feature_version
├── feature_schema
├── feature_schema_version
├── feature_schema_member
│
├── strategy
├── strategy_version
├── strategy_deployment
│
├── risk_policy
├── risk_policy_version
│
├── model
├── model_version
└── model_deployment


operational
├── tenant
├── app_user
├── broker_account
├── watchlist
├── watchlist_item
│
├── setup_observation
├── recommendation
├── risk_context_snapshot
├── risk_decision
├── trade_plan
│
├── order_record
├── order_event
├── fill
├── trade
├── position_projection
│
├── portfolio_snapshot
│
├── reconciliation_run
├── reconciliation_item
│
└── control_state / kill_switch_state


market/research catalog
├── dataset
├── dataset_version
├── pattern_schema
├── pattern_window_metadata
├── pattern_index_manifest
├── outcome_schema
├── experiment
├── experiment_run
├── training_run
└── model_candidate


audit
└── audit_event
```

With:

```text
Parquet / Object Storage

market/raw/trade
market/raw/quote
market/raw/depth

market/candle

feature/snapshot

pattern/window
pattern/embedding

label/outcome

experiment/output
model/artifact
```

---

# The core lineage should become

This is the relationship I want the DDL to make extremely obvious:

```text
MarketObservation
       │
       ▼
Feature Snapshot
       │
       ▼
SetupObservation
       │
       ├────────────► ModelPrediction
       │
       ▼
RiskDecision
       │
       ▼
TradePlan
       │
       ▼
OrderRecord
       │
       ▼
      Fill
       │
       ▼
Position Projection
       │
       ▼
Trade Outcome
```

With immutable audit/event lineage alongside it:

```text
       ┌───────────────────────────────┐
       │                               │
       ▼                               ▼
Trading Events                   Audit Events
```

And later:

```text
MarketObservation
       │
       ▼
PatternWindow
       │
       ▼
Historical Similarity
       │
       ▼
Outcome Distribution
       │
       ▼
ModelPrediction
```

That structure supports both sides of Edge Relative:

**real-time financial correctness** and **long-term market intelligence**.

## Bottom line

Your proposed schema has the **right architecture**, particularly around:

* PostgreSQL vs Parquet;
* versioning;
* temporal mappings;
* immutable trade intent;
* partial fills;
* EAV avoidance;
* asynchronous persistence;
* feature/outcome separation.

I would fix **five things before we write a single migration**:

1. Move identity/account entities out of `reference`.
2. Correct the `Setup → RiskDecision → TradePlan` relationship.
3. Treat fills as authoritative and positions as projections.
4. Reconsider the scale/identity/location of `market_observation`.
5. Add explicit version-set, event, reconciliation, corporate-action, and dataset/index lineage models.

