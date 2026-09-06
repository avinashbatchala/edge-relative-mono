# Edge Relative — Market Data & Feature Architecture

**Document:** DD-05  
**Version:** 1.0  
**Status:** Foundational Data / Feature / Market-Memory Design  
**Product:** Edge Relative  
**Market:** Indian equities and equity derivatives  
**Initial Exchange Focus:** NSE  
**Initial Trading Scope:** Intraday directional equities  
**Primary Methodological Source:** RealDayTrading Wiki, May 2024  
**Related Documents:** DD-01 — Product & Feature Design Specification; DD-02 — Algorithm Research & Strategy Principles; DD-03 — Risk Management & Position Sizing; DD-04A — Development Stack & Engineering Standards; DD-06 — Backtesting & Research Platform; DD-09 — Machine Learning & Stock Intelligence  

---

# 1. Purpose

DD-05 defines how Edge Relative represents, captures, validates, stores, reconstructs, derives, and searches market information.

The document has two equally important purposes.

First, it must provide deterministic, point-in-time-correct inputs to:

```text
Market State
    ↓
Feature Engine
    ↓
Strategy
    ↓
Risk
    ↓
Execution
```

Second, it must build a durable historical **market memory** capable of answering questions such as:

> Have we seen a market/sector/stock state similar to the current state before, and what happened next?

The second requirement is not a future add-on. It is a first-class architectural constraint from the beginning.

---

# 2. Source Hierarchy

DD-05 distinguishes six classes of statements.

## 2.1 RDT-DERIVED

A market-data or feature requirement directly implied or stated by the RealDayTrading Wiki.

## 2.2 EDGE-FORMALIZATION

A deterministic representation introduced by Edge Relative to convert trading concepts into reconstructable data and feature contracts.

## 2.3 NSE-ADAPTATION

A change required because the source methodology is primarily framed around SPY and US equities while Edge Relative initially operates on NSE.

## 2.4 SYSTEM-SAFETY

A rule required to prevent trading on stale, corrupt, incomplete, misordered, or otherwise untrustworthy data.

## 2.5 RESEARCH-ENABLING

A data decision whose primary purpose is future statistical research, historical similarity, ML, or model development.

## 2.6 RESEARCH-HYPOTHESIS

A proposed feature, representation, similarity method, threshold, or retention choice that must demonstrate value empirically.

The RDT Wiki is authoritative for the methodological concepts it actually states. It is not treated as a source for data-engineering mechanics that it does not specify.

---

# 3. Source Provenance

The RDT material most relevant to DD-05 includes:

- Market First / Stock Second and the need to observe the broad market alongside the stock — approximately pp. 51–61;
- Relative Strength / Relative Weakness and the Real Relative Strength workshop — approximately pp. 64–70;
- multi-timeframe market and stock analysis — throughout pp. 83–214;
- highest-probability setup criteria using D1, M5, additional intraday timeframes, relative volume, sector strength, trendlines, compression, and Heiken-Ashi — approximately pp. 147–173 and community trading-plan sections;
- volume analysis, RVOL, VWAP, OBV, and directional price/volume context — approximately pp. 351–357;
- chart/timeframe/candle/price-action factual definitions — approximately pp. 588 onward;
- RRS implementation examples using ATR normalization and 3/8 smoothing — approximately pp. 66–70 and pp. 602–605.

The Wiki describes what a trader needs to observe. It does not define canonical tick schemas, event-ordering policy, storage architecture, replay semantics, feature versioning, data-quality state machines, or vector-search infrastructure. Those are Edge Relative design decisions.

---

# 4. Relationship to DD-01

DD-01 requires:

- a canonical instrument master;
- normalized market data;
- exchange, broker, received, and processed timestamps;
- canonical candle construction;
- historical reconstruction;
- market and sector context;
- versioned feature definitions;
- point-in-time-correct ML datasets;
- setup observations including non-trades;
- outcome labels;
- data-quality monitoring;
- historical similarity search.

DD-05 turns those product requirements into concrete data architecture.

---

# 5. Relationship to DD-02

DD-02 owns the mathematical and strategic meaning of features such as:

- RRS;
- RVOL;
- RVE;
- market state;
- sector state;
- price structure;
- compression;
- trendline breaks;
- breakout confirmation;
- setup state.

DD-05 owns:

- the inputs those features consume;
- their timestamp alignment;
- their versioned storage;
- their historical reconstruction;
- their sequence representation;
- their availability to live/replay/research consumers.

DD-05 must not silently redefine DD-02 mathematics.

---

# 6. Relationship to DD-03

DD-03 consumes market-derived values such as:

- price;
- spread;
- liquidity;
- volatility;
- structural stop references;
- stress/slippage inputs.

DD-05 guarantees that those observations are timestamped, quality-scored, reconstructable, and derived from known data versions.

Risk cannot be more reliable than the data feeding it.

---

# 7. Relationship to DD-04A

DD-04A establishes the initial technical constraints:

```text
Java + Spring Boot
Modular monolith
jOOQ + PostgreSQL
Parquet + object storage
Python for research/ML
No Cassandra initially
No Redis initially
No Kafka initially
```

DD-05 works within those decisions.

PostgreSQL is not used as an unlimited tick warehouse. Large analytical history belongs in Parquet/object storage.

---

# 8. Relationship to DD-06 and DD-09

DD-06 will define how historical data is replayed through production strategy/risk logic and how backtests are evaluated.

DD-09 will define model architecture, embeddings, similarity interpretation, hierarchical stock intelligence, calibration, and ML authority.

DD-05 must provide both documents with lossless enough history and stable enough lineage that neither needs to invent its own incompatible data representation.

---

# 9. Scope

DD-05 covers:

- instrument/reference data required by market observations;
- raw market-data capture;
- canonical normalized events;
- event identity, timestamps, ordering, and deduplication;
- canonical candles;
- historical storage;
- feature calculation contracts;
- feature versioning and lineage;
- market/sector/stock alignment;
- pattern-window representation;
- historical similarity infrastructure boundaries;
- outcome-label data boundaries;
- point-in-time correctness;
- replayability;
- data quality;
- retention and rebuild policy.

---

# 10. Non-Goals

DD-05 does not define:

- broker-order execution;
- OMS state machines;
- strategy profitability;
- position-sizing thresholds;
- final ML model choice;
- final ANN/vector technology;
- final cloud/object-storage vendor;
- market-data vendor contract terms;
- every possible options microstructure feature.

It creates the data foundation on which those later decisions depend.

---

# 11. Foundational Data Principle

The permanent asset is the historical market state, not the feature formula currently fashionable.

Therefore:

```text
Raw / canonical market history
        ↓
Versioned derived features
        ↓
Pattern representations
        ↓
Embeddings / models
```

must never be inverted into:

```text
Feed
  ↓
Current feature/model representation
  ↓
Discard source history
```

Derived representations are replaceable. Source observations are not.

---

# 12. Market Memory Is First-Class

Edge Relative is explicitly designed to accumulate a longitudinal market-memory corpus.

The system must eventually answer:

- what historical states most closely resemble the current state;
- whether the same stock behaved similarly before;
- whether the same sector exhibited the same structure before;
- how outcome distributions differed by market regime;
- whether time-of-day changes conditional expectancy;
- whether a forming pattern resembles historical winners or losers;
- whether a trade is taking unusually long compared with similar historical states.

This requirement influences raw retention, snapshot frequency, normalization, versioning, and storage layout.

---

# 13. Institutional-Pattern Hypothesis

Edge Relative does **not** assume that public market data allows reconstruction of proprietary HFT or institutional algorithms.

The testable hypothesis is narrower:

> Rule-driven institutional activity may leave repeatable conditional patterns in price, volume, relative strength, liquidity, and timing that can be statistically characterized from historical observations.

Pattern matching is intended to discover conditional behavior, not to claim knowledge of another participant's algorithm.

---

# 14. Trading Universe vs Learning Universe

A core distinction is:

```text
Trading Universe
≈ maximum 20 actively watched stocks
```

versus:

```text
Learning Universe
= broad liquid NSE universe + market/sector references
```

The trading universe is constrained for operational focus.

The learning universe is intentionally broader so research is not limited to the small set of stocks currently traded.

---

# 15. Broad Learning Universe

The learning architecture should be able to ingest and retain data for a broad NSE equity universe, subject to:

- market-data licensing;
- vendor coverage;
- storage economics;
- minimum data quality;
- liquidity/relevance filters where necessary.

Universe membership itself must be historically versioned.

---

# 16. Collection Does Not Grant Trading Authority

A symbol can exist in the learning corpus without being eligible for live trading.

Therefore:

```text
collect_data = true
```

does not imply:

```text
strategy_eligible = true
execution_eligible = true
```

This prevents research breadth from bypassing watchlist/risk/product controls.

---

# 17. Raw Truth Invariant

Where vendor entitlements permit, retain enough source-level information to reconstruct future features.

Preferred retained inputs include:

- trades/ticks;
- best bid/ask quotes;
- market depth when available and economically practical;
- exchange/index observations;
- volume;
- open interest for derivatives;
- instrument/session status events;
- vendor corrections.

M5 candles alone are insufficient as the sole permanent historical representation.

---

# 18. Long-Term Retention

Historical market memory is intended to compound in value over years.

Default policy:

> Retain historical market and derived research data indefinitely where licensing, privacy, regulation, and storage economics allow.

Retention policy must be configuration and dataset metadata, not hidden application behavior.

---

# 19. Point-in-Time Correctness Invariant

Every historical state used for backtesting, similarity search, or ML must contain only information that was available at that historical instant.

Examples of prohibited leakage:

```text
10:30 feature uses full-day volume
historical universe uses future index constituents
support level uses future candles
RRS normalization uses future samples
corporate-action metadata appears before it was known
outcome label is joined into a live feature row
```

Point-in-time correctness is a data invariant, not merely a backtest preference.

---

# 20. Temporal Evolution Invariant

A market pattern is not equivalent to its latest scalar feature values.

For example, the two sequences:

```text
RRS: 0.4 → 0.8 → 1.2 → 1.7 → 2.1
```

and:

```text
RRS: 3.9 → 3.4 → 2.9 → 2.5 → 2.1
```

have the same current value but opposite trajectories.

DD-05 therefore stores/derives time windows and sequences as first-class research objects.

---

# 21. Derived Data Is Rebuildable

Every derived dataset must identify the source data and code/configuration versions used to generate it.

If `RRS_V4` replaces `RRS_V3`, the system should rebuild historical RRS from source history rather than mutate old rows in place.

If `EmbeddingModel_V3` replaces `EmbeddingModel_V2`, embeddings are regenerated without recollecting the market.

---

# 22. Lineage Invariant

A derived observation must be traceable through:

```text
Raw Source
    ↓
Canonical Event Revision
    ↓
Candle Revision
    ↓
Feature Definition + Version
    ↓
Feature Snapshot
    ↓
Pattern Window
    ↓
Embedding / Similarity Result
    ↓
Outcome Labels
```

No research result should depend on an untraceable feature dump.

---

# 23. Deterministic Replay Invariant

Given identical:

- canonical historical events;
- data revisions;
- trading calendar;
- feature versions;
- parameters;
- clock;

replay must produce the same candles and feature states within defined numerical tolerances.

Live and replay implementations should share the same calculation code wherever feasible.

---

# 24. Fail-Closed Data Principle

When required data cannot be trusted, the correct trading decision is:

```text
NO NEW TRADES
```

Data uncertainty can reduce authority but must never increase it.

---

# 25. Logical Data Layers

DD-05 defines six logical layers:

```text
1. Source / Raw Observations
2. Canonical Market Events
3. Canonical Time Series / Candles
4. Versioned Feature Observations
5. Pattern / Similarity Representations
6. Future Outcome Labels
```

Embeddings are derived indexes over Layer 5, not a seventh source of truth.

---

# 26. Source / Raw Observation Layer

The raw layer preserves vendor/broker semantics sufficiently to:

- audit normalization;
- diagnose feed anomalies;
- replay alternate normalization rules;
- resolve disagreements between providers;
- measure source-specific latency.

Raw payloads should remain distinguishable from canonical internal events.

---

# 27. Canonical Event Layer

The canonical event layer converts vendor-specific messages into stable internal contracts.

