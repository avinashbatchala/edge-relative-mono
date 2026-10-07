# Edge Relative — Incremental AI Agent Prompting Guide for the Python Research & Historical Search Layer

**Purpose:** Build the Edge Relative Python research layer in small, auditable units instead of one large implementation.

**Repository:** https://github.com/avinashbatchala/edge-relative-mono

**Design sources to read before implementation:**
- `DD01 - Product Spec and Features.md`
- `DD02 - Algorithm Research and Strategy Principles.md`
- `DD03 - Risk Management and Position Sizing.md`
- `DD04 - Dev Stack.md`
- `DD04A - DB Schema.md`
- `DD04B - Database Physica Schema.md`
- `DD05 - Market Data & Feature Architecture.md`

---

# How to Use This Guide

Run these prompts **in order**.

Do not ask the agent to implement the whole research platform at once.

Each prompt deliberately:
- builds on the previous stage;
- keeps the implementation scope narrow;
- requires inspection of existing code before changes;
- requires real tests rather than mock-only success;
- requires point-in-time correctness;
- requires reproducibility and lineage;
- requires explicit evidence before declaring success;
- prevents Python research code from modifying authoritative trading state;
- prevents research findings from silently changing the Java production strategy.

At the end of every stage, commit only after:
1. all required tests pass;
2. the agent reports exactly what changed;
3. the agent documents known gaps;
4. the implementation satisfies the stage exit criteria;
5. no unrelated architecture has been changed.

---

# Global Architecture Invariants

These rules apply to **every prompt below**.

```text
Market Data
    ↓
Feature Calculation
    ↓
Market / Sector Context
    ↓
Deterministic Setup Detection
    ↓
Research / ML Evaluation
    ↓
Risk
    ↓
Execution
```

The research layer may discover and validate hypotheses.

It must **not**:
- place orders;
- change risk decisions;
- write fills;
- mutate positions;
- mutate authoritative trade plans;
- modify production model deployments;
- silently promote research rules to production;
- use future information in historical analysis.

Python may read production observations and write research-owned data only.

The desired lifecycle is:

```text
Python Hypothesis
    ↓
Research
    ↓
Candidate Improvement
    ↓
Independent Validation
    ↓
Out-of-Sample
    ↓
Walk-Forward
    ↓
Formal Strategy Specification
    ↓
Java Canonical Implementation
    ↓
Production-Grade Java Backtest
    ↓
Shadow / Paper / Live Promotion
```

Never bypass this boundary.

---

# Global Agent Working Rules

Use these rules in every implementation step.

1. Read the relevant design docs before coding.
2. Inspect the existing repository before proposing new abstractions.
3. Reuse existing contracts, tables, IDs, enums, fixtures, and conventions wherever possible.
4. Do not duplicate an existing implementation.
5. Prefer small modules with explicit contracts over notebooks containing production logic.
6. Notebooks are consumers of tested Python packages, not the source of truth.
7. Use `uv` for Python dependency/package management.
8. Prefer:
   - Polars for analytical transforms;
   - NumPy for numerical operations;
   - Pandas only where materially useful;
   - SciPy/statsmodels for statistical analysis;
   - scikit-learn for classical ML;
   - matplotlib for visualization;
   - pytest + Hypothesis for testing;
   - Ruff for linting/formatting.
9. Do not introduce an ML framework until an actual stage requires it.
10. Do not add Kafka, Redis, Cassandra, TimescaleDB, pgvector, FAISS, or another infrastructure dependency merely because it may be useful later.
11. Respect the existing PostgreSQL + Parquet/object-storage boundary.
12. All research datasets must be versioned and reproducible.
13. All temporal joins must be point-in-time correct.
14. All outcome labels must remain logically separate from feature inputs.
15. Historical search must enforce an `as_of` cutoff in historical simulation.
16. Every experiment must record dataset version, code version, parameters, date range, universe, cost assumptions, and results.
17. Do not claim a discovered edge from in-sample performance alone.
18. Never optimize primarily for maximum historical P&L.
19. Prefer robust out-of-sample risk-adjusted expectancy.
20. A failed hypothesis is a valid research result and must be recordable.

---

# Definition of "Done" for Every Stage

Before reporting a stage complete, provide:

```text
1. Repository areas inspected
2. Design-document requirements used
3. Files added
4. Files modified
5. Database/API contracts used
6. Tests added
7. Tests executed
8. Exact commands executed
9. Test results
10. Sample real-data execution performed
11. Validation checks performed
12. Bugs found
13. Bugs fixed
14. Known limitations
15. Data-lineage implications
16. Point-in-time correctness assessment
17. Whether backward compatibility changed
18. Whether Java/Python parity is affected
19. Whether schema changes were required
20. Exit criteria: PASS / FAIL
```

If exit criteria are `FAIL`, stop. Do not continue to the next stage.

---

# Prompt 00 — Repository and Data Architecture Reconnaissance (Done)

