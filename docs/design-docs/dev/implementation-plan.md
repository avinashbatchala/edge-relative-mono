
```text
Market truth
    ↓
Replay
    ↓
Features
    ↓
Strategy
    ↓
Research/Backtest
    ↓
Risk
    ↓
Simulation
    ↓
Live market data
    ↓
Broker execution
    ↓
UI
    ↓
ML
```

DD-01 already says MVP-0 exists to answer one question: **do we actually have a statistical edge?** It puts instrument master, historical data, features, backtesting, experiment tracking, feature snapshots, and outcome labels ahead of the workstation and live execution.

## The implementation order I recommend

| Phase | Build                           | Why now                                          |
| ----- | ------------------------------- | ------------------------------------------------ |
| 0     | Engineering invariants          | Prevent architecture erosion immediately         |
| 1     | Reference data                  | Everything depends on canonical instruments/time |
| 2     | Canonical market model          | Foundation of live + historical + replay         |
| 3     | Historical ingestion/storage    | Gives us actual material to develop against      |
| 4     | Candle + replay engine          | Creates deterministic time                       |
| 5     | Feature framework               | Infrastructure before individual indicators      |
| 6     | RRS/RVOL/RVE + context          | First actual quantitative capabilities           |
| 7     | Observation/research data model | Start accumulating market memory correctly       |
| 8     | Strategy engine                 | Implement DD-02 against stable measurements      |
| 9     | Backtester                      | Validate the first strategy                      |
| 10    | Experiment/analytics layer      | Efficient hypothesis testing                     |
| 11    | Risk engine                     | Only once valid trades exist                     |
| 12    | Paper execution                 | End-to-end trading lifecycle                     |
| 13    | Live broker/data integration    | External complexity comes late                   |
| 14    | Operator UI                     | Build against stable backend contracts           |
| 15    | Historical similarity + ML      | Once trustworthy observations exist              |
| 16    | Assisted live → autopilot       | Last                                             |

---

# Phase 0 — Lock down engineering invariants

Do this before meaningful domain implementation.

You already scaffolded the project, so I'd spend perhaps **1–2 focused days**, not weeks.

Set up:

```text
Maven module boundaries
ArchUnit dependency rules
Flyway baseline
jOOQ generation
Testcontainers PostgreSQL
Clock abstraction
ID/value-object conventions
UTC/Asia-Kolkata time handling
structured logging
CI
```

Especially enforce dependencies such as:

```text
strategy      X→ broker
features      X→ execution
risk          X→ web
domain        X→ Spring
```

DD-04 explicitly wants the trading domain framework-light and the hot path to remain direct in-process Java calls.

### First architectural tests

I'd write these before features:

```java
strategyCannotDependOnBroker();
featuresCannotDependOnExecution();
domainCannotDependOnSpring();
riskCannotDependOnBrokerImplementation();
```

They'll save substantial cleanup later.

---

# Phase 1 — Instrument master + trading calendar

This should be the first actual domain work.

Implement:

```text
Instrument
InstrumentId
Exchange
Segment
Symbol
Sector
Industry
TickSize
LotSize
TradingStatus

Index
SectorIndex

TradingSession
TradingDay
ExchangeCalendar
```

Also establish mappings:

```text
Stock
 → sector benchmark
 → broad-market benchmark
```

For example conceptually:

```text
HDFCBANK
    ↓
NIFTY BANK
    ↓
NIFTY 50
```

Don't start RRS until this relationship is explicit.

The product specification already requires canonical internal instrument IDs instead of broker-specific tokens.

---

# Phase 2 — Canonical market-data contracts

Next, define the domain models that everything else consumes.

Start small:

```text
MarketEvent

TradeTick
Quote

Candle

Timeframe
MarketDataQuality
```

With timestamps:

```text
exchangeTimestamp
brokerTimestamp
receivedTimestamp
processedTimestamp
```

DD-01 requires these specifically because they support latency measurement, replay, stale-data detection, and execution analysis.

Do **not** start by implementing Zerodha/Groww classes.

Wrong:

```text
KiteTick
  ↓
FeatureEngine
```

Correct:

```text
KiteTick
  ↓
ZerodhaNormalizer
  ↓
CanonicalMarketEvent
  ↓
everything else
```

---

# Phase 3 — Historical-data pipeline

Now get real NSE data flowing through the system.

Build one simple ingestion pipeline:

```text
Source historical data
        ↓
Parser
        ↓
Validation
        ↓
Canonical representation
        ↓
Parquet / historical storage
```

Initially use a deliberately tiny universe:

```text
NIFTY 50
one sector index
3–5 stocks
```

Something like:

```text
NIFTY
NIFTY BANK
HDFCBANK
ICICIBANK
SBIN
```

Do **not** load thousands of instruments first.

Prove correctness on five.

Once the pipeline works, scale ingestion independently.

---