Strategy and feature code must not depend on:

```text
Zerodha token semantics
Groww JSON field names
vendor-specific depth arrays
vendor-specific status enums
```

Those belong inside adapters.

---

# 28. Canonical Time-Series Layer

Canonical candles/time bars provide stable inputs for:

- M1/M5/D1 analysis;
- multi-timeframe features;
- backtests;
- research exports;
- visualization;
- pattern-window extraction.

All candle consumers must agree on interval boundaries and finalization semantics.

---

# 29. Feature Observation Layer

The feature layer transforms canonical observations into deterministic measurements such as:

```text
RRS
RVOL
RVE
ATR
VWAP distance
moving-average relationships
volume context
price structure
market/sector context
liquidity
regime
```

Each feature is explicitly versioned.

---

# 30. Pattern Layer

The pattern layer represents historical trajectories around a stable anchor observation.

It is optimized for questions of the form:

> What prior observations had similar state and evolution?

Pattern windows preserve references to their underlying observations and do not become independent truth.

---

# 31. Outcome Layer

Outcome labels represent data that occurs **after** a historical anchor.

Examples:

- subsequent returns;
- MFE;
- MAE;
- target-before-stop;
- time-to-target;
- time-to-stop.

Outcome data is strictly segregated from live feature inputs.

---

# 32. Embedding Layer

Learned embeddings are optional, versioned, disposable projections of pattern windows.

They may support fast approximate similarity retrieval.

They must never be the only stored representation of a historical pattern.

---

# 33. Live / Historical Parity

A feature consumer should not need a different domain model for:

```text
LIVE
REPLAY
BACKTEST
RESEARCH
```

The source of events and clock changes; the canonical contracts and feature semantics do not.

---

# 34. Market Data Source Abstraction

Each market-data provider implements an adapter that can emit normalized source messages into the canonical pipeline.

Conceptually:

```text
Broker/Vendor Feed
      ↓
Source Adapter
      ↓
Normalizer
      ↓
Canonical MarketEvent
```

Provider-specific behavior remains outside strategy/features.

---

# 35. Source Identity

Every raw and canonical observation must identify its source.

Minimum metadata includes:

```text
source_id
source_type
source_connection_id
source_sequence_if_available
source_payload_version
```

This enables feed comparison and post-incident analysis.

---

# 36. Multiple Data Sources

The architecture must allow multiple sources for the same instrument without assuming they are identical.

Future uses include:

- primary/backup feed;
- broker-vs-vendor comparison;
- data-quality validation;
- execution-feed vs research-feed comparison.

Source priority and authority must be explicit configuration.

---

# 37. Source Reconciliation

When two feeds disagree, the system must not silently average them.

A reconciliation policy may:

- choose the configured authoritative source;
- flag divergence;
- downgrade data quality;
- suspend dependent trading if disagreement exceeds tolerance.

The actual tolerance is environment/provider-specific configuration.

---

# 38. Canonical Instrument Master

All market observations reference internal `instrument_id` values.

The instrument master owns stable identity across:

- symbol changes;
- broker token changes;
- corporate actions;
- exchange identifiers;
- derivatives expiries.

Trading logic must never use a broker token as permanent security identity.

---

# 39. Instrument Identity

For equities, the internal identity should prefer durable exchange/security identifiers where available, with symbol as a versioned alias.

Representative metadata:

```text
instrument_id
exchange
segment
symbol
ISIN
company
sector_id
industry_id
tick_size
lot_size
trading_status
valid_from
valid_to
```

---

# 40. Broker Token Mapping

Broker-specific instrument tokens are temporal mappings:

```text
instrument_id
broker_id
broker_token
valid_from
valid_to
```

A token change must not create a new economic instrument.

---

# 41. Historical Universe Membership

Research must know which instruments were actually eligible/existent at each historical date.

Store versioned membership for relevant universes such as:

- learning universe;
- trading universe;
- index membership;
- sector index membership.

This reduces survivorship and future-membership leakage.

---

# 42. Sector Mapping

Each equity should map to a canonical sector classification and, where defensible, a canonical NSE sector benchmark.

The mapping is temporal:

```text
instrument_id
sector_id
benchmark_instrument_id
valid_from
valid_to
mapping_version
```

A later reclassification must not rewrite historical context.

---

# 43. Corporate-Action Reference Data

Reference data must record events such as:

- splits;
- bonuses;
- dividends;
- rights issues;
- mergers;
- demergers;
- symbol changes;
- delistings.

Corporate-action handling is essential for historical continuity and feature correctness.

---

# 44. Trading Calendar

The market-data layer owns a canonical exchange calendar containing:

- trading dates;
- holidays;
- normal session windows;
- pre-open/auction windows;
- special sessions;
- derivatives expiry metadata where relevant.

Algorithms must not derive trading sessions from weekdays alone.

---

# 45. Time Zone

All machine timestamps use UTC/`Instant`.

Exchange-session semantics use:

```text
Asia/Kolkata
```

Server-local timezone must not alter bar boundaries, session labels, or historical reconstruction.

---

# 46. Canonical MarketEvent Envelope

A normalized event envelope should contain at minimum:

```text
event_id
instrument_id
event_type
source_id
exchange_timestamp
broker_timestamp
received_timestamp
processed_timestamp
source_sequence
quality_state
payload
```

Fields may be nullable only when the source genuinely cannot provide them.

---

# 47. Event Types

Initial canonical event categories include:

```text
TRADE
QUOTE
DEPTH
OPEN_INTEREST
INDEX_VALUE
MARKET_STATUS
INSTRUMENT_STATUS
SOURCE_HEARTBEAT
CORRECTION
```

Vendor snapshot messages may be normalized into one or more canonical event types.

---

# 48. TradeTick

Canonical trade/tick payload should support:

```text
price
quantity
cumulative_volume_if_available
trade_id_if_available
exchange_trade_sequence_if_available
```

Do not infer aggressor side unless a documented method/source supplies it.

---

# 49. Quote

Canonical quote payload should support at minimum:

```text
best_bid_price
best_bid_quantity
best_ask_price
best_ask_quantity
last_price_if_supplied
```

Quote and trade semantics must remain distinct.

---

# 50. Market Depth

Depth is an optional but supported first-class event.

Conceptual payload:

```text
levels[]:
  side
  level
  price
  quantity
  order_count_if_available
```

Depth retention may be limited by licensing, source capability, or storage cost without changing the canonical model.

---

# 51. Open Interest

Derivatives observations may include:

```text
open_interest
open_interest_change
```

Open interest is not required for V1 equity strategy authority but should fit the same timestamp/quality model.

---

# 52. Index Values

Market and sector benchmarks such as NIFTY 50 and sector indices are represented as canonical instruments/events, not as special hard-coded globals.

This allows the same alignment, replay, and feature machinery to operate on stocks and benchmarks.

---

# 53. Market Status Events

The canonical model should support explicit state such as:

```text
PRE_OPEN
OPEN
HALTED
AUCTION
CLOSED
SPECIAL_SESSION
```

Where the source does not provide an event, the exchange calendar may supply expected session state but must not fabricate trade events.

---

# 54. Event Identity

`event_id` must provide stable internal identity for deduplication and lineage.

If the source provides a durable exchange sequence/trade identifier, incorporate it.

Otherwise generate identity from a documented canonical key plus source namespace.

Hash-based fallback identity must account for genuine identical repeated trades and therefore cannot naïvely hash only price, quantity, and timestamp.

---

# 55. Exchange Timestamp

`exchange_timestamp` represents when the event occurred according to the exchange/source's market clock.

It is the preferred event-time coordinate for market reconstruction when trustworthy and available.

---

# 56. Broker Timestamp

`broker_timestamp` records the timestamp assigned by the broker/vendor infrastructure.

It supports:

- vendor latency analysis;
- discrepancy detection;
- broker-specific replay diagnostics.

It must not replace exchange time merely because it is easier to obtain.

---

# 57. Received Timestamp

`received_timestamp` records when Edge Relative first received the message.

It is essential for:

- feed latency;
- stale-data detection;
- live-vs-historical realism;
- execution analysis.

---

# 58. Processed Timestamp

`processed_timestamp` records when canonical normalization/processing completed.

The difference between received and processed time measures internal ingestion latency.

---

# 59. Event Time vs Processing Time

The architecture explicitly distinguishes:

```text
When market event happened
```

from:

```text
When Edge Relative learned about it
```

Historical replay may reproduce either event-time-perfect or arrival-time-realistic modes depending on DD-06 objectives.

---

# 60. Source Sequence Numbers

When a source exposes sequence numbers they must be retained.

They support:

- gap detection;
- duplicate detection;
- reconnect recovery;
- ordering.

Sequence semantics are source-specific metadata, not universal exchange truth.

---

# 61. Event Ordering

Canonical processing should establish a deterministic ordering rule using the best available combination of:

1. exchange timestamp;
2. source sequence;
3. received timestamp;
4. stable tie-breaker.

The selected ordering policy must be versioned because it affects candle/features under ambiguous input.

---

# 62. Late Events

Events arriving after their expected chronological location are marked as late.

Late-event handling has two distinct concerns:

```text
LIVE DECISION STATE
HISTORICAL CORRECTED STATE
```

A live engine cannot retroactively unknow a late event.

Historical datasets may later incorporate valid corrections, but must preserve revision lineage.

---

# 63. Duplicate Events

Duplicate detection must occur before double-counting volume or rebuilding candles.

Metrics should record:

- duplicate count;
- duplicate rate by source;
- duplicate bursts;
- duplicate recovery actions.

---

# 64. Corrections

Vendor/exchange corrections are stored as explicit revisions/events where possible.

Do not silently mutate historical raw data.

Consumers should be able to reproduce:

- the original live view;
- the later corrected historical view.

---

# 65. Missing Sequence / Gap Detection

A suspected feed gap should create a structured data-quality event.

Potential responses include:

```text
attempt snapshot recovery
request historical backfill
mark affected interval incomplete
block dependent strategy
```

A missing sequence must not be hidden by simply continuing candle calculation.

---

# 66. Impossible-Value Validation

Validation rules should detect conditions including:

- non-positive prices where impossible;
- negative quantities;
- bid greater than ask beyond known auction/special semantics;
- absurd price jumps inconsistent with exchange bands unless verified;
- cumulative volume decreasing unexpectedly;
- timestamps outside valid sessions;
- malformed depth.

Validation flags observations; it does not automatically invent replacement values.

---

# 67. Staleness

Every live market state exposes age:

```text
now - latest_required_received_timestamp
```

Staleness thresholds differ by data type and strategy but are configuration/versioned policy.

---

# 68. Data Quality States

Canonical observations and derived states should support quality states such as:

```text
GOOD
DEGRADED
STALE
INCOMPLETE
SUSPECT
CORRECTED
UNAVAILABLE
```

Quality is data, not merely a log message.

---

# 69. Quality Propagation

Derived values inherit relevant input quality.

For example:

```text
Market benchmark stale
        ↓
Stock-vs-market RRS = not trustworthy
        ↓
RRS quality = STALE
        ↓
Strategy requiring RRS = blocked
```

A numerically available feature is not automatically a valid feature.

---

# 70. Data-Quality Trading Gate

Each strategy declares required data capabilities.

Example:

```text
ER_RS_CONTINUATION_V1 requires:
- stock M5 GOOD
- broad-market M5 GOOD
- sector context GOOD or explicitly optional by strategy version
- RRS warmup complete
- RVOL baseline valid
```

Failure of a hard requirement blocks new trading.

---
# 71. Live In-Memory State

The live trading path should consume canonical events from memory, not repeatedly query historical storage.

Conceptually:

```text
Canonical Event
      ↓
In-Memory Market State
      ↓
Candle Builder
      ↓
Feature Engine
      ↓
Strategy
```

Persistence occurs alongside the hot path where data is reconstructable.

---

# 72. Asynchronous Market-Data Persistence

Raw/canonical market observations are generally recoverable rather than financial-intent records.

Therefore persistence may be asynchronous provided:

- queues are bounded;
- lag is observable;
- overflow policy is explicit;
- lost data triggers a quality event;
- critical live decisions do not block on ordinary historical writes.

---

# 73. Bounded Queues

No ingestion/persistence queue may be unbounded.