```text
You are working on Edge Relative, an algorithmic trading platform for NSE equities.

Your first task is NOT to write the research engine.

Perform a detailed reconnaissance of the existing repository and determine the current state of the Python research layer, database contracts, historical-data storage, feature implementations, setup observations, trade/fill data, backtesting outputs, and research schema.

READ FIRST:
- DD01 - Product Spec and Features.md
- DD02 - Algorithm Research and Strategy Principles.md
- DD03 - Risk Management and Position Sizing.md
- DD04 - Dev Stack.md
- DD04A - DB Schema.md
- DD04B - Database Physica Schema.md
- DD05 - Market Data & Feature Architecture.md

Inspect at minimum:
- repository module structure;
- research/ Python project;
- pyproject.toml and uv.lock;
- backend feature implementations;
- backend setup/strategy implementations;
- historical market-data ingestion/storage;
- PostgreSQL migrations;
- research schema;
- dataset/version tables;
- feature definitions and feature-schema versions;
- setup_observation;
- model_prediction;
- risk_decision;
- trade_plan;
- trade;
- order_record/order_event;
- fill;
- pattern metadata;
- outcome definitions;
- experiment tables;
- backtest outputs;
- Parquet/object-storage conventions;
- contracts/fixtures.

Produce a concrete architecture report answering:

1. What already exists?
2. What is incomplete?
3. What is missing?
4. Where does authoritative historical data currently live?
5. What exact tables/files contain:
   - setup observations;
   - executed trades;
   - fills;
   - P&L;
   - historical candles;
   - feature snapshots;
   - strategy versions;
   - market/sector context?
6. Can a point-in-time research row currently be reconstructed?
7. Are feature versions preserved?
8. Are labels separated from features?
9. Are near-setups/rejected/skipped setups persisted?
10. Can we distinguish:
    - valid executed;
    - valid skipped;
    - risk rejected;
    - invalid;
    - near-trigger;
    - missed?
11. What Java feature logic needs Python parity fixtures?
12. What APIs or DB queries should Python use?
13. Which schemas are read-only for Python?
14. Which research tables may Python write?
15. Are there current schema/code mismatches?
16. What changes are prerequisite before research implementation?

Do not implement large new functionality yet.

You may fix only tiny defects that prevent repository inspection or test execution.

Create:
research/docs/research-layer-current-state.md

The document must contain:
- architecture diagram;
- source-of-truth matrix;
- existing-data matrix;
- missing-data matrix;
- Python read/write permissions assumptions;
- prerequisite work;
- proposed incremental implementation sequence;
- risks.

VALIDATION:
- run current Python tests;
- run relevant backend tests;
- confirm DB migrations compile/run in the project-supported test setup;
- inspect actual schema rather than relying only on docs.

EXIT CRITERIA:
PASS only when we have a concrete evidence-based map of how a point-in-time research dataset can be constructed from the existing system.

Do not implement Prompt 01 work.
```

---

# Prompt 01 — Bootstrap and Harden the Python Research Package (Done)

```text
Continue from Prompt 00.

Implement only the foundational Python research package structure.

Goal:
Create a clean, testable Python package that future research stages can build upon without putting logic into notebooks.

Use the existing repository conventions. Do not create parallel architecture if equivalent modules already exist.

Target conceptual structure, adapting to existing code:

research/
├── pyproject.toml
├── src/edge_relative_research/
│   ├── config/
│   ├── db/
│   ├── datasets/
│   ├── features/
│   ├── labels/
│   ├── patterns/
│   ├── similarity/
│   ├── experiments/
│   ├── validation/
│   └── cli/
├── notebooks/
├── experiments/
└── tests/

Implement:
- typed research configuration;
- environment configuration loading;
- logging;
- deterministic random seed helpers;
- UTC timestamp utilities;
- Asia/Kolkata exchange-time helpers;
- database connection abstraction for read-only production access and research-write access;
- package version/code-version helper;
- test configuration;
- Ruff;
- pytest;
- Hypothesis;
- CI-compatible commands.

Critical restrictions:
- Python must not gain write access to operational orders, fills, risk decisions, trade plans, positions, or deployment authority.
- No notebook may contain required reusable business logic.
- No ML dependency beyond the approved basic stack unless already present.

Add automated tests for:
- timezone conversion;
- session-local date conversion;
- configuration validation;
- read-only DB transaction behavior where test infrastructure permits;
- deterministic seed behavior.

Run:
- uv sync
- Ruff
- pytest

EXIT CRITERIA:
- package imports cleanly;
- tests pass;
- no authoritative production write path is introduced;
- research package is ready for data contracts.

Do not implement dataset construction yet.
```

---

# Prompt 02 — Define Canonical Research Data Contracts (Done)

```text
Continue from the completed research package foundation.

Do not build analysis yet.

Define explicit typed Python contracts for the data the research layer will consume and produce.

The contracts must be aligned with existing Java/domain/database semantics.

Define, where supported by the existing system:

ResearchAnchor
- observation key/id
- instrument_id
- symbol only as display metadata
- exchange timestamp
- timeframe
- strategy_version
- setup observation identity
- setup instance identity if available
- setup state
- direction

FeatureContext
- feature_schema_version
- feature values
- feature versions
- market context
- sector context
- stock context
- time-of-day context
- data-quality state

ExecutionContext
- trade identity
- entry reference
- actual entry
- exit
- quantity
- costs
- slippage
- fill timestamps
- realized P&L
- realized R where computable

OutcomeRecord
- future returns by horizon
- MFE
- MAE
- target-before-stop
- target hit
- stop hit
- time-to-target
- time-to-stop
- future path metadata

DatasetIdentity
- dataset/version key
- code version
- point-in-time cutoff
- feature schema version
- outcome schema version
- parent dataset versions
- checksum/manifest identity

PatternWindow
- pattern identity
- anchor identity
- feature schema version
- pattern schema version
- normalization version
- source observation range
- market/sector regime
- minutes since open
- sequence reference

PatternMatch
- query pattern
- matched pattern
- cohort
- method version
- score
- rank
- matched anchor timestamp
- matched instrument
- outcome reference

Rules:
- use stable IDs rather than symbol as identity;
- do not place future outcomes inside feature contracts;
- represent missing values explicitly;
- represent data-quality states explicitly;
- distinguish observed, derived, and labeled values;
- do not hide version metadata.

Add serialization tests and schema tests.

Add a document:
research/docs/research-data-contracts.md

Include examples constructed from test fixtures.

EXIT CRITERIA:
Contracts are explicit enough that future stages cannot accidentally mix future labels with feature inputs.
```

---

# Prompt 03 — Implement Research Data Access Layer (Done)

```text
Continue from canonical research contracts.

Implement the data-access layer required to read research inputs from the existing system.

Do NOT yet build full research datasets.

Implement repository/query adapters for the existing schema.

Required capabilities should include, where data exists:

Reference:
- instruments;
- temporal symbol/identifier mappings;
- sectors;
- temporal sector mappings;
- benchmark mappings;
- trading calendar;
- sessions;
- corporate actions;
- universe membership.

Control:
- feature definitions;
- feature versions;
- feature schema versions;
- strategy versions.

Market:
- market observations;
- revisions;
- data-quality state;
- historical dataset manifests.

Operational read-only:
- setup observations;
- trades;
- orders;
- fills;
- trade plans;
- model predictions if relevant;
- risk decisions where useful for classification;
- portfolio/risk snapshots if useful.

Research:
- dataset metadata;
- outcome definitions;
- pattern metadata;
- experiments.

Requirements:
- parameterized queries only;
- no raw string concatenation for dynamic SQL values;
- streaming/batched reading for large data;
- deterministic ordering;
- explicit timestamp boundaries;
- temporal joins must support as-of semantics;
- test SQL against Testcontainers or the project-equivalent integration DB.

Implement query tests covering:
- symbol changed historically;
- sector changed historically;
- historical benchmark membership;
- setup observations ordered correctly;
- multiple fills for one order;
- partial fill;
- skipped/rejected setup classification;
- no future temporal mapping is returned.

Add instrumentation for query duration and row count, but no distributed monitoring infrastructure.

EXIT CRITERIA:
Python can safely and reproducibly retrieve all currently available source records needed to construct a research observation.
```