# Phase 4 — Candle construction + deterministic replay

This is the most important early infrastructure milestone.

Build:

```text
M1 canonical candles
      ↓
M5
M15
M30
H1
D1
```

and the replay engine:

```text
Historical events
      ↓
ReplayClock
      ↓
same MarketEvent interface as live
      ↓
application
```

You should reach this invariant very early:

> The trading engine cannot tell whether its market events came from historical replay or live data.

Conceptually:

```java
interface MarketEventSource {
    void subscribe(MarketEventConsumer consumer);
}
```

Then:

```text
HistoricalReplaySource
LiveBrokerSource
```

both implement the same boundary.

DD-04 explicitly requires deterministic replay: identical events, feature versions, strategy versions and parameters must produce identical decisions.

---

# Phase 5 — Feature engine framework

Before implementing RRS, build the machinery around features.

I would establish:

```text
FeatureDefinition
FeatureVersion

FeatureValue
FeatureSnapshot

FeatureCalculator
FeatureState
FeatureContext
```

Features need to support both:

```text
incremental live update
```

and:

```text
historical replay
```

without two implementations.

For example:

```java
interface FeatureCalculator<S, O> {
    O update(S state, MarketObservation observation);
}
```

The exact interface is something we can refine later.

The important point is that:

```text
RRS
RVOL
ATR
EMA
```

are plugins into a deterministic framework rather than miscellaneous utility functions.

---

# Phase 6 — Implement quantitative primitives

Now implement them in dependency order.

### First

```text
returns
ATR
EMA
VWAP
volume baselines
```

### Then

```text
RVOL
RVE
```

### Then

```text
market movement
sector movement
RRS
sector RRS
stock-vs-sector RRS
```

### Then

```text
RRS persistence
RRS trend
RRS acceleration
```

Don't implement the whole strategy yet.

First prove:

```text
Historical input
        ↓
Feature Engine
        ↓
Known deterministic output
```

Create canonical fixture datasets shared with Python, as DD-04 recommends.

---

# Phase 7 — Build market memory *before* the strategy

This is one place I'd change the obvious order.

Don't wait for ML.

Implement the DD-05 observation model now:

```text
MarketObservation
FeatureSnapshot
SetupObservation
PatternWindow
OutcomeLabel
```

At minimum:

```text
instrument
timestamp
timeframe
feature_version
market context
sector context
stock features
time-of-day
```

And start producing:

```text
6-bar windows
12-bar windows
24-bar windows
```

You don't need embeddings yet.

But you **do** want the historical sequence corpus being generated correctly from day one.

DD-01 explicitly treats data as a core asset and requires recording more than executed trades to avoid selection bias.

---

# Phase 8 — Strategy engine

Only now implement DD-02.

Start with exactly **one strategy**:

```text
ER_RS_CONTINUATION_V1
```

Build its state machine:

```text
NONE
 ↓
WATCH
 ↓
FORMING
 ↓
NEAR_TRIGGER
 ↓
VALID
```

with structured result:

```text
strategyVersion
instrument
direction

setupState
entryReference
invalidation
targetReference

reasonCodes

featureSnapshotId
marketContext
sectorContext
```

Don't implement ten setup families.

One complete strategy is much more valuable.

---

# Phase 9 — Build the backtester around production logic

Now the architecture should become:

```text
Historical Data
      ↓
Replay
      ↓
Production Feature Engine
      ↓
Production Strategy Engine
      ↓
Simulation
      ↓
Outcome
```

Not:

```text
Python backtester
with separately reimplemented strategy
```

Python can still do exploratory research, but the canonical validation path should reuse Java production logic, consistent with DD-04.

### First milestone

Before worrying about fancy statistics, you should be able to execute:

```bash
./mvnw test
```

and something roughly equivalent to:

```text
backtest
strategy = ER_RS_CONTINUATION_V1
symbols = HDFCBANK, ICICIBANK, SBIN
from = 2024-01-01
to   = 2025-12-31
```

and get deterministic results.

---

# Phase 10 — Research and experiment framework

Once you have a functioning strategy/backtest loop, make iteration cheap.

Implement:

```text
Experiment
DatasetVersion
StrategyVersion
ParameterSet
CostModelVersion
Result
```

Then support experiments such as:

```text
RRS persistence:
3 bars
vs
4 bars
vs
6 bars
```

or:

```text
entry blackout:
15 min
30 min
45 min
```

DD-01 explicitly requires parameter robustness, chronological validation, walk-forward testing and experiment lineage.

Don't add Bayesian optimization yet.

Simple parameter sweeps are enough initially.

---

# Phase 11 — Outcome-label generation

This can partly happen alongside Phase 10.

For every historical observation, calculate:

```text
return_5m
return_15m
return_30m
return_60m

MFE
MAE

1R before stop
2R before stop

time_to_1R
time_to_stop
```

This serves two purposes immediately:

### Strategy research