Each queue must expose:

```text
capacity
current_depth
oldest_event_age
dropped_event_count
write_latency
```

If market-data capture cannot keep up, the system must degrade visibly rather than exhaust memory.

---

# 74. Raw-Capture Durability Policy

For primary market sources, losing a material segment of raw data reduces the value of future research.

The system should therefore prefer durable append as soon as practical while keeping strategy latency independent from storage latency.

The exact persistence SLO belongs to deployment engineering, but capture lag must be monitored continuously.

---

# 75. PostgreSQL Role

PostgreSQL owns authoritative operational metadata including:

- instruments and temporal mappings;
- trading calendar;
- source configuration;
- feature definitions;
- feature/version metadata;
- recent feature observations needed operationally;
- pattern metadata/index metadata;
- outcome-label metadata where convenient;
- data-quality incidents;
- dataset manifests;
- lineage manifests.

It is not the permanent warehouse for every tick.

---

# 76. Parquet / Object Storage Role

Large append-oriented analytical history belongs in Parquet/object storage.

Expected datasets include:

```text
raw trades
raw quotes
raw depth
canonical events
M1/M5/... candles
historical feature matrices
pattern sequences
outcome-label datasets
ML training datasets
backtest exports
```

---

# 77. Dataset Namespace

Each analytical dataset should have a stable logical namespace, for example:

```text
market/raw/trade
market/raw/quote
market/canonical/event
market/candle
feature/snapshot
pattern/window
label/outcome
ml/dataset
```

Logical dataset names should remain stable even if physical bucket/container names change.

---

# 78. Partitioning Principles

Partition keys should optimize common large scans without producing millions of tiny files.

Candidate dimensions include:

- exchange;
- dataset type;
- timeframe;
- trading date/month;
- instrument bucket rather than always one directory per instrument.

Exact partitioning must be benchmarked against real query patterns.

---

# 79. Avoid Over-Partitioning

A layout such as:

```text
one Parquet file per stock per 5-minute bar
```

is prohibited.

Small-file proliferation materially degrades metadata operations and analytical scan performance.

Data should be compacted into appropriately sized columnar files.

---

# 80. File Compaction

The platform should support scheduled compaction of small ingestion files into larger immutable analytical files.

Compaction must preserve:

- dataset version;
- event identity;
- revision identity;
- checksums;
- lineage.

Physical compaction must not change logical observations.

---

# 81. Schema Evolution

Parquet schemas evolve through explicit dataset versions.

Adding an optional column may remain backward compatible.

Changing semantics of an existing field requires a new logical version even if its physical type remains identical.

---

# 82. Columnar Design

Analytical datasets should favor types that support vectorized scans and compression.

Examples:

- integer instrument IDs;
- UTC timestamps;
- numeric primitive columns for features;
- dictionary encoding for low-cardinality enums;
- nested structures only where they materially improve representation.

---

# 83. Data Compression

Compression codec is an implementation benchmark, not a domain rule.

The system should prefer a codec balancing:

- scan speed;
- storage cost;
- CPU cost;
- ecosystem compatibility.

The logical schema must not depend on a specific codec.

---

# 84. Dataset Catalog

A dataset catalog should record:

```text
dataset_id
logical_name
schema_version
data_revision
created_at
source_range
row_count
min_timestamp
max_timestamp
code_version
parameter_hash
storage_location
checksum/manifest
```

Research should refer to dataset IDs/manifests rather than ad-hoc directory paths.

---

# 85. Immutable Historical Versions

A corrected dataset does not overwrite the identity of the previous dataset.

Conceptually:

```text
canonical_events_rev_17
canonical_events_rev_18
```

or equivalent manifest revisioning.

This permits exact reproduction of prior experiments.

---

# 86. Checksums

Files/manifests should carry checksums or equivalent integrity metadata.

Silent bit rot or partial copy must not become a research result.

---

# 87. Backups and Replication

Historical market memory is a core asset and requires durable backup policy.

At minimum, design must support:

- replicated object storage or backup copy;
- PostgreSQL backups;
- restoration tests;
- catalog/manifest recovery.

Exact RPO/RTO belongs to infrastructure design.

---

# 88. Licensing Metadata

Each dataset should know its provenance and usage restrictions where relevant.

Metadata may include:

```text
provider
license_id
storage_permitted
historical_use_permitted
derived_data_permitted
redistribution_permitted
retention_constraints
```

A future commercial platform must not accidentally redistribute data whose license only permits private use.

---

# 89. Data Deletion / Tombstones

If a license or legal obligation requires deleting historical data, deletion must be explicit and auditable.

Derived datasets dependent on deleted source material must be marked accordingly.

---

# 90. Data Revision Model

Every material historical dataset should distinguish:

```text
LIVE_AS_SEEN
CORRECTED_HISTORICAL
```

where possible.

`LIVE_AS_SEEN` supports realistic replay of what the system actually knew.

`CORRECTED_HISTORICAL` supports research against the best known market history.

---

# 91. Canonical Candle Contract

A canonical candle should expose at minimum:

```text
candle_id
instrument_id
timeframe
open_time
close_time
open
high
low
close
volume
trade_count
vwap_if_available
is_complete
source_revision
quality_state
```

Optional fields may include bid/ask summaries, spread statistics, and depth-derived metrics.

---

# 92. Candle Identity

A candle is uniquely identified by:

```text
instrument_id
+ timeframe
+ open_time
+ candle_definition_version
```

A corrected candle is a revision of the same logical interval, not a different market interval.

---

# 93. Session-Anchored Candles

Intraday bar boundaries are anchored to the canonical NSE session start rather than arbitrary UTC boundaries.

For a normal 09:15 IST open, M5 bars are:

```text
09:15–09:20
09:20–09:25
...
```

This must remain deterministic in live and replay modes.

---

# 94. M1 Base Candles

The preferred long-term canonical bar base is M1 where data quality permits.

Reasons:

- supports future lower-timeframe research;
- permits deterministic derivation of M3/M5/M15/etc.;
- retains more path information than M5 alone;
- remains much cheaper than full tick/depth history.

Raw ticks/quotes are still retained where available.

---

# 95. RDT-Required Timeframes

RDT-derived requirements make the following especially relevant:

```text
D1
M5
M15
M30
H1
H2
H4
```

D1 and M5 are primary for Strategy V1.

The additional timeframes support multi-timeframe RS/RW and context analysis.

---

# 96. Supported Timeframe Registry

Timeframes are registry/configuration data, not scattered enum assumptions.

Initial registry may include:

```text
M1
M3
M5
M15
M30
H1
H2
H4
D1
W1
```

Each timeframe defines session alignment and partial-bar policy.

---

# 97. Higher-Timeframe Construction

Higher intraday candles should be derived from canonical lower-timeframe bars/events using the same deterministic aggregator.

Do not independently trust unrelated vendor M5 and M15 histories when they can be produced from the same canonical M1/tick source.

Vendor bars may be retained for comparison/validation.

---

# 98. H1/H2/H4 Session Semantics

Longer intraday intervals are anchored to session open.

Because a normal NSE cash session is not an integer multiple of every interval, the final bar may be partial.

The partial final bar must be explicitly marked rather than silently stretched or discarded.

---

# 99. D1 Candle Semantics

D1 uses the canonical trading session for that date.

Pre-open/auction events are included only if the selected candle definition explicitly specifies them.

Changing inclusion semantics creates a new candle-definition version.

---

# 100. W1 Candle Semantics

Weekly candles aggregate actual trading sessions from the exchange calendar.

Holiday-shortened weeks remain valid weeks and must not assume five sessions.

---

# 101. In-Progress Candles

Live consumers may access an in-progress candle, but it must be clearly marked:

```text
is_complete = false
```

A strategy condition requiring a confirmed close must never receive an in-progress candle as if it were final.

---

# 102. Candle Finalization

A candle becomes complete only after its close boundary plus configured lateness handling.

The live engine may finalize promptly for strategy decisions while later historical revisions remain possible.

Finalization latency must be measured.

---

# 103. Empty Intervals

For an instrument with no trades in an interval, do not fabricate volume.

Depending on consumer needs, the time-series layer may materialize an explicit empty bar with:

```text
volume = 0
trade_count = 0
price = previous close only if documented
quality/context = NO_TRADES
```

or represent absence separately.

The policy must be consistent and versioned.

---

# 104. Missing Data Is Not Zero

A missing feed interval and a real zero-volume interval are different states.

They must never be encoded identically.

---

# 105. Out-of-Order Candle Updates

Late valid events that belong to a previously finalized bar may create a historical candle revision.

The live-as-seen candle remains reproducible.

Corrected historical bars identify the revision source.

---

# 106. Candle Revision Lineage

A corrected bar records:

```text
logical_candle_id
revision
previous_revision
reason
source_dataset_revision
recomputed_at
```

Downstream feature datasets derived from earlier revisions remain identifiable.

---

# 107. OHLC Calculation

For trade-derived bars:

```text
open  = first ordered valid trade
high  = maximum valid trade price
low   = minimum valid trade price
close = last ordered valid trade
```

Quote-derived prices must not silently substitute for trade-derived OHLC.

---

# 108. Volume Calculation

Where incremental trade quantities are reliable:

```text
volume = sum(valid trade quantities)
```

Where only cumulative volume is reliable, use a source-specific documented delta method with reset/anomaly handling.

Volume provenance must be known because RVOL depends on it.

---

# 109. Trade Count

`trade_count` is retained where reconstructable.

It may later support microstructure features such as average trade size or burstiness without requiring raw-tick scans for every query.

---

# 110. Candle VWAP

Trade-derived interval VWAP:

```text
VWAP = Σ(price_i × quantity_i) / Σ(quantity_i)
```

if trade-level quantity is available and valid.

A vendor-provided VWAP is not assumed equivalent unless semantics are verified.

---

# 111. Session VWAP

Intraday session VWAP is a cumulative feature derived from canonical trades/bars since the defined session start.

Its reset boundary is the canonical trading session, not midnight UTC.

---

# 112. Raw vs Adjusted Prices

Historical storage should preserve raw exchange prices.

Adjusted price series for research are separate derived datasets.

Never overwrite original trade/candle prices with split/dividend-adjusted values.

---

# 113. Adjustment Factors

A corporate-action adjustment dataset should expose versioned factors sufficient to create consistent adjusted series.

Features sensitive to long-term continuity may use adjusted history, while execution/backtest fill prices use actual historical tradable prices.

---

# 114. Overnight Gap

The transition between previous session close and current session open is explicit data.

Gap features must use canonical session boundaries and adjusted/unadjusted semantics appropriate to the feature definition.

---

# 115. Pre-Open Data

Pre-open/auction information may be retained separately from continuous-session data.

Whether a strategy uses it is a DD-02 decision.

The data layer should not mix pre-open prints into normal M5 bars unless candle definition explicitly requires it.

---

# 116. Special Sessions

Muhurat trading or other special exchange sessions require explicit calendar/session definitions.

Their bars and time-of-day features must not be forced into a normal-session template without a documented policy.

---

# 117. Candle Validation

Candle validation should include:

```text
low <= open <= high
low <= close <= high
volume >= 0
open_time < close_time
bar belongs to valid session
no unexpected overlap for same instrument/timeframe
```

Violations create data-quality incidents.

---

# 118. Cross-Timeframe Consistency

Derived M5/M15/etc. bars should reconcile with their lower-timeframe components within exact deterministic arithmetic/tolerance.

Daily OHLC/volume should reconcile with intraday source history when both are derived from the same source definition.

---

# 119. Candle Construction Tests

Maintain canonical fixture streams covering:

- ordinary sessions;
- no-trade intervals;
- duplicate ticks;
- late ticks;
- feed reconnects;
- session close partial bars;
- holidays/special sessions;
- corporate-action boundaries.

The same fixtures should pass live/replay candle builders.

---

# 120. Feature Definition Registry

Every feature has an explicit registry entry.

Minimum metadata:

```text
feature_name
feature_version
calculation_version
value_type
input_dependencies
parameters
warmup_requirements
supported_timeframes
quality_requirements
point_in_time_semantics
```

---

# 121. Feature Version vs Calculation Version

`feature_version` represents semantic meaning.

`calculation_version` identifies implementation changes that should produce the same semantics.