---

# Prompt 04 — Historical Dataset Manifest and Reproducibility Layer (Done)

```text
Implement dataset versioning and reproducibility before building analytical datasets.

Use the existing research.dataset / dataset_version / dataset_version_input model where present.

Required behavior:

A dataset build must record:
- dataset identity;
- dataset type;
- immutable version;
- feature schema version where relevant;
- outcome schema version where relevant;
- point-in-time cutoff;
- universe;
- start/end timestamps;
- exact parent dataset versions;
- storage URI/path;
- partition manifest;
- row count;
- checksum;
- code version;
- build parameters;
- creation timestamp;
- status BUILDING / COMMITTED / FAILED / RETIRED or existing equivalents.

A COMMITTED dataset must never be silently overwritten.

Implement:
- dataset builder lifecycle;
- deterministic manifest creation;
- checksums;
- parent-lineage validation;
- commit semantics;
- failure cleanup;
- local/test Parquet storage abstraction matching the architecture;
- no cloud vendor coupling unless already implemented.

Tests:
- same input + same code + same params produces same manifest identity/checksum where determinism permits;
- changed parent dataset changes derived dataset lineage;
- committed dataset cannot be overwritten;
- failed build cannot appear committed;
- a dataset cannot reference a future parent relative to its cutoff;
- corrupted file checksum is detected.

EXIT CRITERIA:
No later experiment can run against an unidentified mutable folder of Parquet files.
```

---

# Prompt 05 — Point-in-Time Join and Leakage Guard Framework (Done)

```text
This is a critical correctness stage.

Implement a reusable point-in-time join framework and leakage guard before building research datasets.

Goal:
Make future-information leakage difficult to introduce accidentally.

Support point-in-time alignment for:
- instrument identity;
- sector mapping;
- benchmark membership;
- universe membership;
- trading session;
- market features;
- sector features;
- stock features;
- feature normalization baselines;
- setup state.

Explicitly prohibit:
- future candles;
- full-day volume in intraday rows;
- future-confirmed pivots;
- future constituents;
- future sector classifications;
- future corporate-action knowledge unless its effective/known-time semantics allow it;
- future normalization samples;
- outcome columns entering features.

Implement:
- `as_of_timestamp` required for temporal loaders;
- assertions that source timestamps <= anchor timestamp;
- feature timestamp validation;
- higher-timeframe closed/incomplete-bar semantics;
- train/test normalization fit boundaries;
- leakage scanning utilities that inspect dataset schemas and timestamps.

Create adversarial tests that intentionally inject:
- a future row;
- a future sector mapping;
- future full-day volume;
- a future pivot confirmation;
- an outcome column into feature columns;
- a future normalization statistic.

Every case must fail loudly.

Add:
research/docs/point-in-time-invariants.md

EXIT CRITERIA:
The test suite proves that representative future-leakage cases are detected automatically.
```

---

# Prompt 06 — Build the Setup Observation Research Dataset

```text
Now construct the first real research dataset.

The unit of analysis should be a setup/market observation, not only an executed trade.

Build a point-in-time-correct dataset containing all available categories:

- executed valid setups;
- valid but skipped setups;
- risk-rejected setups;
- model-rejected setups where applicable;
- lower-ranked valid opportunities;
- WATCH;
- FORMING;
- NEAR_TRIGGER;
- VALID;
- INVALIDATED;
- MISSED where represented;
- ordinary non-setup observations only if the source architecture already supports them without inventing data.

For each row include, where available:
- observation/setup identity;
- setup instance identity;
- instrument;
- strategy version;
- direction;
- setup status;
- anchor timestamp;
- session date;
- minutes since open;
- day of week;
- market regime/bias/phase;
- sector/regime;
- stock context;
- feature schema version;
- point-in-time feature values;
- whether trade occurred;
- rejection/skip reason;
- data quality.

Do NOT add future outcomes yet.

Requirements:
- avoid duplicate setup-instance rows caused by accidental repeated evaluation semantics;
- preserve append-only setup-state history;
- allow both state-transition analysis and anchor-level analysis;
- document exactly how duplicate/repeated observations are handled.

Validation:
- count rows by state;
- count rows by instrument;
- count rows by date;
- verify uniqueness keys;
- verify no executed-trade-only selection bias;
- inspect random samples against raw DB/source data;
- test cold-start/mid-session setup-state cases;
- test NONE/WATCH/FORMING/NEAR_TRIGGER/VALID transitions where represented.

Produce:
- Parquet dataset;
- dataset manifest;
- quality report.

EXIT CRITERIA:
We have a reproducible point-in-time dataset representing what the system knew at setup-observation time, including non-trades.
```

---

# Prompt 07 — Outcome Label Engine

```text
Build the future-outcome layer as a separate dataset.

Do not merge labels into the feature dataset physically or logically.

Implement versioned outcome definitions for configurable horizons such as:
- return_1m if data granularity supports it;
- return_5m;
- return_15m;
- return_30m;
- return_60m;
- return_close.

Also compute where valid:
- MFE;
- MAE;
- MFE_R;
- MAE_R;
- target_1R_hit;
- target_2R_hit;
- stop_hit;
- target_before_stop;
- time_to_target;
- time_to_stop;
- time_to_MFE;
- time_to_MAE.

For executed trades additionally support:
- actual entry;
- actual exit;
- realized gross P&L;
- transaction costs;
- estimated/actual slippage where available;
- net P&L;
- realized R;
- holding time;
- entry efficiency;
- exit efficiency where definition can be made explicit.

Critical details:
- define intrabar ambiguity policy;
- define whether high/low is sufficient for target-vs-stop ordering;
- if ordering is unknowable from bar data, label UNKNOWN rather than inventing an answer;
- use actual fills for executed-trade outcomes where appropriate;
- separate theoretical setup outcomes from actual execution outcomes.

Version every label definition.

Tests:
- long and short;
- target first;
- stop first;
- both target and stop inside same bar;
- missing future bars;
- market close truncation;
- partial fill;
- multi-fill average entry;
- transaction costs;
- short trade arithmetic;
- zero/invalid stop distance.

EXIT CRITERIA:
Feature rows and future outcomes can be linked by stable identity while remaining separate datasets.
```