You can ask:

> Does RRS actually predict future performance?

### Future ML

You're already building labeled observations.

Do this **long before training a model**.

---

# Phase 12 — Risk engine

Now implement DD-03.

At this point you finally have something meaningful for it to consume:

```text
ValidSetup
        ↓
RiskEngine
```

Start with:

```text
per-trade risk
structural stop sizing
max symbol exposure
max portfolio open risk
max sector exposure
daily loss
```

Then:

```text
correlation
progressive drawdown states
stress risk
```

Don't start with every DD-03 feature.

Build the simple deterministic core first.

And property-test invariants such as:

```text
permitted risk <= hard maximum
```

DD-04 specifically calls out financial-risk rules as strong candidates for property-based tests.

---

# Phase 13 — Paper execution

At this point you have:

```text
Data
Features
Strategy
Risk
```

Now add:

```text
TradePlan
Order
Fill
Position
```

with a simulation adapter.

Initially:

```text
limit orders
market orders
spread
slippage
fees
partial fills
```

Then add failures:

```text
timeout
rejection
partial fill
delayed fill
```

The execution architecture should already look like live:

```text
ExecutionEngine
        ↓
ExecutionAdapter
       / \
      /   \
 Paper    Broker
```

---

# Phase 14 — Live market data

Only now integrate the first real broker.

I would choose **one broker**.

Not:

```text
Zerodha + Groww simultaneously
```

Build:

```text
authentication
instrument mapping
WebSocket market data
reconnection
stale-feed detection
rate limits
health
```

Then feed it into exactly the same:

```text
CanonicalMarketEvent
```

pipeline.

First milestone:

> A live market day produces the same feature calculations and setup observations that a replay of that day produces later.

That is an extremely valuable parity test.

---

# Phase 15 — Backend API + minimal Vue workstation

Only now would I invest heavily in frontend.

Before this point, CLI/test output is sufficient.

Build the UI in vertical slices:

```text
1. System health
2. Watchlist
3. Live features
4. Setup state
5. Recommendation
6. Positions
7. Paper trades
8. Journal
```

Don't build beautiful charts while RRS is still changing every few days.

---

# Phase 16 — Live execution

Then implement:

```text
BrokerExecutionAdapter
OMS
order state
partial fills
idempotency
reconciliation
positions
protective orders
kill switch
```

Start with:

```text
Assisted Live
```

not autopilot.

Human clicks:

```text
APPROVE
```

before entry.

---

# Phase 17 — Historical similarity

Now DD-05's long-term architecture starts paying off.

First version doesn't need neural networks.

Start with:

```text
current normalized feature/sequence vector
        ↓
same-stock historical candidates
        ↓
distance calculation
        ↓
top K
        ↓
outcome statistics
```

Then add:

```text
sector
cross-stock
regime
time-of-day
```

Only later introduce:

```text
embeddings
ANN
learned sequence representations
```

The fact that we collected PatternWindows from Phase 7 means we don't have to redesign the data layer at this point.

---

# Phase 18 — ML

Only after the deterministic baseline is quantitatively understood.

Sequence:

```text
Observer
   ↓
Offline evaluation
   ↓
Shadow predictions
   ↓
Ranking
   ↓
Filtering
   ↓
possibly risk modifier much later
```

DD-01 explicitly requires comparing ML against the strategy without ML rather than judging a model in isolation.

---

# The first vertical slice I would build

Despite the overall order above, I would avoid building each subsystem completely before moving forward.

Build this **thin vertical slice first**:

```text
HDFCBANK
NIFTY BANK
NIFTY 50

Historical M1
     ↓
M5 candle builder
     ↓
ATR
     ↓
RVOL
     ↓
RRS
     ↓
FeatureSnapshot
     ↓
ER_RS_CONTINUATION_V1
     ↓
SetupObservation
     ↓
simple backtest
     ↓
Outcome labels
```

Once that works end-to-end:

```text
1 stock
→ 5 stocks
→ 20 stocks
→ broad learning universe
```

This is considerably safer than spending two months building an elaborate data platform before proving the abstractions work.

---

# What I would work on **tomorrow**

Given that the repo is scaffolded, my concrete order for the first implementation tickets would be:

1. `TradingClock`, `InstrumentId`, `Money/Price`, `Timeframe` and core primitives.
2. `Instrument` + sector/index relationships.
3. NSE `TradingCalendar`.
4. Canonical `Candle`.
5. Historical M1 reader.
6. Deterministic M1 → M5 aggregator.
7. Replay clock/event source.
8. ATR calculator.
9. RVOL calculator.
10. RRS calculator.
11. `FeatureSnapshot`.
12. First deterministic replay test.

At the end of that sequence, we should be able to feed historical HDFCBANK + NIFTY BANK + NIFTY through the application and inspect reproducible RRS/RVOL values.

**That is the first useful engineering milestone.**