Example:

```text
RRS semantic formula changes → new feature_version
performance refactor only → new calculation_version if needed for audit
```

---

# 122. Parameter Hash

Feature instances with configurable parameters expose a deterministic parameter hash.

Example:

```text
RRS_V1
ATR_length = 600 M5 bars
fast = 3
slow = 8
benchmark = NIFTY50
```

must be distinguishable from another RRS_V1 experimental parameter set.

---

# 123. Feature Dependency Graph

Features form a directed acyclic dependency graph where possible.

Example:

```text
Canonical Trades
      ↓
M5 Candle
  ┌───┼──────────┐
  ↓   ↓          ↓
ATR  RVOL       VWAP
  ↓              ↓
  └─────→ RRS ←── Market ATR / Return
```

Dependencies should be machine-readable for replay/backfill planning.

---

# 124. Single Canonical Feature Implementation

Production live and production-grade backtest calculations must reuse the same feature implementation wherever practical.

Python research may implement experimental equivalents, but shared fixtures must detect drift.

---

# 125. No Database Round-Trip in Feature Hot Path

The live feature engine consumes in-memory rolling state.

It must not require a PostgreSQL query for every market event or M5 update.

Historical baselines should be preloaded/cached into controlled state before they are needed.

---

# 126. Feature Warmup

A feature is not valid until its required historical context is available.

Example:

```text
50-session baseline unavailable
        ↓
Daily RVOL quality = INCOMPLETE
```

The engine must not silently compute over fewer periods unless the feature version explicitly permits it.

---

# 127. Feature Observation

A feature observation should identify:

```text
observation_id
instrument_id
anchor_timestamp
timeframe
feature_schema_version
values
quality_state
source_data_revision
calculation_version
```

Wide columnar storage is preferred for analytical feature matrices where practical.

---

# 128. Stable Market Observation Key

Every meaningful completed bar observation should have a stable logical key:

```text
instrument_id
+ timeframe
+ bar_close_timestamp
```

This is the anchor to which features, setup states, patterns, and labels attach.

---

# 129. Observation ID

A compact internal `observation_id` may materialize the stable key for efficient joins.

Identity must not change because a feature version changes.

---

# 130. Feature Snapshot

A `FeatureSnapshot` is the set of point-in-time features available for an observation under a specific schema/version.

It is distinct from a strategy decision.

A stock may have a feature snapshot even when no setup exists.

---

# 131. Feature Schema Version

Because hundreds of feature columns may evolve together, historical matrices expose a `feature_schema_version` describing the set and semantics of included columns.

Individual features still retain their own semantic versions.

---

# 132. Snapshot Quality

A feature snapshot exposes both aggregate and component-level quality where necessary.

One missing optional feature should not necessarily invalidate the entire snapshot.

Required vs optional feature policy belongs to each consumer.

---

# 133. Missing-Feature Representation

Missing, unavailable, not-yet-warmed, and invalid feature values must be distinguishable.

A blanket numeric `0` is prohibited because zero may be a meaningful feature value.

---

# 134. Numerical Representation

Statistical features use `double` or equivalent analytical floating point unless a feature requires exact decimal semantics.

Money/authoritative account values remain governed by DD-04/DD-03 numeric rules.

---

# 135. Numerical Reproducibility

Replay tests define acceptable numerical tolerance for floating-point features.

Any tolerance must be narrow enough that threshold decisions cannot drift silently around boundaries.

Where a feature is a hard gate, decision fixtures should test boundary behavior explicitly.

---

# 136. Benchmark Mapping Version

Every relative feature must identify the benchmark actually used.

For Strategy V1:

```text
primary broad benchmark = NIFTY 50
challenger = NIFTY 500
```

A backtest must not silently substitute one benchmark for another.

---

# 137. Market / Sector / Stock Temporal Alignment

Relative features require timestamp-aligned observations.

A stock M5 bar ending 10:20 IST must compare against the market/sector state for the same canonical interval.

Misaligned bars are a data error, not an acceptable approximation.

---

# 138. Benchmark Staleness

If the broad-market or sector benchmark is stale while the stock feed remains live, relative features become degraded/stale.

The stock's own price remaining fresh does not rescue RRS validity.

---

# 139. NIFTY 50 Primary Benchmark

DD-02 selects NIFTY 50 as the initial Strategy V1 broad-market benchmark.

DD-05 treats it as a normal canonical instrument whose observations participate in the same event/candle/feature lineage.

---

# 140. NIFTY 500 Challenger Dataset

Historical market memory must retain sufficient NIFTY 500 history to test the DD-02 benchmark challenger without rearchitecting data ingestion.

Benchmark experiments are dataset/configuration choices, not bespoke pipelines.

---
# 141. Sector Benchmark Dataset

The historical store must retain the relevant NSE sector index history required to evaluate:

```text
Sector vs Broad Market
Stock vs Sector
Stock vs Broad Market
```

Sector mapping is temporal and versioned.

---

# 142. Cross-Sectional Market Context

Market context may include cross-sectional measurements such as:

- breadth;
- advance/decline;
- percentage above/below moving averages;
- sector leadership;
- watchlist ranking;
- dispersion.

These are derived from point-in-time universe membership and must not use future constituents.

---

# 143. RRS Source Requirement

RDT-derived Real Relative Strength requires synchronized stock and benchmark movement normalized by their expected volatility.

DD-02 defines:

```text
RRS_raw_t
=
(ΔP_stock / ATR_stock)
-
(ΔP_market / ATR_market)
```

DD-05 guarantees the aligned prices, ATR input history, benchmark identity, and version lineage necessary to calculate it.

---

# 144. RRS Raw Feature

Persist/reconstruct:

```text
RRS_raw
```

with metadata identifying:

- stock instrument;
- benchmark instrument;
- timeframe;
- price-change definition;
- ATR definition/length;
- source/candle revision.

---

# 145. RRS Smoothing

RDT material discusses the risk of a one-candle burst producing misleading apparent RS and includes an example using fast/slow smoothing.

DD-05 therefore supports:

```text
RRS_fast
RRS_slow
```

as separately versioned derived features.

---

# 146. RRS Persistence

Persist/reconstruct sufficient history for features such as:

- rolling mean/median RRS;
- proportion of recent bars above/below zero;
- fast-vs-slow relationship;
- sign persistence.

Pattern windows should preserve the RRS trajectory rather than only the latest persistence score.

---

# 147. RRS Trend and Acceleration

Support:

```text
RRS_trend
RRS_slope
RRS_acceleration
```

as derived observations where defined by DD-02 feature versions.

These should remain reconstructable from historical RRS sequences.

---

# 148. Multi-Timeframe RRS

RRS may be calculated independently for:

```text
D1
M5
M15
M30
H1
H2
H4
```

The presence of multiple timeframes does not justify copying stale higher-timeframe final values into earlier historical timestamps.

Each value must reflect only information known as of the anchor.

---

# 149. Stock-vs-Sector RRS

The same core relative-strength representation should support stock vs sector benchmark.

The feature identity includes both `instrument_id` and `benchmark_instrument_id` so different relative relationships cannot be confused.

---

# 150. Sector-vs-Market RRS

Sector strength/weakness is represented through the same benchmark-relative feature family where appropriate.

This enables stacked context:

```text
Sector strong vs NIFTY
AND
Stock strong vs Sector
AND
Stock strong vs NIFTY
```

---

# 151. RRS Historical Percentiles

Historical percentile features are point-in-time rolling distributions.

A percentile at time `t` may use only samples available before or at `t` under the feature's documented window.

Global normalization computed using the full dataset is prohibited for live-equivalent features.

---

# 152. Daily RVOL

DD-02 defines Daily RVOL conceptually as:

```text
current daily volume
/
prior-session volume baseline
```

The baseline dataset must identify:

- lookback sessions;
- estimator type;
- excluded/invalid sessions;
- corporate-action handling;
- baseline version.

---

# 153. Intraday Interval RVOL

Time-of-day-normalized interval RVOL requires a historical baseline for the same canonical session slot.

For M5 slot `s`:

```text
RVOL_interval(t,s)
=
Volume_today(s)
/
ExpectedVolume(s | prior valid sessions)
```

The baseline must not use the current day's future slots.

---

# 154. Intraday Cumulative RVOL

Cumulative RVOL compares today's cumulative volume up to exchange time `τ` with historical cumulative volume up to the same session-relative time.

This requires session-aligned historical curves, not only end-of-day volume.

---

# 155. Volume Baseline Store

Precomputed baseline statistics may be persisted for operational efficiency.

A baseline record should identify:

```text
instrument_id
session_slot
lookback_definition
estimator
sample_count
value
valid_from/as_of
feature_version
```

Baseline caches are derived and rebuildable from historical volume.

---

# 156. Day-of-Week Volume Context

DD-01 anticipates day-of-week normalization.

DD-05 supports conditional baselines such as:

```text
expected M5 volume for RELIANCE at 10:15 on Mondays
```

only if sample size and research justify the added segmentation.

Insufficient sample size must be exposed, not hidden.

---

# 157. Relative Volume Expansion

DD-02 defines the Edge Relative working feature:

> RVE = Relative Volume Expansion

based on fast/slow EWMAs of log RVOL.

DD-05 stores/reconstructs the underlying RVOL trajectory so RVE parameters can be retested without raw re-ingestion.

---

# 158. Directional Volume

RDT emphasizes whether volume accompanies price movement with or against the intended trade.

DD-05 supports rolling features such as:

```text
up_bar_volume
down_bar_volume
directional_volume_ratio
```

The bar-direction rule belongs to the feature definition and is versioned.

---

# 159. OBV

The RDT Wiki discusses On-Balance Volume as a useful volume-context tool.

OBV may be calculated and stored as a research/quality feature.

It is not a Strategy V1 hard gate unless DD-02 research later promotes it.

---

# 160. ATR

ATR is a core dependency for:

- RRS normalization;
- volatility context;
- normalized pattern representations;
- structural-distance features;
- risk calculations.

ATR definition, timeframe, lookback, and adjustment semantics are versioned.

---

# 161. Moving Averages

The feature framework supports SMA/EMA families including the RDT-relevant examples:

```text
3 EMA
8 EMA
50-day MA
100-day MA
200-day MA
```

The exact moving-average type and timeframe are part of the feature identity.

---

# 162. VWAP

RDT explicitly treats VWAP as useful on intraday charts.

DD-05 supports:

- interval VWAP;
- session cumulative VWAP;
- distance to VWAP;
- ATR-normalized VWAP distance.

---

# 163. Heiken-Ashi

Heiken-Ashi candles are derived exclusively from canonical candle data.

They are not an independent market-data source.

HA feature versions must specify:

- input timeframe;
- initialization policy;
- recursive calculation semantics.

---

# 164. Support / Resistance Inputs

Price-structure features require access to historical highs/lows/closes and sufficient lookback windows to derive:

- horizontal support/resistance;
- prior-day high/low;
- all-time/high-period extremes;
- pivot structures.

The raw historical bars must remain available even if the support algorithm changes.

---

# 165. Pivot Features

Candidate pivot/swing features may materialize:

```text
pivot_high
pivot_low
pivot_strength
distance_to_nearest_pivot
```

They are deterministic feature outputs, not manually drawn chart annotations.

---

# 166. Trendline Features

Trendlines require versioned anchor-selection rules.

A future algorithm may change how anchors are detected; therefore historical prices are authoritative and trendline outputs are rebuildable.

---

# 167. Compression Features

Compression detection may consume:

- normalized range;
- ATR;
- overlapping bars;
- rolling high-low width;
- volume contraction.

DD-05 supports storing both raw inputs and derived compression state.

---

# 168. Breakout Features

Breakout observations may include:

```text
break_level
close_beyond_level
break_distance_atr
volume_confirmation
retest_state
```

These are versioned DD-02-derived semantics rather than intrinsic properties of a candle.

---

# 169. Gap Features

Gap measurements should include normalized and raw forms, e.g.:

```text
open - previous_close
gap_percent
gap_atr
```

Corporate-action-adjustment rules must prevent artificial split gaps from being interpreted as trading patterns.

---

# 170. Liquidity Features

Market-data architecture supports features including:

- absolute traded value;
- average traded value;
- bid/ask spread;
- relative spread;
- depth where available;
- quote age;
- average trade size;
- volume concentration.

Liquidity is distinct from RVOL.

---

# 171. Spread Features

If quotes are available, retain enough data to derive:

```text
spread_absolute
spread_bps
spread_percentile
spread_mean
spread_max
spread_at_bar_close
```

over relevant windows.

Spread feature quality degrades if quote coverage is incomplete.

---

# 172. Market Regime Inputs

DD-02 owns market-regime logic.

DD-05 provides the required time-aligned inputs and stores the resulting regime observation with its version.

A regime label such as `BULL_TREND` is derived data, not raw truth.

---

# 173. Sector Context Features

Potential point-in-time sector features include:

- sector return;
- sector RRS vs market;
- sector breadth;
- sector leadership rank;
- number/percentage of constituents with positive RRS;
- dispersion.

Historical constituent membership must be respected.

---

# 174. Time-of-Day Features

Time context is first-class because intraday behavior may be stock-specific.

Each observation should derive:

```text
exchange_date
exchange_local_time
minutes_since_session_open
minutes_to_session_close
session_slot
session_segment
day_of_week
```

These values derive from the canonical exchange calendar.

---

# 175. Session Segment

A coarse categorical session segment may be materialized for analysis, for example:

```text
OPEN
EARLY
MID_MORNING
MIDDAY
AFTERNOON
CLOSE
```

Exact boundaries are research/configuration, not universal market facts.

Continuous `minutes_since_open` must also be retained so future models are not trapped by coarse buckets.

---

# 176. Expiry Context

Where derivatives context is used, an observation may include:

```text
days_to_expiry
is_expiry_day
minutes_to_expiry_session_close
```

These are calendar-derived known-at-time features.

---

# 177. Feature Snapshot Frequency

For the broad learning universe, every completed M5 observation should eventually be representable as a feature snapshot under the selected historical feature schema.

This is the baseline research cadence for intraday pattern memory.

---

# 178. D1 Feature Snapshots

Each completed daily candle should similarly produce or be able to reconstruct a D1 feature snapshot.

D1 snapshots provide higher-timeframe context for every subsequent intraday observation.

---

# 179. Additional Event-Driven Snapshots

In addition to regular M5 cadence, important state transitions may create explicit observations such as:

- setup enters `FORMING`;
- `NEAR_TRIGGER`;
- `VALID`;
- risk rejection;
- strategy invalidation;
- trade entry/exit.

These reference the nearest canonical market observation and do not replace regular snapshots.

---

# 180. Record Ordinary Observations

Edge Relative must not persist only attractive setups.

The learning corpus needs ordinary market states so ML can learn the difference between:

```text
interesting
ordinary
failed
near-miss
invalid
```

Selection-only logging creates biased research data.

---

# 181. Setup Observations

A `SetupObservation` attaches strategy interpretation to an underlying feature observation.

Representative fields:

```text
observation_id
strategy_id
strategy_version
setup_state
direction
hard_gates
quality_factors
reason_codes
```

The feature observation remains valid independently of any strategy.

---

# 182. Non-Trade Reason Codes

Persist reasons a setup was not traded, including:

```text
NO_SETUP
RISK_REJECTED
LOW_RANK
MANUAL_REJECT
DATA_QUALITY_BLOCKED
ML_REJECTED
OUTSIDE_TRADING_WINDOW
```

This supports later counterfactual analysis.

---

# 183. PatternWindow Domain Object

Historical similarity is built around a first-class `PatternWindow`.

A pattern window is a sequence of point-in-time observations ending at an anchor.

It is not simply a text label such as `BREAKOUT`.

---

# 184. Pattern Window Identity

Representative identity:

```text
pattern_window_id
anchor_observation_id
pattern_schema_version
window_definition_id
```

The same anchor may have multiple windows and representations.

---

# 185. Pattern Anchor

For intraday similarity, the anchor is normally a completed canonical observation such as:

```text
RELIANCE
M5
2026-09-04 10:25 IST close
```

All sequence values must be available by that anchor time.

---

# 186. Multiple Pattern Horizons

No single horizon is assumed to capture all useful behavior.

Initial M5 research windows should support candidates such as:

```text
6 bars  = 30 minutes
12 bars = 60 minutes
24 bars = 120 minutes
```

These are research candidates, not permanent optimal values.

---

# 187. Daily Pattern Horizons

D1 pattern windows should support candidate horizons such as:

```text
5 sessions
10 sessions
20 sessions
```

for higher-timeframe structure and stock-specific historical behavior.

---

# 188. Multi-Resolution Pattern Context

A current intraday pattern may combine:

```text
D1 context window
+
M5 sequence window
+
market M5 sequence
+
sector M5 sequence
```

The representation must preserve which timeframe each sequence came from.

---

# 189. Raw Price Is Not Similarity Space

Historical similarity must not primarily compare absolute rupee prices.

A stock trading at ₹900 in one year and ₹1,800 later can exhibit the same behavior.

Pattern representations therefore prefer normalized movement.

---

# 190. Return-Normalized Price Sequence

Core sequence elements may include:

```text
log return
simple return
return / ATR
range / ATR
body / ATR
wick lengths / ATR
```

The selected representation is versioned and research-driven.

---

# 191. Structural Distance Normalization

Distances to context levels should be normalized where possible:

```text
(price - VWAP) / ATR
(price - EMA8) / ATR
(price - resistance) / ATR
(price - support) / ATR
```

This improves comparability across instruments and volatility regimes.

---

# 192. Volume Normalization in Patterns

Pattern windows should use relative rather than raw volume where useful:

```text
interval RVOL
cumulative RVOL
log RVOL
volume percentile
directional volume ratio
```

Raw volume may still be retained as auxiliary context.

---

# 193. Market Sequence in Pattern

A stock pattern should retain the contemporaneous broad-market trajectory.

Examples:

```text
market returns
market ATR-normalized returns
market regime path
market breadth path
```

This helps distinguish identical stock shapes occurring under different market pressure.

---

# 194. Sector Sequence in Pattern

Where a sector benchmark exists, retain contemporaneous sector trajectory and relative behavior.

This enables later research into stacked sector/stock pattern similarity.

---

# 195. Current-State Vector

One pattern representation is a scalar current-state vector:

```text
[RRS, RVOL, RVE, ATR%, VWAP_distance_atr,
 market_state, sector_RRS, liquidity, ...]
```

It is useful for tabular ML and fast candidate filtering but does not preserve full temporal evolution.

---

# 196. Sequence Vector

A sequence representation concatenates or structures recent values across the pattern window.

Example:

```text
RRS[t-11..t]
RVOL[t-11..t]
returns[t-11..t]
market_returns[t-11..t]
sector_RRS[t-11..t]
```

This preserves trajectory.

---

# 197. Shape Representation

A shape representation normalizes each sequence so geometry can be compared independently from absolute magnitude.

Possible normalizations include:

- z-score within past-only window;
- start-at-zero cumulative return;
- ATR scaling;
- range scaling.

Normalization must not use future observations.

---

# 198. Missingness Mask

Pattern representations include explicit masks for unavailable/missing optional features.

A missing depth signal must not numerically resemble a zero imbalance.

---

# 199. Categorical Context

Pattern metadata may include known-at-anchor categories such as:

```text
instrument_id
sector_id
market_regime
day_of_week
session_segment
strategy/setup_state
```

These may be used for filtering or ML but remain separate from raw sequence truth.

---

# 200. Pattern Schema Version

Each pattern representation has a `pattern_schema_version` defining:

- included features;
- sequence ordering;
- window sizes;
- normalization;
- missingness handling;
- benchmark context.

Changing representation semantics creates a new version.

---

# 201. Pattern Materialization Policy

Not every possible pattern representation must be physically precomputed forever.

The architecture supports:

```text
EAGER materialization for common windows
LAZY generation for experimental windows
```

Both derive from stable observation history.

---

# 202. Pattern Metadata in PostgreSQL

PostgreSQL may store compact pattern metadata such as:

```text
pattern_window_id
anchor_observation_id
instrument_id
timeframe
window_definition_id
pattern_schema_version
market_regime
sector_id
session_segment
storage_reference
```

The high-dimensional sequence itself may remain in analytical storage.

---

# 203. Pattern Sequences in Parquet

Large pattern matrices/tensors should normally be stored columnarly or in research-friendly batch formats in object storage rather than row-by-row JSON in PostgreSQL.

The exact physical tensor layout should be benchmarked with Python/Java consumers.

---

# 204. Recent Pattern Cache

The live system may retain recent pattern-window state in memory for the active trading universe.

This avoids rereading the last 12/24 bars from object storage at every M5 close.

---

# 205. Pattern Lineage

A pattern must identify:

```text
source observation range
feature schema version
pattern schema version
normalization version
data revision
```

Similarity results without this lineage are not auditable.

---

# 206. Pattern Revision

If corrected historical market data materially changes a pattern, a new pattern revision is generated.

Prior experiments remain linked to the previous pattern revision.

---

# 207. Human Setup Labels Are Auxiliary

Labels such as:

```text
COMPRESSION_BREAKOUT
TRENDLINE_BREAK
RS_CONTINUATION
```

are useful metadata but must not be the only pattern representation.

The measured market state is primary.

---

# 208. Pattern Identity Is Not Outcome

Pattern representation contains only data available at the anchor.

Future success/failure lives in the separate outcome layer.

This separation is enforced by dataset schemas and training builders.

---

# 209. Ordinary Pattern Windows

Regular M5 observations outside any setup state should still be eligible for pattern-window construction.

This prevents the similarity corpus from containing only hand-selected "interesting" moments.

---

# 210. Similarity Query Contract

The similarity subsystem should accept a structured query such as:

```text
anchor/current pattern
pattern_schema_version
cohort policy
metadata filters
k
similarity_method_version
as_of_limit
```

The `as_of_limit` is mandatory in historical simulation so future matches cannot leak into the past.

---

# 211. Same-Stock Cohort

The first research cohort should retrieve historical patterns from the same stock.

This supports stock-specific behavior such as:

> How has RELIANCE historically behaved under states similar to RELIANCE now?

---

# 212. Same-Stock + Same-Regime Cohort

A more specific cohort conditions same-stock history on comparable market regime or context.

This may improve relevance but can reduce sample size.

Both sample size and conditionality must be reported.

---

# 213. Sector Cohort

A broader cohort searches historically similar patterns among stocks in the same sector.

This is useful when same-stock history is sparse or when sector behavior generalizes.

---

# 214. Behavioral Cohort

Future research may cluster instruments by empirical behavior rather than industry label alone.

A behavioral cluster is model/research metadata, not permanent instrument identity.

---

# 215. Global Cohort

The broad NSE learning universe provides the fallback/global pattern corpus.

Global matches should remain distinguishable from same-stock or same-sector matches in the result.

---

# 216. Do Not Collapse Cohorts Prematurely

Similarity results should initially report separate cohorts rather than blindly mixing them into one score.

Example:

```text
Same stock: N=63
Same sector: N=412
Global: N=8,204
```

DD-09 may later learn how to combine them hierarchically.

---

# 217. Candidate Search Stage 1 — Metadata Filtering

Before expensive similarity comparison, filter on cheap metadata where useful:

- timeframe;
- pattern schema;
- cohort/instrument/sector;
- data quality;
- session-relative constraints;
- optional regime constraints;
- historical `as_of` boundary.

This should reduce the search corpus substantially.

---

# 218. Candidate Search Stage 2 — Fast Similarity Retrieval

A fast approximate or exact vector distance retrieves a manageable candidate set.

Potential implementations include normalized feature distance or future learned embeddings.

Technology choice remains abstract in DD-05.

---

# 219. Candidate Search Stage 3 — Precise Re-Ranking

The best candidates may be re-ranked with more expensive sequence-aware metrics.

Possible challengers include:

- weighted normalized Euclidean distance;
- cosine similarity;
- correlation distance;
- Mahalanobis distance;
- Dynamic Time Warping;
- learned sequence similarity.

No metric is declared superior without out-of-sample evidence.

---

# 220. Similarity Result

A `PatternMatch` should expose at minimum:

```text
query_pattern_id
matched_pattern_id
cohort
similarity_score
similarity_method_version
rank
anchor_timestamp
instrument_id
outcome_reference_if_available
```