---

# Prompt 08 — Java/Python Feature Parity Harness

```text
Before exploratory research, implement parity checks for features used by the strategy.

Use shared fixtures under the existing contracts/fixtures architecture.

At minimum cover, where implemented in Java:
- RRS raw;
- RRS fast/slow/persistence;
- RRS acceleration;
- Daily RVOL;
- interval RVOL;
- cumulative RVOL;
- RVE;
- ATR;
- VWAP distance;
- confirmed pivots;
- compression;
- market state;
- sector relative strength;
- setup qualification inputs.

Do not independently reinterpret feature formulas.

Read the Java implementation and DD-02.

For each feature:
- identify Java source;
- identify feature version;
- implement/reference Python equivalent;
- define numerical tolerance;
- create shared fixture;
- test edge cases.

Required adversarial examples:
- zero/near-zero ATR;
- missing sessions;
- opening high-volume slot;
- midday high relative volume;
- one-candle RRS spike;
- market down while stock flat;
- market flat while stock directional;
- corporate-action boundary where applicable;
- incomplete higher timeframe.

Generate a parity report listing:
- exact matches;
- tolerance matches;
- mismatches;
- unresolved semantic differences.

Do not proceed with research using a feature if parity is unresolved unless it is explicitly marked Python-only experimental and cannot affect canonical conclusions.

EXIT CRITERIA:
Canonical research features match Java semantics within documented tolerance.
```

---

# Prompt 09 — Data Quality and Exploratory Profiling

```text
Implement a deterministic dataset-quality and exploratory profiling pipeline.

Do not search for trading edges yet.

For every dataset version report:
- row count;
- date coverage;
- instrument coverage;
- session coverage;
- missingness by feature;
- missingness by instrument;
- data-quality state distribution;
- duplicate keys;
- timestamp gaps;
- suspicious future timestamps;
- feature min/max/quantiles;
- extreme values;
- infinities/NaNs;
- stale-value runs;
- label availability;
- setup-state distribution;
- executed vs non-executed ratio;
- long vs short ratio;
- market-regime distribution;
- sector distribution;
- time-of-day distribution.

Detect likely data defects, not just statistical outliers.

Provide a machine-readable quality result and a human-readable report.

Fail the dataset for research if configured critical checks fail.

Do not automatically delete or impute suspicious source rows.

EXIT CRITERIA:
We know whether the dataset is safe enough for hypothesis discovery and which biases/gaps remain.
```

---

# Prompt 10 — Experiment Registry and Reproducible Research Runner

```text
Implement the experiment execution framework before running feature research.

Use the existing research experiment tables where available.

Every experiment must record:
- experiment_id;
- hypothesis;
- strategy version;
- dataset version;
- outcome dataset version;
- universe;
- date range;
- direction;
- parameters;
- cost model;
- feature set;
- code version;
- random seed;
- train/validation/test boundaries;
- results;
- notes;
- status;
- artifact references.

Implement a CLI/API such as:

uv run edge-research experiment run <config>

or adapt to repository conventions.

Experiment configs should be immutable inputs to a run.

Persist:
- configuration;
- metrics;
- diagnostic tables;
- plots;
- logs;
- candidate findings.

Do not treat notebook execution as the experiment registry.

Tests:
- same config is reproducible;
- changed config produces a distinct run;
- interrupted run is FAILED;
- results cannot be attached to wrong dataset version;
- code version is captured.

EXIT CRITERIA:
Every future research conclusion can be traced to an exact dataset, config, code version, and outcome definition.
```

---

# Prompt 11 — Baseline Statistical Analysis

```text
Implement baseline statistical analysis before complex feature mining or ML.

Goal:
Understand unconditional and simple conditional behavior.

Analyze outcome distributions by:
- instrument;
- direction;
- strategy;
- setup state;
- market regime;
- sector;
- day of week;
- time-of-day bucket;
- year/quarter/month;
- volatility bucket;
- liquidity bucket.

Metrics should include where meaningful:
- count;
- win rate;
- mean R;
- median R;
- expectancy;
- average winner;
- average loser;
- profit factor;
- MFE;
- MAE;
- target-before-stop rate;
- confidence intervals.

Always report sample size.

Never rank a tiny sample as meaningful merely because its return is large.

Implement bootstrap confidence intervals where appropriate.

Separate:
- executed trade outcomes;
- theoretical setup outcomes.

Produce baseline reports and reusable analytical functions.

EXIT CRITERIA:
We have defensible baseline behavior against which later feature research can demonstrate incremental value.
```

---

# Prompt 12 — Single-Feature Predictive Research

```text
Implement controlled univariate feature research.

Test whether individual point-in-time features contain information about outcomes.

Initial candidates should come from DD-02/DD-05 and existing feature implementations, such as:
- RRS raw;
- RRS persistence;
- RRS slope/acceleration;
- Daily RRS;
- stock-vs-sector RRS;
- sector-vs-market RRS;
- interval RVOL;
- cumulative RVOL;
- RVE;
- ATR / normalized volatility;
- VWAP distance normalized by ATR;
- directional efficiency;
- liquidity;
- spread;
- gap;
- price structure;
- compression;
- time of day.

Do not generate hundreds of arbitrary indicators.

For each feature:
1. state the hypothesis;
2. describe why the feature could matter;
3. inspect distribution;
4. bin by quantiles and/or defensible ranges;
5. measure conditional outcome distributions;
6. measure monotonicity or nonlinear behavior;
7. estimate confidence intervals;
8. measure stability across years/regimes;
9. compare long vs short;
10. report sample size.

Correct for repeated exploratory testing where appropriate.

Do not call a feature useful solely because one threshold performs well.

Produce candidate findings, rejected hypotheses, and unresolved findings.

EXIT CRITERIA:
We know which individual features appear to provide stable signal worthy of interaction testing.
```

---

# Prompt 13 — Incremental Information and Redundancy Analysis

```text
Now determine whether apparently useful features add information beyond features the strategy already knows.

Goal:
Avoid adding complexity for redundant indicators.

For candidate features:
- correlation analysis;
- rank correlation;
- mutual-information style exploratory measures where justified;
- conditional analysis;
- simple interpretable regression/classification baselines;
- permutation importance where appropriate;
- ablation studies.

Questions to answer:
- Does RVE add information after RVOL?
- Does momentum add information after RRS?
- Does stock-vs-sector strength add information after stock-vs-market strength?
- Does time of day remain useful after market regime?
- Does VWAP extension alter expectancy after RRS and RVOL?
- Does ATR contain predictive information or merely scale outcomes?

Use time-based validation.

Do not use feature importance from one overfit model as proof.

Produce:
- redundancy matrix;
- incremental-value report;
- candidate features to retain;
- candidate features to reject.

EXIT CRITERIA:
Candidate strategy complexity is justified by incremental evidence rather than duplicated indicators.
```

---

# Prompt 14 — Feature Interaction Research

```text
Investigate conditional interactions among the strongest defensible features.

Do not brute-force millions of arbitrary threshold combinations.

Focus on hypotheses such as:
- RRS × RVOL;
- RRS persistence × RVE;
- RRS × market regime;
- stock strength × sector strength;
- RVOL × time of day;
- VWAP extension × relative strength;
- volatility × breakout state;
- setup state × market phase.

Support:
- 2D conditional tables;
- heatmaps/data matrices;
- simple interpretable models;
- decision-tree style exploratory segmentation with strict depth/min-sample controls;
- generalized linear models where appropriate.

Require:
- minimum sample per cell/leaf;
- confidence interval;
- stability across chronological partitions;
- long/short separation;
- parameter-neighborhood stability.

Flag suspicious islands such as:

RRS > 1.73 works,
RRS > 1.70 fails,
RRS > 1.76 fails.

Treat such results as likely overfit unless evidence supports a discontinuity.

EXIT CRITERIA:
We have a small set of interpretable interaction hypotheses rather than a combinatorial rule explosion.
```

---

# Prompt 15 — Stock-Specific, Sector, Strategy, and Global Hierarchical Evidence

```text
Implement hierarchical research summaries.

Do NOT train an independent production model for every stock.

For each candidate condition, compare:

Global evidence
    +
Strategy evidence
    +
Sector evidence
    +
Stock-specific evidence

Report for each level:
- sample count;
- target-before-stop;
- expectancy;
- median R;
- MFE;
- MAE;
- confidence interval;
- recent-period stability.

Implement shrinkage or hierarchical statistical methodology only if it can be justified and tested; otherwise begin with transparent evidence tables.

Critical rule:
stock-specific evidence must not dominate when the stock has insufficient historical samples.

Produce outputs such as:

Condition:
RRS persistent positive
RVOL elevated
market bullish
sector bullish
10:15–11:30

Global N=...
Sector N=...
RELIANCE N=...

with separate statistics.

Do not collapse them into one opaque score yet.

EXIT CRITERIA:
The system can identify possible stock-specific behavior while exposing sample-size uncertainty.
```

---

# Prompt 16 — Pattern Window Dataset

```text
Now implement historical sequence/pattern representation.

Do not start similarity search until pattern windows have deterministic lineage.

A current scalar snapshot is insufficient.

Create versioned PatternWindow datasets around stable anchor observations.

Pattern contents may include, where available:
- OHLC/returns;
- volume;
- RRS trajectory;
- RVOL trajectory;
- RVE trajectory;
- ATR/volatility;
- VWAP distance;
- market context sequence;
- sector context sequence;
- price-structure state;
- setup-state evolution.

Support configurable windows such as:
- previous 12 M5 bars;
- previous 24 M5 bars;
- corresponding higher-timeframe context.

Every pattern must identify:
- anchor observation;
- source observation range;
- instrument;
- timeframe;
- feature schema version;
- pattern schema version;
- normalization version;
- market regime;
- sector;
- session segment/minutes since open;
- source dataset version;
- data revision.

Pattern representation must contain only information known by the anchor timestamp.

Outcome must NOT be stored inside the pattern tensor.

Store high-dimensional sequence data in Parquet/object-storage-compatible representation.
Store compact searchable metadata in the research schema if existing architecture supports it.

Tests:
- no future bar included;
- correct ordering;
- missing bar behavior;
- session-boundary behavior;
- split/corporate-action boundary behavior where relevant;
- same source data yields same pattern.

EXIT CRITERIA:
We have deterministic, auditable historical patterns ready for similarity research.
```

---

# Prompt 17 — Similarity Search Stage 1: Metadata Filtering

```text
Implement only Stage 1 of historical similarity search.

Do not add ANN/vector infrastructure yet.

The query contract must require:
- query/anchor pattern;
- pattern schema version;
- cohort policy;
- metadata filters;
- k or candidate limit;
- similarity method version placeholder;
- as_of_limit.

The `as_of_limit` is mandatory for replay/backtest use.

Implement cohorts separately:
- SAME_STOCK;
- SAME_STOCK_SAME_REGIME;
- SAME_SECTOR;
- GLOBAL.

Do not prematurely combine cohort results.

Metadata filters may include:
- timeframe;
- pattern schema;
- feature schema compatibility;
- data quality;
- instrument;
- sector as of anchor;
- market regime;
- sector regime;
- minutes since open/session segment;
- direction where defensible;
- as_of cutoff.

Stage 1 result should reduce the searchable corpus efficiently and deterministically.

Tests:
- no candidate later than as_of_limit;
- historical sector mapping used;
- same-stock cohort contains only same instrument;
- same-sector cohort uses sector as-of candidate timestamp;
- incompatible pattern schemas rejected;
- bad-quality patterns excluded according to policy.

Benchmark candidate counts on real data.

EXIT CRITERIA:
Historical simulation cannot retrieve future patterns and cohort semantics are correct.
```