The similarity score alone must not imply trade authority.

---
# 221. Similarity Score Semantics

Similarity score semantics must be documented per method.

A score of `0.92` is meaningless unless the method defines whether higher/lower is better and how the value was normalized.

Cross-method scores must not be compared directly without calibration.

---

# 222. Time-of-Day in Similarity

Time-of-day is potentially predictive and must be available as a first-class filter/feature.

However, the engine should support both:

```text
same-time-of-day matches
```

and:

```text
broader-time matches
```

so research can determine whether temporal conditioning adds value rather than assume it.

---

# 223. Regime Conditioning in Similarity

Likewise, market regime may be used as:

- hard filter;
- soft feature;
- post-retrieval stratification.

The architecture supports all three because hard filtering too early may hide useful cross-regime analogues.

---

# 224. Recency

Recent historical patterns may deserve different statistical weight than very old patterns, but recency weighting is not a storage/index truth.

Store timestamps exactly; apply recency weighting in research/model logic.

---

# 225. Sample Size Reporting

Every similarity-derived statistic must report effective sample size.

A statement such as:

```text
P(+1R first) = 80%
```

must not appear without context such as:

```text
N = 5
```

versus:

```text
N = 250
```

Confidence intervals/uncertainty belong in DD-09/research outputs.

---

# 226. Search Diversity

A top-k search can be dominated by near-duplicate observations from adjacent bars of the same historical episode.

The query layer should support de-duplication/diversity constraints such as:

- minimum time separation;
- one representative per historical session/window;
- capped matches per instrument/session.

Otherwise the sample size may be illusory.

---

# 227. Historical `As-Of` Boundary

In a replay at historical time `T`, similarity search may only query patterns with anchor timestamps strictly before the permitted historical cutoff.

Searching the full future corpus would introduce direct look-ahead leakage.

This boundary must be enforced by the similarity API, not left to caller discipline.

---

# 228. Real-Time Similarity Goal

Historical similarity should eventually be available quickly enough to inform post-M5-close decisioning.

Initial design target:

> Pattern construction + candidate retrieval + outcome summary should complete within low single-digit seconds at normal live load.

This is an engineering SLO target, not a trading-edge assumption.

---

# 229. Similarity Index Abstraction

The domain depends on an abstraction rather than a specific vector product.

Conceptually:

```java
interface PatternSimilarityIndex {
    List<PatternMatch> nearest(PatternQuery query);
}
```

This permits implementation changes without changing strategy/research contracts.

---

# 230. Initial Similarity Implementation

V1 research may begin with straightforward exact searches over bounded datasets before introducing ANN complexity.

The purpose is to validate representation quality before optimizing retrieval technology.

---

# 231. Future ANN Technology

Potential future implementations may include:

- PostgreSQL vector extensions;
- FAISS-like local indexes;
- dedicated vector-search infrastructure.

DD-05 deliberately does not select one before actual corpus size and latency are measured.

---

# 232. Similarity Index Is Rebuildable

All indexes must be rebuildable from stable pattern/embedding datasets.

An index is an acceleration structure, not authoritative data.

---

# 233. Index Version

Each similarity index identifies:

```text
index_version
pattern_schema_version
embedding_version_if_used
similarity_method_version
indexed_time_range
indexed_dataset_revision
build_timestamp
```

---

# 234. Deterministic Fallback

If an ANN index is unavailable or suspect, research/live advisory logic should be able to fall back to a slower exact/bounded method or no similarity result.

Similarity failure must never force a trade.

---

# 235. Learned Embeddings

A future model may transform a pattern window into a fixed-dimensional vector:

```text
Pattern sequence
      ↓
Embedding model
      ↓
vector[d]
```

Embedding dimensionality is model metadata and not fixed by DD-05.

---

# 236. Embedding Versioning

An embedding record identifies:

```text
pattern_window_id
embedding_model_id
embedding_model_version
pattern_schema_version
vector
created_at
```

Embeddings from incompatible models are not searched in the same index unless explicitly transformed/calibrated.

---

# 237. Offline Embedding Backfill

When a new embedding model is promoted for research, historical embeddings should be batch-generated from existing pattern windows.

This is why pattern/raw history is preserved independently.

---

# 238. Live Embedding Generation

For live similarity, the same embedding transformation used on historical patterns must generate the current query vector.

Different preprocessing between offline and live is prohibited.

---

# 239. Embedding Does Not Replace Feature Explainability

Even if learned embeddings outperform hand-defined distances, the system should retain interpretable feature values and matched historical examples.

A user/model should be able to inspect why a historical observation is relevant beyond an opaque vector score where practical.

---

# 240. Approximate Search Validation

ANN retrieval quality must be benchmarked against exact nearest neighbors on representative samples.

Measure at least:

- recall@k;
- query latency;
- index size;
- build time;
- sensitivity to corpus growth.

---

# 241. OutcomeLabel Domain Object

Every historical anchor may eventually receive one or more future-outcome label sets.

Labels attach to `observation_id` / `pattern_window_id` but live in a logically separate namespace.

---

# 242. Forward Return Labels

Support labels such as:

```text
return_1_bar
return_3_bar
return_6_bar
return_12_bar
return_15m
return_30m
return_60m
return_to_close
```

Exact horizons depend on timeframe and research objective.

---

# 243. MFE / MAE Labels

For each defined future horizon, support:

```text
MFE
MAE
```

in raw return, ATR-normalized, and potentially R-multiple form where a valid trade-plan reference exists.

---

# 244. Target / Stop Sequence Labels

For a historical hypothetical trade plan, outcome labels may include:

```text
target_1R_hit
target_2R_hit
stop_hit
target_before_stop
stop_before_target
```

These labels require explicit entry/stop/target definitions from a strategy/trade-plan version and are not generic market facts.

---

# 245. Time-to-Event Labels

Support:

```text
time_to_1R
time_to_2R
time_to_stop
time_to_MFE
```

This is important for future timing and trade-management intelligence.

---

# 246. Label Availability

An outcome label is only complete after the required future horizon has elapsed or the outcome event is conclusively resolved.

Recent observations may have `label_state = PENDING`.

---

# 247. Censoring

If a horizon extends beyond session close or data becomes unavailable, the label must record censoring rather than invent a value.

Possible states:

```text
COMPLETE
RIGHT_CENSORED
DATA_INVALID
NOT_APPLICABLE
```

---

# 248. Labels Are Never Live Features

The feature store and live API must be structurally incapable of exposing future labels to the strategy/ML input builder at the same anchor.

This should be enforced through separate schemas/dataset builders and leakage tests.

---

# 249. Label Versioning

Label definitions are versioned because outcomes depend on:

- price source;
- horizon;
- intrabar ordering assumptions;
- stop/target definitions;
- corporate-action handling;
- transaction-cost model where applicable.

---

# 250. Market Outcome vs Execution Outcome

Distinguish:

```text
MARKET OUTCOME
what price path did
```

from:

```text
EXECUTION OUTCOME
what our simulated/real order achieved
```

DD-05 stores market-path labels; DD-06/DD-07 add execution simulation/realized fills.

---

# 251. Cost-Adjusted Labels

Cost-adjusted research outcomes may be materialized later, but transaction-cost assumptions must be versioned and never replace raw market outcomes.

---

# 252. Executed and Non-Executed Observations

Outcome labels must be produced for sufficiently broad non-traded observations, not only executed trades.

This supports learning from:

- skipped valid setups;
- risk rejections;
- lower-ranked candidates;
- near-setups;
- ordinary market states.

---

# 253. Counterfactual Strategy Labels

A research dataset may ask:

> What would have happened if Strategy V2 had acted on this historical observation?

Such counterfactual labels identify the strategy version and are separate from what the live system actually decided at the time.

---

# 254. Dataset Builder

ML/research datasets are generated through an explicit builder that joins:

```text
historical observations
+
point-in-time features
+
allowed metadata
+
future labels
```

The builder produces a dataset manifest recording exact inputs and temporal rules.

---

# 255. As-Of Join

Historical context joins must be `as-of` joins, not ordinary joins that can accidentally select future data.

Examples:

- sector mapping valid at anchor date;
- latest known D1 feature at intraday anchor;
- volume baseline calculated only from prior sessions;
- model/strategy version active at the anchor if reproducing live decisions.

---

# 256. Higher-Timeframe Availability

At 11:00 intraday, the current D1 candle is incomplete.

A feature definition must explicitly state whether it uses:

- previous completed D1 bar only;
- current in-progress D1 state;
- both as separate features.

Historical dataset builders must reproduce the same choice.

---

# 257. Rolling Normalization

Any rolling z-score, percentile, mean, variance, quantile, or baseline used as an input is calculated using prior/available data only.

Full-sample normalization belongs only in explicitly offline exploratory analysis, never live-equivalent training features.

---

# 258. Historical Constituent Leakage

Market/sector breadth and cross-sectional features must use constituents valid at that historical time.

Using today's NIFTY membership throughout ten years of history is prohibited.

---

# 259. Survivorship Bias

The learning universe should retain delisted/renamed/merged instruments where licensing/data allow.

Restricting history to currently surviving symbols can materially bias research.

---

# 260. Corporate-Action Leakage

Corporate-action adjustments may be applied retrospectively for mathematically continuous series, but metadata known only later must not become a contemporaneous predictive feature.

Raw and adjusted series remain separate so this distinction is auditable.

---

# 261. Data Quality in Training Sets

Training builders must filter or flag poor-quality observations using explicit rules.

Silently training on periods with missing market benchmark or broken volume can teach the model data artifacts instead of market behavior.

---

# 262. Dataset Manifest

Every research/ML dataset records:

```text
dataset_id
feature_schema_version
pattern_schema_version_if_any
label_version
source_data_revision
universe_version
time_range
as_of_rules
filter_rules
row_count
code_version
created_at
```

---

# 263. Reproducibility

Given a dataset manifest and retained source versions, the system should be able to rebuild the same logical dataset.

Reproducibility is required for strategy/model validation and audit.

---

# 264. Stock Identity as Feature

DD-09 may choose to include stock identity or learned stock embeddings.

DD-05 ensures stable historical instrument identity so renamed symbols do not fragment the stock's history.

---

# 265. Sector Identity as Feature

Sector identity is available point-in-time and versioned.

Models may use it directly, hierarchically, or not at all.

The data layer does not prescribe the modeling choice.

---

# 266. Time-of-Day Intelligence

The market-memory architecture explicitly supports future questions such as:

> At what times does this stock's validated RS-continuation pattern historically perform best?

Required data includes:

- continuous minutes since open;
- session slot;
- session segment;
- stock identity;
- pattern/feature state;
- outcome labels.

---

# 267. Example Stock-Specific Query

A future research query may ask:

```text
Instrument: RELIANCE
Pattern: persistent positive RRS
Sector: strong
Market: bullish
RVOL: elevated
Anchor time: 10:45–11:30
```

and compare resulting forward-outcome distributions with the same pattern at other times.

DD-05 ensures this query can be answered without reconstructing missing context from trade logs.

---

# 268. Example Similarity Query

A live advisory request may conceptually return:

```text
Current: HDFCBANK M5 11:05

Same-stock nearest episodes: 35
Same-sector nearest episodes: 80
Global nearest episodes: 100

Outcome summaries:
+30m return distribution
+60m return distribution
MFE / MAE
1R-before-stop probability where applicable
```

The numerical interpretation and confidence belong to DD-09.

---

# 269. Pattern Timing Intelligence

Pattern outcomes should include duration because "what happened" is incomplete without "how long it took."

Future models may learn that a setup which normally resolves within 30 minutes is deteriorating if it remains stagnant for 70 minutes.

---

# 270. Similarity Has No Initial Trading Authority

Historical similarity is initially evidence only.

The authority chain remains:

```text
DD-02 deterministic validity
        ↓
Similarity / ML evidence
        ↓
Ranking / filtering only after validation
        ↓
DD-03 risk
```

A close historical match cannot manufacture a trade that DD-02 marks invalid.

---

# 271. Live Processing Pipeline

Initial live pipeline:

```text
Provider/Broker Feed
       ↓
Source Adapter
       ↓
Canonical Normalizer
       ↓
Quality / Ordering / Dedup
       ↓
In-Memory Market State
       ↓
Candle Builder
       ↓
Feature DAG
       ↓
Market Observation / Snapshot
       ↓
Strategy / Risk
       ↓
Async Historical Persistence
       ↓
Pattern / Outcome Research Pipeline
```

---

# 272. M5 Close Pipeline

At each completed M5 close for a learning-universe instrument:

```text
Finalize M5
    ↓
Update M5 features
    ↓
Attach D1 / market / sector as-of context
    ↓
Create/identify observation
    ↓
Persist/queue feature snapshot
    ↓
If active trading symbol:
    evaluate strategy
    ↓
Optionally build live pattern query
```

---

# 273. Broad-Universe Compute Policy

The broad learning universe does not require every expensive feature on every tick.

A tiered computation model is preferred:

```text
Tier A — active 20: full live features, intrabar state if needed
Tier B — broad liquid universe: M1/M5 + core features at bar close
Tier C — archival universe: raw/candle retention; expensive features backfilled offline
```

This controls compute cost without sacrificing long-term data potential.

---

# 274. Backfill Pipeline

Historical import/backfill should normalize source history into the same canonical contracts used by live data.

Backfill must not create a separate "research-only" candle definition.

---

# 275. Historical Vendor Bars

If only vendor OHLCV is available for older periods, store it with explicit provenance and quality semantics.

Do not pretend vendor bars are equivalent to internally trade-derived bars when raw source ticks were unavailable.

---

# 276. Feature Backfill

Feature backfill processes historical canonical bars/events through versioned feature implementations and writes immutable feature datasets.

Backfill jobs must be restartable and idempotent at dataset-partition level.

---

# 277. Pattern Backfill

Pattern-window materialization derives from feature observations after feature backfill.

If a pattern schema changes, rebuild pattern datasets without modifying feature history.

---

# 278. Label Materialization

Outcome labels are generated only after canonical future prices are available and validated.

Label jobs should support incremental daily completion for newly matured observations.

---

# 279. Daily Post-Market Validation

After session close, run validation including:

- expected bar counts;
- missing instruments;
- benchmark completeness;
- volume consistency;
- cross-timeframe reconciliation;
- source sequence gaps;
- suspicious price anomalies;
- feature warmup/quality failures.

The result becomes a daily data-quality report.

---

# 280. Reprocessing Policy

Any change to:

- normalization;
- candle definition;
- feature semantics;
- corporate-action adjustment;
- pattern representation;
- label definition;

must declare which downstream datasets require rebuild.

Dependency metadata should automate this impact analysis where feasible.

---

# 281. Data Correction Isolation

A historical correction should not alter live trading audit history.

Preserve:

```text
what live system saw
what corrected history later says
```

Both can be analytically valuable.

---

# 282. Source Upgrade / Provider Migration

If the market-data provider changes, DD-05 requires overlap/validation where possible.

Measure:

- price agreement;
- volume agreement;
- timestamp semantics;
- quote/depth semantics;
- missing-event behavior;
- bar reconciliation.

A provider migration must not silently change historical feature meaning.

---

# 283. Data Quality Incident

A structured incident record should capture:

```text
incident_id
source_id
start_time
end_time
affected_instruments
affected_datasets
quality_state
root_cause
recovery_action
trading_impact
```

---

# 284. Observability Metrics

Core market-data metrics include:

- event rate by source/type;
- receive latency;
- processing latency;
- queue depth;
- duplicate rate;
- late-event rate;
- gap count;
- stale age;
- candle-finalization latency;
- missing-bar count;
- persistence lag;
- backfill lag;
- data-quality state counts.

---

# 285. Feature Metrics

Feature-engine metrics include:

- update latency;
- snapshot latency;
- warmup failures;
- missing dependencies;
- benchmark alignment failures;
- quality downgrades;
- per-feature error counts;
- live/replay fixture divergence.

---

# 286. Similarity Metrics

Future similarity infrastructure should measure:

- query latency p50/p95/p99;
- candidate count after filters;
- ANN recall vs exact sample;
- index age;
- index coverage;
- cohort sample sizes;
- failed/no-match queries.

---

# 287. Storage Metrics

Monitor:

- bytes/day by dataset;
- rows/day;
- file counts;
- average file size;
- compaction backlog;
- object-storage request rate;
- PostgreSQL metadata growth;
- backup status;
- checksum failures.

---

# 288. Capacity Planning

Capacity planning must use measured event rates from selected vendors and the learning universe.

Do not design storage based solely on the active 20-stock watchlist if the learning universe is intentionally broad.

---

# 289. Market Depth Retention Policy

Full depth can become much larger than trades/quotes.

DD-05 therefore adopts:

> Support depth structurally from day one; retain it when available and economically justified; do not make V1 strategy correctness dependent on historical full-depth availability.

Depth value is a research hypothesis.

---

# 290. Tick vs M1 Research Value

Where raw tick history is available, retain it because future research may discover features unavailable from M1 bars.

Where it is unavailable, M1 remains the minimum preferred long-term canonical bar history for broad-universe pattern research.

---

# 291. Research Hypothesis D1 — Same-Stock Similarity

> Historical matches from the same stock contain more predictive information about subsequent intraday outcomes than equally close matches from the global NSE universe.

---

# 292. Research Hypothesis D2 — Sector Hierarchy

> Combining same-stock, same-sector, and global cohorts hierarchically improves predictive stability versus either same-stock-only or global-only matching.

---

# 293. Research Hypothesis D3 — Sequence Beats Snapshot

> Temporal feature trajectories materially outperform current-state scalar vectors for predicting forward outcomes.

This tests the foundational decision to make pattern windows first-class.

---

# 294. Research Hypothesis D4 — ATR-Normalized Shape

> ATR/volatility-normalized price and structural-distance sequences produce more transferable historical matches than raw price/percentage-only representations.

---

# 295. Research Hypothesis D5 — Time-of-Day Edge

> For at least some stocks/setups, conditional expectancy differs materially by session time after controlling for market regime and setup quality.

This directly tests the long-term stock-specific timing hypothesis.

---

# 296. Research Hypothesis D6 — Sector Context

> Including contemporaneous sector trajectory improves similarity quality and forward-outcome prediction beyond stock + broad-market state alone.

---

# 297. Research Hypothesis D7 — Learned Embeddings

> A learned sequence embedding produces better out-of-sample neighbor outcome consistency than hand-designed normalized-distance representations.

Hand-designed similarity remains the baseline.

---

# 298. Research Hypothesis D8 — Depth/Microstructure

> Quote/depth-derived features add enough predictive or execution value to justify their materially higher data-retention cost.

Until demonstrated, depth is optional research enrichment.

---

# 299. Research Hypothesis D9 — Recency Weighting

> More recent historical analogues deserve greater weight because stock/institutional behavior drifts over time.

This must be tested against the loss of sample size and regime coverage.

---

# 300. Research Hypothesis D10 — Pattern Duration

> Different setup families and stocks have different optimal context-window horizons rather than one universal 60-minute pattern window.

---
# 301. Research Hypothesis D11 — Broad Learning Universe

> A broad liquid NSE learning universe materially improves stock-specific and cross-stock model quality compared with collecting only the active 20-stock watchlist.

This should be measured against storage and data-quality costs.

---

# 302. Research Hypothesis D12 — Ordinary Observations

> Including regular non-setup observations improves calibration and reduces selection bias versus training only on strategy-qualified/near-qualified setups.

---

# 303. Research Hypothesis D13 — Market-As-Seen vs Corrected History

> Preserving arrival-time/live-as-seen data materially improves the realism of live-performance estimation compared with corrected historical data alone.

---

# 304. Research Hypothesis D14 — Multi-Resolution Context

> Combining D1 context with M5 temporal trajectories improves outcome prediction beyond either D1-only or M5-only pattern representations.

---

# 305. Research Hypothesis D15 — Institutional Footprint Repeatability

> Conditional price/volume/relative-strength trajectories exhibit statistically repeatable behavior for some stock × regime × time-of-day combinations.

This is the appropriate empirical form of the institutional-pattern thesis.

The hypothesis must be rejected for stocks/contexts where evidence does not support it.

---

# 306. Core PostgreSQL Logical Tables

The initial relational metadata model should anticipate tables conceptually equivalent to:

```text
instrument
instrument_alias
broker_instrument_mapping
sector
instrument_sector_mapping
universe
universe_membership
trading_calendar
trading_session
corporate_action
market_data_source
market_data_incident
feature_definition
feature_schema
market_observation
setup_observation
dataset_manifest
pattern_window_metadata
similarity_index_metadata
outcome_label_metadata
```

Exact SQL DDL belongs in the detailed persistence/schema design, but DD-05 fixes their responsibilities.

---

# 307. `market_observation` Logical Schema

Recommended logical fields:

```text
observation_id           BIGINT/UUID
instrument_id            internal key
timeframe_id              registry key
bar_open_timestamp        UTC
bar_close_timestamp       UTC
candle_revision           integer
quality_state             enum
canonical_dataset_id      manifest reference
created_at                UTC
```

Unique logical identity:

```text
instrument_id + timeframe_id + bar_close_timestamp
```

The row is a stable join anchor rather than a giant feature-value table.

---

# 308. `feature_definition` Logical Schema

Representative fields:

```text
feature_definition_id
feature_name
feature_version
calculation_version
value_type
description
point_in_time_semantics
warmup_definition
parameter_schema
status
created_at
```

Feature calculation source/version should be traceable to repository code.

---

# 309. `feature_schema` Logical Schema

A feature schema groups compatible feature definitions into a materialized snapshot shape.

Representative fields:

```text
feature_schema_id
schema_name
schema_version
feature_definition_ids
created_at
status
```

The actual wide matrix may live in Parquet.

---

# 310. Feature Snapshot Physical Strategy

For broad historical research, avoid an entity-attribute-value table containing one PostgreSQL row per feature per observation.

That design would create excessive row counts and poor analytical scan locality.

Preferred model:

```text
PostgreSQL → observation/catalog metadata
Parquet     → wide feature matrices
```

A recent operational subset may be persisted relationally if needed for UI/debugging.

---

# 311. Feature Matrix Row Shape

A historical feature-matrix Parquet row should conceptually resemble:

```text
observation_id
instrument_id
anchor_timestamp
timeframe
feature_schema_version
quality_mask

rrs_raw
rrs_fast
rrs_slow
rrs_persistence
rvol_interval
rvol_cumulative
rve
atr_pct
vwap_distance_atr
...
```

Columnar format permits efficient projection of only the features needed by an experiment.

---

# 312. Raw Trade Parquet Shape

Conceptual columns:

```text
event_id
instrument_id
exchange_timestamp
source_id
source_sequence
received_timestamp
processed_timestamp
price
quantity
trade_id
quality_state
data_revision
trading_date
```

Partition/layout details are benchmarked separately.

---

# 313. Quote Parquet Shape

Conceptual columns:

```text
event_id
instrument_id
exchange_timestamp
source_id
received_timestamp
bid_price
bid_quantity
ask_price
ask_quantity
quality_state
data_revision
trading_date
```

This remains distinct from trades.

---

# 314. Candle Parquet Shape

Conceptual columns:

```text
instrument_id
timeframe
open_timestamp
close_timestamp
open
high
low
close
volume
trade_count
vwap
is_complete
quality_state
revision
source_dataset_id
```

Candles should be sorted physically by instrument/time for efficient sequential pattern extraction within files/partitions.

---

# 315. Pattern Window Storage Shape

A common pattern dataset may use one row per anchor with fixed-size sequence columns for a particular schema/window, or a long-form row-per-step representation.

Both should be benchmarked.

Candidate fixed-window representation:

```text
pattern_window_id
anchor_observation_id
instrument_id
anchor_timestamp
pattern_schema_version

return_seq[12]
rrs_seq[12]
rvol_seq[12]
market_return_seq[12]
sector_rrs_seq[12]
missing_mask[...]
```

Fixed-size arrays can be efficient for ML batches but should not be forced on variable-length future models.

---

# 316. Outcome Dataset Shape

Conceptual wide label row:

```text
observation_id
label_version
label_state
return_15m
return_30m
return_60m
return_close
mfe_30m
mae_30m
mfe_60m
mae_60m
time_to_mfe
...
```