---

# Prompt 18 — Similarity Search Stage 2: Exact Baseline Distance

```text
Implement a simple exact similarity baseline before ANN or learned embeddings.

Candidate metrics:
- normalized weighted Euclidean distance;
- cosine similarity/cosine distance;
- correlation distance.

Choose one simple primary baseline based on pattern representation and document its score semantics.

Requirements:
- normalize only using statistics available according to the dataset/research split;
- version normalization;
- version distance method;
- configurable feature weights;
- deterministic ranking;
- stable tie-breaking;
- missing-value policy;
- score direction explicitly documented.

Return PatternMatch:
- query pattern;
- matched pattern;
- cohort;
- score;
- method version;
- rank;
- instrument;
- anchor timestamp;
- optional outcome reference.

Do not claim that the metric is predictive yet.

Tests:
- identical sequence should rank nearest;
- perturbed sequence should rank farther under expected conditions;
- feature scaling should not dominate accidentally;
- missing features handled deterministically;
- future patterns impossible due to Stage 1 cutoff;
- deterministic ordering of ties.

Benchmark exact search on representative corpus sizes.

EXIT CRITERIA:
We have a trustworthy baseline similarity implementation against which more complex methods can be evaluated.
```

---

# Prompt 19 — Similarity Search Stage 3: Sequence Re-Ranking

```text
Implement optional precise re-ranking for the top Stage-2 candidates.

Research candidate methods may include:
- weighted sequence distance;
- correlation-based sequence similarity;
- Dynamic Time Warping where justified.

Do not assume DTW or another complex metric is superior.

Architecture:
Millions
    ↓ Stage 1 metadata
Thousands
    ↓ Stage 2 baseline
Dozens
    ↓ Stage 3 precise re-ranking
Top historical analogues

Requirements:
- method version;
- reproducible settings;
- computational benchmark;
- deterministic result;
- no future leakage;
- same cohort identity preserved.

Evaluate whether Stage 3 materially changes neighbor quality using future outcomes only in evaluation, never in matching.

EXIT CRITERIA:
Any sequence re-ranker must demonstrate measurable validation value or remain experimental.
```

---

# Prompt 20 — Historical Analogue Outcome Summaries

```text
Build the layer that answers:

Have we seen states similar to this before, and what happened afterward?

Do not convert neighbor outcomes directly into trade authority.

For each similarity query and cohort report:
- number of candidates searched;
- number of neighbors returned;
- effective sample size;
- similarity distribution;
- target-before-stop rate;
- median R;
- mean R with robust warnings;
- MFE distribution;
- MAE distribution;
- time-to-target distribution;
- time-to-stop distribution;
- horizon returns;
- historical date distribution;
- regime distribution.

Keep cohorts separate:

Same stock: N=...
Same stock + regime: N=...
Same sector: N=...
Global: N=...

Report uncertainty/confidence intervals.

Reject or warn when:
- N too small;
- neighbors are concentrated in one narrow historical period;
- similarity is poor;
- data quality is weak.

Tests must prove future labels are used only after neighbor selection.

EXIT CRITERIA:
Similarity produces contextual historical evidence, not an oracle or hidden trade signal.
```

---

# Prompt 21 — Time-Based Train / Validation / Out-of-Sample Framework

```text
Implement chronological research splits.

Never randomly shuffle market observations for primary strategy validation.

Support:
- discovery/train;
- validation;
- untouched out-of-sample test;
- repeated walk-forward windows.

All transformers/normalizers/models must fit only on the applicable training period.

Implement configuration such as:

train:      2021-01-01 → 2023-12-31
validate:   2024-01-01 → 2024-12-31
test:       2025-01-01 → 2025-12-31

and rolling/expanding walk-forward alternatives.

Tests:
- no train row after train cutoff;
- validation cannot influence train transforms;
- test cannot influence tuning;
- normalization leakage caught;
- pattern search cutoff respects each evaluation timestamp.

EXIT CRITERIA:
Research conclusions can be evaluated on genuinely unseen future periods.
```

---

# Prompt 22 — Robustness and Sensitivity Framework

```text
Implement robustness analysis for candidate rules.

A candidate must not depend on one magical threshold.

For every numerical threshold candidate:
- test neighboring values;
- plot/record parameter surface;
- measure sample-size change;
- measure expectancy stability;
- measure drawdown stability;
- measure regime stability.

Support:
- grid sensitivity;
- random search where appropriate;
- bootstrap;
- Monte Carlo resampling of trade/setup outcomes;
- confidence intervals.

Report:
- stable plateau;
- unstable island;
- insufficient evidence.

Do not promote a candidate whose performance collapses with small parameter changes unless there is a defensible structural discontinuity.

EXIT CRITERIA:
Candidate rules have quantified parameter sensitivity rather than one optimized number.
```

---

# Prompt 23 — Transaction-Cost and Execution-Aware Research

```text
Integrate realistic transaction effects into research evaluation.

Do not redefine canonical Java execution behavior.

Use available:
- actual fill prices;
- spread;
- slippage;
- fees/taxes;
- partial fills;
- liquidity;
- time-of-day effects.

For theoretical setup evaluation, use explicitly versioned cost/slippage assumptions.

For executed trades, distinguish:
- signal/reference price;
- planned entry;
- actual weighted fill;
- actual exit fill;
- gross P&L;
- costs;
- net P&L.

Evaluate whether candidate edge survives realistic costs.

Segment execution degradation by:
- time of day;
- liquidity;
- spread;
- volatility;
- stock.

EXIT CRITERIA:
No research finding is considered viable solely on frictionless price returns.
```

---

# Prompt 24 — Candidate Strategy Improvement Generator

```text
Build a research-output layer that converts evidence into explicit candidate changes.

It must NOT modify production strategy code automatically.

Supported candidate types:
- ADD_QUALITY_FACTOR;
- REMOVE_REDUNDANT_FACTOR;
- MODIFY_THRESHOLD;
- ADD_REGIME_CONDITION;
- MODIFY_ENTRY_CONDITION;
- MODIFY_EXIT_CONDITION;
- ADD_STOCK_SPECIFIC_ADJUSTMENT;
- ADD_SECTOR_CONDITION;
- NO_CHANGE;
- REJECT_HYPOTHESIS.

Every candidate must contain:
- hypothesis;
- current rule;
- proposed rule;
- evidence;
- dataset versions;
- sample sizes;
- in-sample metrics;
- validation metrics;
- out-of-sample metrics where available;
- walk-forward results;
- sensitivity results;
- cost-aware results;
- long/short breakdown;
- regime breakdown;
- stock/sector/global evidence;
- known risks;
- confidence/uncertainty;
- recommended next validation stage.

Never output:
"production rule updated."

Output:
"candidate for formal strategy specification."

Persist candidates as research artifacts.

EXIT CRITERIA:
Research can propose auditable modifications without gaining production authority.
```

---

# Prompt 25 — Classical ML Baseline as Meta-Model

```text
Only after the statistical pipeline is trustworthy, implement simple ML baselines.

Do not build deep learning first.

Goal:
Evaluate whether ML can improve ranking/filtering of already-valid deterministic setups.

Possible targets:
- P(target before stop);
- expected R;
- expected MFE;
- expected MAE;
- expected holding time.

Start with interpretable baselines such as:
- logistic regression;
- regularized linear/logistic models;
- simple tree ensemble only if justified.

Inputs must be point-in-time features only.

Compare against:
- unconditional baseline;
- deterministic strategy score;
- simple RRS/RVOL baseline.

Evaluation:
- chronological split;
- walk-forward;
- calibration;
- Brier/log loss for probabilities;
- ROC/PR only as secondary diagnostics;
- expectancy by predicted-quality decile;
- stability by stock/sector/regime;
- feature ablation.

Do not allow ML to promote invalid setups.

Do not connect model output to live trading.

EXIT CRITERIA:
We know whether ML adds out-of-sample information beyond deterministic strategy context.
```

---

# Prompt 26 — Historical Similarity as a Model Feature

```text
Evaluate whether historical-similarity evidence adds incremental predictive information.

Candidate similarity-derived features may include:
- same-stock analogue target rate;
- same-stock median R;
- sector analogue target rate;
- global analogue target rate;
- similarity-weighted MFE;
- analogue sample size;
- nearest-neighbor distance quality;
- outcome dispersion.

Critical safeguards:
- all neighbors must predate the evaluated sample;
- neighbor selection must not use outcomes;
- similarity index/corpus cutoff must be versioned;
- poor/low-sample analogue sets must expose uncertainty.

Compare models:
A. base deterministic features
B. base + stock/sector hierarchy
C. base + similarity evidence
D. base + hierarchy + similarity

Use out-of-sample and walk-forward evaluation.

EXIT CRITERIA:
Similarity is retained as a predictive feature only if it demonstrates incremental, stable, out-of-sample value.
```

---

# Prompt 27 — Research Dashboard / Reports

```text
Build research-facing outputs for human inspection.

This is not a trading dashboard.

Provide views/reports for:
- dataset health;
- experiment history;
- feature distributions;
- outcome distributions;
- regime segmentation;
- stock-specific findings;
- sector findings;
- feature interactions;
- parameter sensitivity;
- walk-forward stability;
- candidate strategy changes;
- historical analogue examples.

Every chart/table must display:
- dataset version;
- date range;
- sample size;
- applicable cohort;
- whether results are in-sample, validation, or test.

Avoid charts that imply certainty from tiny samples.

Do not expose production-control actions.

EXIT CRITERIA:
A researcher can inspect evidence and lineage without querying raw tables manually.
```

---

# Prompt 28 — End-to-End Research Pipeline Validation

```text
Perform a complete end-to-end validation of the Python research layer.

Use actual persisted historical data.

Run the full path:

Historical Data
    ↓
Point-in-Time Source Loading
    ↓
Feature/Setup Observation Dataset
    ↓
Outcome Dataset
    ↓
Quality Validation
    ↓
Experiment
    ↓
Baseline Analysis
    ↓
Feature Analysis
    ↓
Pattern Window
    ↓
Historical Similarity
    ↓
Chronological Validation
    ↓
Candidate Research Finding

Choose several representative stocks and periods.

Include:
- long example;
- short example;
- valid executed setup;
- valid skipped setup;
- rejected setup;
- near-trigger setup;
- losing trade;
- winning trade;
- low-liquidity or bad-data example where available.

For selected rows manually trace lineage back to:
- source market data;
- feature version;
- setup observation;
- strategy version;
- trade/fills where applicable;
- outcome calculations.

Recalculate key arithmetic independently.

Test:
- R/P&L math;
- MFE/MAE;
- target-before-stop;
- time horizons;
- time zone;
- session boundaries;
- costs;
- same-stock historical search cutoff;
- sector mapping at historical timestamp.

Intentionally inject future data and prove leakage checks fail.

Produce:
research/docs/end-to-end-validation-report.md

Do not fix findings silently.
Document every defect discovered, then fix it with regression tests.

EXIT CRITERIA:
The entire pipeline is proven against real historical records with reproducible lineage and no known leakage defect.
```

---

# Prompt 29 — Performance and Scale Validation

```text
Benchmark the research/search layer on representative historical scale.

Measure:
- source query latency;
- Parquet scan time;
- dataset build throughput;
- memory usage;
- pattern-window generation speed;
- Stage-1 filter latency;
- exact similarity latency;
- Stage-3 rerank latency;
- experiment runtime.

Test increasing corpora.

Do not optimize prematurely by adding infrastructure.

First optimize:
- column pruning;
- predicate pushdown;
- Polars lazy execution;
- partition selection;
- vectorized NumPy;
- batch size;
- precomputed metadata;
- compact pattern representation.

Only document a future ANN/vector requirement if exact/filtered search is demonstrably inadequate.

Do not add infrastructure solely from theoretical scale concerns.

EXIT CRITERIA:
The current architecture has measured performance limits and a documented evidence-based next scaling step.
```

---

# Prompt 30 — Final Architecture and Promotion Readiness Audit