Strategy-dependent stop/target labels may live in a separate dataset keyed by strategy/trade-plan definition.

---

# 317. Query Pattern Q1 — Instrument Time Range

Frequent requirement:

> Load RELIANCE M5 observations/features for a date range.

Physical sorting/indexing should make sequential instrument-time scans efficient.

---

# 318. Query Pattern Q2 — Cross-Section at Time

Frequent requirement:

> Rank all learning-universe stocks by RRS/RVOL at 11:00 on a historical date.

Feature datasets should support efficient date/time partition pruning and cross-sectional scans.

---

# 319. Query Pattern Q3 — Same-Stock Historical Pattern Search

Frequent requirement:

> Retrieve candidate RELIANCE M5 pattern windows before the current/historical as-of time.

Pattern metadata/indexing should be selective on:

```text
instrument_id
pattern_schema_version
anchor_timestamp
```

---

# 320. Query Pattern Q4 — Sector Historical Search

Frequent requirement:

> Retrieve same-sector patterns under comparable context.

Temporal sector mapping may require joining anchor date to valid mapping before/vector alongside similarity retrieval.

---

# 321. Query Pattern Q5 — Outcome Join

Frequent requirement:

> For matched pattern IDs, summarize subsequent MFE/MAE/returns.

Outcome lookup should be batch-oriented by observation/pattern IDs rather than one remote query per match.

---

# 322. Query Pattern Q6 — Full-Day Replay

Frequent requirement:

> Replay all relevant market/benchmark events for a historical session in deterministic event order.

Raw/canonical datasets should support date/time scans without opening thousands of tiny files.

---

# 323. Query Pattern Q7 — Feature Rebuild

Frequent requirement:

> Recalculate RRS/RVOL/etc. for a date range and broad universe under a new feature version.

Canonical M1/M5 + benchmark/sector data should be batch-readable with predictable partition pruning.

---

# 324. PostgreSQL Indexing Principles

Relational indexes should support metadata/operational queries, not emulate columnar analytics.

Likely useful index dimensions include:

- instrument + validity period;
- observation instrument + timeframe + timestamp;
- dataset logical name + version;
- pattern metadata instrument/sector + schema + timestamp;
- quality incidents by source/time.

Exact indexes belong to DDL benchmarking.

---

# 325. Object-Store Layout Principle

Physical object layout must balance two conflicting access paths:

```text
instrument-longitudinal scans
cross-sectional date scans
```

Therefore exact partition strategy should be selected using benchmarked representative queries rather than ideology.

A hybrid of date partitions plus sorting/bucketing by instrument is a likely candidate.

---

# 326. Storage Benchmark Requirement

Before finalizing physical Parquet partitioning, benchmark at least:

- one-stock multi-year scan;
- one-day all-stock scan;
- 20-stock replay session;
- broad-universe feature backfill;
- pattern-window extraction;
- outcome join;
- file compaction.

Record throughput, latency, memory, file count, and object-store requests.

---

# 327. No Premature Time-Series Database

DD-05 does not introduce a dedicated time-series database initially.

The chosen stack is sufficient to validate workload shape first:

```text
PostgreSQL metadata/operational
+
Parquet analytical history
```

A specialized store must earn its complexity through measured requirements.

---

# 328. No Premature Vector Database

Likewise, a dedicated vector database is not an initial dependency.

Exact/limited search or a PostgreSQL/local vector implementation can validate whether historical similarity adds value before specialized infrastructure is adopted.

---

# 329. No Premature Streaming Platform

A distributed event platform is not required while the Java modular monolith remains the primary processing authority.

In-process bounded queues and durable analytical writes are sufficient initially.

Future independent consumers may justify an event broker later.

---

# 330. Pattern-Matching Performance Strategy

Efficient similarity retrieval uses layered reduction:

```text
Millions of observations
        ↓
Metadata / as-of / quality filter
        ↓
Thousands of eligible candidates
        ↓
Fast vector retrieval
        ↓
Dozens/hundreds of nearest candidates
        ↓
Optional expensive sequence re-ranking
        ↓
Outcome distribution
```

This prevents brute-force expensive sequence comparison over the entire corpus on every live query.

---

# 331. Avoiding Adjacent-Bar Leakage in Evaluation

When evaluating pattern matching statistically, adjacent anchors from the same historical episode can create train/test dependence.

Research splits and match-dedup rules should account for overlapping windows so apparent performance is not driven by near-identical samples.

---

# 332. Purged Temporal Evaluation Support

DD-05 metadata must make it possible for DD-09/DD-06 to implement purged/embargoed temporal validation around overlapping pattern windows.

Anchor timestamps and window start/end boundaries are therefore explicit fields.

---

# 333. Event/Feature Revision Freeze for Experiments

An experiment references immutable dataset revisions.

If corrected data later appears, the original experiment remains reproducible and a new experiment may run on the corrected revision.

---

# 334. Data Authority Hierarchy

For historical research:

```text
Raw source observation
    ↓
Canonical validated history
    ↓
Canonical candle
    ↓
Feature snapshot
    ↓
Pattern representation
    ↓
Embedding/index
```

A lower layer may be rebuilt from a higher-authority layer; the reverse is generally impossible.

---

# 335. Data Quality vs Data Availability

A provider returning a value is not the same as the value being trustworthy.

All consumers should reason over:

```text
value + quality + timestamp + lineage
```

rather than value alone.

---

# 336. Security and Access Control

Historical market data may be commercially sensitive/licensed.

Database/object-store access should follow least privilege.

Python research credentials may read approved operational/reference datasets but must not mutate authoritative production trading state, consistent with DD-04A.

---

# 337. Research Dataset Isolation

Research outputs should live in research-owned schemas/buckets/dataset namespaces.

A notebook or ML experiment must not overwrite canonical production history.

---

# 338. Production Feature Promotion

An experimental Python feature becomes production-authoritative only after:

```text
research definition
    ↓
formal specification
    ↓
Java canonical implementation
    ↓
shared fixtures
    ↓
replay validation
    ↓
strategy research
```

Historical research implementation and production implementation must not drift silently.

---

# 339. Shared Fixtures

Critical features such as RRS/RVOL/RVE should have fixture datasets containing:

- canonical input candles/events;
- expected intermediate values;
- expected feature outputs;
- tolerance rules.

Java and Python implementations use the same fixtures.

---

# 340. Golden Historical Days

Maintain a small set of real historical NSE sessions as golden replay datasets covering:

- trend day;
- range/chop day;
- gap day;
- high-volatility day;
- low-volume day;
- feed anomaly scenario where possible.

They serve as integration/regression tests for candle/features/strategy lineage.

---

# 341. DD-05 Research Order

Recommended sequence:

```text
1. Establish instrument/calendar/reference correctness
2. Establish canonical M1/M5/D1 candle pipeline
3. Establish event timestamps/quality/replay
4. Implement RRS/RVOL/RVE canonical feature fixtures
5. Build broad-universe M5 feature snapshots
6. Build outcome labels
7. Establish point-in-time dataset builder
8. Implement simple same-stock exact pattern matching
9. Compare snapshot vs sequence representations
10. Add sector/global cohorts
11. Add time-of-day conditioning
12. Benchmark physical storage layout
13. Add learned embeddings only after simple baselines
14. Evaluate quote/depth incremental value
```

---

# 342. First Data Milestone

The first milestone is:

> Can Edge Relative replay one complete historical NSE session and reproduce identical canonical M5/D1 bars and RRS/RVOL/RVE feature states using the same Java logic intended for live trading?

Until this works, historical ML is premature.

---

# 343. Second Data Milestone

Then:

> Can the system generate point-in-time-correct M5 feature snapshots and future outcome labels for a broad NSE learning universe without selection bias?

---

# 344. Third Data Milestone

Then:

> Given a current/historical M5 pattern, can the system retrieve prior same-stock analogues efficiently and summarize their forward outcomes without future leakage?

This is the first end-to-end market-memory proof.

---

# 345. Fourth Data Milestone

Then compare:

```text
current-state vector
vs
hand-designed sequence similarity
vs
shape-normalized sequence
vs
learned embedding
```

using strict chronological out-of-sample evaluation.

---

# 346. Parameters Intentionally Left for Benchmarking/Research

The following are intentionally not fixed in DD-05:

- exact learning-universe membership/filter;
- exact raw-depth retention duration;
- exact Parquet partition/bucketing strategy;
- target Parquet file size;
- compression codec;
- feature persistence cadence below M5;
- exact pattern-window horizons;
- exact normalization method per pattern family;
- exact similarity metric;
- exact ANN technology;
- embedding dimension/model;
- cohort-combination weights;
- recency weighting;
- similarity k;
- time-of-day bucket boundaries;
- sample-size thresholds;
- data staleness thresholds.

These are design parameters to be measured, not guessed.

---

# 347. Decisions Fixed by DD-05

The following are architectural decisions, not open experiments:

```text
Broad learning universe is separate from 20-stock trading universe.
Raw/canonical history is retained where permitted.
M1 is the preferred broad-universe canonical bar base.
D1/M5 and RDT-relevant higher timeframes are deterministic derivatives.
Market/sector/stock observations are time-aligned.
Feature semantics are versioned and replayable.
Every completed M5 observation can participate in learning.
Ordinary/non-trade observations are retained.
PatternWindow is first-class.
Pattern trajectories are preserved, not just scalar snapshots.
Absolute price is not the primary similarity space.
Outcome labels are strictly separated from features.
Historical similarity enforces an as-of boundary.
Embeddings/indexes are rebuildable acceleration structures.
PostgreSQL owns metadata/operational state.
Parquet/object storage owns large analytical history.
Data quality can block trading.
```

---

# 348. What Remains Outside DD-05

## DD-06 — Backtesting & Research

- replay scheduling semantics;
- fill simulation;
- transaction costs;
- walk-forward engine;
- experiment execution;
- Monte Carlo.

## DD-08 — Live Execution

- OMS;
- order routing;
- fills;
- reconciliation;
- broker recovery.

## DD-09 — ML & Stock Intelligence

- embedding model architecture;
- similarity-to-probability conversion;
- hierarchical stock/sector/global models;
- calibration;
- ranking/filtering authority;
- drift.

## Detailed Persistence / System DDL

- exact PostgreSQL SQL DDL;
- indexes and constraints;
- Flyway migration sequence;
- exact physical object-store paths;
- benchmark-selected partition sizes.

---

# 349. Market Memory Architecture Summary

The intended architecture is:

```text
                      EDGE RELATIVE MARKET MEMORY

Broker / Vendor / Exchange-derived feeds
                  │
                  ▼
             Raw Observations
                  │
                  ▼
           Canonical Market Events
                  │
          ┌───────┴────────┐
          ▼                ▼
    In-Memory Live      Historical Append
       Market State          │
          │                  ▼
          ▼            Parquet/Object Store
    Canonical Candles         │
          │                   │
          ▼                   │
      Feature DAG ◄───────────┘
          │
          ▼
    FeatureSnapshot
          │
     ┌────┼──────────────┐
     │    │              │
     ▼    ▼              ▼
 Strategy/ Risk      PatternWindow
                      │       │
                      │       ├── hand-designed vector
                      │       ├── normalized sequence
                      │       └── learned embedding
                      │
                      ▼
                Similarity Search
                      │
                      ▼
             Historical Matches
                      │
                      ▼
                 Outcome Labels
                      │
                      ▼
              DD-09 ML / Research
```

---

# 350. Final Data Principle

The foundational DD-05 rule is:

> **Preserve market truth at sufficient granularity to reconstruct future ideas; represent every point in time with explicit timestamp, quality, and lineage; store both state and trajectory; keep future outcomes physically and logically separate from contemporaneous features; and treat historical similarity as a first-class, point-in-time-correct consumer of the data architecture rather than as a bolt-on ML feature.**

In practical terms:

```text
Observe broadly.
Store faithfully.
Normalize deterministically.
Version everything derived.
Never leak the future.
Preserve sequences, not only snapshots.
Search history efficiently.
Keep embeddings disposable.
Record non-trades.
Let years of observations compound into stock-specific market memory.
```

This is the foundational market-data, feature, and historical-pattern architecture for Edge Relative.