```text
Perform a final architecture audit.

Verify that the Python research layer still respects all system boundaries.

Confirm:
- Python does not write authoritative trading state;
- feature/outcome separation remains intact;
- point-in-time joins are enforced;
- dataset versions are immutable;
- experiment lineage is complete;
- Java/Python feature parity is covered;
- historical similarity enforces as_of cutoff;
- stock-specific conclusions expose sample size;
- no candidate rule auto-modifies Java production strategy;
- ML cannot manufacture valid production setups;
- failed experiments remain recorded;
- notebooks contain no irreplaceable logic.

Produce:
research/docs/research-layer-architecture.md
research/docs/research-layer-operating-guide.md
research/docs/research-layer-known-limitations.md

Also create a concise checklist for taking one candidate result through:

Research Candidate
    ↓
Formal Strategy Spec
    ↓
Java Implementation
    ↓
Java/Python Fixture Parity
    ↓
Production Backtest
    ↓
Out-of-Sample
    ↓
Walk-Forward
    ↓
Shadow
    ↓
Paper
    ↓
Assisted Live

Do not promote anything to live.

EXIT CRITERIA:
The research layer is reproducible, auditable, point-in-time correct, tested, and incapable of silently changing trading authority.
```

---

# Optional Prompt A — Deep Bug Hunt for a Completed Stage

Use this after any stage if the implementation feels suspicious.

```text
Do not add new features.

Perform a hostile review of the implementation completed in the previous stage.

Assume there are subtle bugs.

Inspect:
- timestamp semantics;
- UTC vs Asia/Kolkata;
- off-by-one candle windows;
- inclusive/exclusive range boundaries;
- future leakage;
- accidental future normalization;
- temporal joins;
- duplicate observations;
- missing observations;
- symbol changes;
- sector-history correctness;
- corporate-action boundaries;
- long/short arithmetic;
- target/stop ordering;
- partial fills;
- transaction costs;
- NaN/infinity behavior;
- zero division;
- duplicate IDs;
- unstable ordering;
- non-determinism;
- mutable committed datasets;
- bad caching;
- train/test contamination.

Create adversarial tests for every plausible failure.

Do not report success merely because existing tests pass.

Provide:
- bugs found;
- severity;
- root cause;
- fix;
- regression test;
- residual risk.

Exit only when the stage passes the hostile review.
```

---

# Optional Prompt B — Validate a Research Finding Before Strategy Change

```text
A previous experiment has produced a candidate finding.

Do not modify production strategy code.

Attempt to falsify the finding.

Perform:
- alternate date ranges;
- alternate chronological splits;
- recent holdout;
- long/short separation;
- market-regime separation;
- sector separation;
- stock-specific vs global evidence;
- transaction-cost sensitivity;
- threshold-neighborhood analysis;
- bootstrap confidence intervals;
- Monte Carlo outcome ordering;
- feature ablation;
- simpler-baseline comparison;
- look-ahead leakage audit;
- survivorship audit;
- sample-size audit;
- multiple-testing concern assessment.

Try to determine whether the finding is:
- ROBUST_CANDIDATE;
- WEAK_EVIDENCE;
- REGIME_SPECIFIC;
- STOCK_SPECIFIC_LOW_SAMPLE;
- REDUNDANT;
- COST_SENSITIVE;
- OVERFIT;
- DATA_QUALITY_ARTIFACT;
- REJECTED.

Record the result as a research artifact.

Do not promote it.
```

---

# Optional Prompt C — Convert a Validated Finding into a Formal Strategy Specification

```text
A research candidate has survived discovery, validation, out-of-sample testing, walk-forward analysis, sensitivity testing, and realistic transaction costs.

Do not directly edit Java strategy logic yet.

Create a formal strategy-change specification containing:

- strategy name;
- current strategy version;
- proposed next version;
- motivation;
- exact deterministic rule;
- exact feature versions;
- exact parameter values/ranges;
- direction applicability;
- market-regime applicability;
- sector applicability;
- stock applicability;
- setup-state applicability;
- entry impact;
- invalidation impact;
- exit impact;
- risk-engine impact, if any;
- backward-compatibility implications;
- shared fixture changes required;
- historical evidence;
- out-of-sample evidence;
- walk-forward evidence;
- cost-aware evidence;
- parameter sensitivity;
- known failure modes;
- rollback criteria.

The specification must be detailed enough that a Java engineer can implement it without interpreting statistical notebooks.

No implementation in this step.
```

---

# Recommended Execution Order

```text
00  Reconnaissance
01  Python package foundation
02  Data contracts
03  Data access
04  Dataset lineage
05  Point-in-time/leakage guards
06  Setup observation dataset
07  Outcome labels
08  Java/Python feature parity
09  Data quality
10  Experiment registry

11  Baselines
12  Single-feature research
13  Incremental information
14  Feature interactions
15  Hierarchical stock/sector/global evidence

16  Pattern windows
17  Similarity Stage 1
18  Similarity Stage 2
19  Similarity Stage 3
20  Historical analogue outcomes

21  Chronological validation
22  Robustness/sensitivity
23  Costs/execution
24  Candidate improvements

25  ML baseline
26  Similarity as ML input
27  Research reports
28  End-to-end validation
29  Performance validation
30  Final architecture audit
```

---

# What Not to Do

Do not let an AI agent jump directly to:

```text
Load trades
→ generate 150 TA indicators
→ train XGBoost
→ report 84% accuracy
→ modify strategy
```

That path violates the architecture and is likely to produce selection bias, leakage, overfitting, and untraceable strategy changes.

The intended architecture is:

```text
Reliable historical truth
        ↓
Point-in-time reconstruction
        ↓
Versioned feature/setup dataset
        ↓
Separate outcome labels
        ↓
Reproducible experiments
        ↓
Statistical evidence
        ↓
Historical analogue evidence
        ↓
Out-of-sample validation
        ↓
Robustness testing
        ↓
Candidate strategy improvement
        ↓
Formal specification
        ↓
Java implementation
```

---

# Final Principle

The research layer should answer:

> Which observable, point-in-time market, sector, stock, setup, execution, and historical-pattern features contain stable incremental information about future trade outcomes, under what conditions, and does that information survive unseen data, realistic costs, parameter perturbation, and chronological regime changes?

It should **not** answer:

> What combination of historical variables gives the largest backtested P&L?

That distinction should remain visible throughout the implementation.
