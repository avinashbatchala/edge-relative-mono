# Edge Relative — Product & Feature Design Specification

**Document:** DD-01  
**Version:** 1.1  
**Status:** Foundational Product Design  
**Product:** Edge Relative  
**Market:** Indian equities and equity derivatives  
**Initial Exchange Focus:** NSE  
**Initial User Model:** Single user  
**Future User Model:** Multi-tenant platform  
**Primary Trading Modes:** Research, Backtest, Observe, Paper, Assisted Live, Guarded Autopilot  
**Last Major Revision:** ML Meta-Model Architecture

---

# 1. Executive Summary

Edge Relative is an algorithmic trading platform for the Indian stock market.

The initial product will be operated by a single user but will be architected so that it can later evolve into a multi-tenant platform.

The system will maintain a focused watchlist of no more than 20 stocks.

For those stocks it will continuously calculate market features including:

- Real Relative Strength;
    
- Real Relative Volume;
    
- RVE;
    
- trend;
    
- volatility;
    
- liquidity;
    
- market context;
    
- sector context;
    
- options context;
    
- position/risk information.
    

The system will continuously determine:

- whether a legitimate trading setup exists;
    
- how attractive that setup is;
    
- whether risk permits the trade;
    
- which instrument should be used;
    
- how much capital should be allocated;
    
- when to enter;
    
- when to exit;
    
- whether an existing trade should continue to be held;
    
- what opportunities are better than others.
    

The product will progress from:

**Research → Backtesting → Paper Trading → Assisted Live Trading → Guarded Autopilot → Advanced Quantitative Intelligence**

Machine learning will initially operate as a **meta-model**.

The deterministic strategy determines whether a valid setup exists.

ML determines:

> How favorable is this already-valid setup for this stock, under these exact circumstances?

ML will initially rank and filter deterministic opportunities rather than independently manufacture trades.

---

# 2. Product Mission

Build a disciplined quantitative trading system capable of identifying, evaluating, executing and managing statistically defensible trading opportunities while preserving capital and maintaining complete operational control.

The product should optimize for:

**Reliable risk-adjusted expectancy after realistic costs.**

Raw return alone is not the primary objective.

---

# 3. Product Philosophy

Edge Relative should behave more like a small systematic trading desk than a technical-analysis dashboard.

The complete lifecycle is:

```text
Market Data
    ↓
Feature Calculation
    ↓
Market / Sector Context
    ↓
Deterministic Setup Detection
    ↓
ML Opportunity Evaluation
    ↓
Portfolio Ranking
    ↓
Risk Engine
    ↓
Trade Plan
    ↓
Execution
    ↓
Position Management
    ↓
Exit
    ↓
Reconciliation
    ↓
Outcome Analysis
    ↓
Research Dataset
    ↓
Improved Strategy / Models
```

Every layer has a specific responsibility.

---

# 4. Core System Invariant

The architecture must maintain a strict separation between:

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

The responsibilities are:

**Measurement**

What is happening?

**Strategy**

Is this a structurally valid trade?

**ML**

How attractive is this valid trade?

**Risk**

Are we allowed to trade it, and how much?

**Execution**

How should the position actually be established?

These layers must not silently absorb each other's responsibilities.

---

# 5. Conservative Trading Principle

The system should prefer missing a trade over taking a structurally poor trade.

A legitimate system decision is frequently:

**NO TRADE**

Trading frequency should never itself be a target.

A high-confidence opportunity should still be rejected when:

- liquidity is insufficient;
    
- spread is excessive;
    
- expected reward is insufficient;
    
- available risk is insufficient;
    
- portfolio correlation is excessive;
    
- market conditions are prohibited;
    
- infrastructure is degraded;
    
- the broker cannot reliably execute;
    
- required market data is stale;
    
- the system cannot establish an appropriate stop.
    

---

# 6. Risk-First Principle

Capital preservation sits above opportunity generation.

The authority hierarchy is:

```text
Compliance
    ↓
Infrastructure Safety
    ↓
Risk Limits
    ↓
Strategy Validity
    ↓
ML Preference
    ↓
Execution Optimization
```

A lower layer can never override a higher-level prohibition.

---

# 7. Determinism Before Machine Learning

The first trading strategies should be fully deterministic.

Given identical:

- data;
    
- strategy version;
    
- feature definitions;
    
- parameters;
    

the strategy must always generate the same result.

This makes:

- backtesting;
    
- debugging;
    
- auditing;
    
- paper/live comparison;
    
- ML evaluation;
    

significantly more reliable.

---

# 8. ML Meta-Model Principle

Machine learning initially operates after deterministic strategy qualification.

The deterministic strategy asks:

> Is this a valid setup?

ML asks:

> Given that this is a valid setup, how favorable is it?

Example:

```text
Strategy:
VALID RS BREAKOUT

Base R:R:
2.7

ML:
P(target before stop) = 73%

Expected value:
+0.68R

Stock-specific adjustment:
+9%

Decision:
HIGH-QUALITY VALID SETUP
```

---

# 9. ML Cannot Manufacture Production Trades Initially

A fundamental invariant is:

```text
Strategy = INVALID
ML confidence = 97%

Final decision = NO TRADE
```

ML may analyze invalid setups for research.

It cannot initially convert them into live trades.

This provides asymmetric ML authority:

**ML can become more conservative than the strategy but not more permissive.**

---

# 10. Single User First

The initial product serves one operator.

Initial assumptions:

- one owner;
    
- one primary trading account;
    
- potentially multiple connected broker accounts;
    
- one global portfolio;
    
- one watchlist;
    
- several strategies;
    
- one risk policy.
    

Development should avoid unnecessary SaaS complexity during this phase.

---

# 11. Multi-Tenant Ready Architecture

Although the first version is single-user, important records should support:

```text
tenant_id
user_id
broker_account_id
```

Future multi-tenancy should not require rewriting the trading domain.

Multi-tenant requirements include:

- strict credential isolation;
    
- independent risk policies;
    
- independent broker accounts;
    
- independent orders;
    
- independent positions;
    
- independent model personalization;
    
- independent audit logs.
    

---

# 12. Trading Modes

The system will support distinct operating modes.

## Research

Historical exploration only.

## Backtest

Historical simulated strategy operation.

## Observe

Live data and recommendations without order simulation.

## Paper

Live recommendations with realistic simulated execution.

## Assisted Live

System proposes trades; human approves entry.

## Guarded Autopilot

System automatically trades within strict limits.

## Full Autopilot

Long-term mode only after substantial production evidence.

---

# 13. Strategy Promotion Pipeline

No strategy should move directly from research to unrestricted live trading.

Required progression:

```text
Research
↓
Historical Backtest
↓
Out-of-Sample Validation
↓
Walk-Forward Testing
↓
Shadow Live
↓
Paper Trading
↓
Assisted Live
↓
Limited Autopilot
↓
Production
```

Each stage has defined acceptance criteria.

---

# 14. Watchlist

The active watchlist contains a maximum of:

**20 stocks**

The constraint is intentional.

The system should deeply analyze a small universe rather than superficially scan thousands of securities.

---

# 15. Watchlist Features

Each row should display at minimum:

- symbol;
    
- price;
    
- daily percentage move;
    
- sector;
    
- relative strength;
    
- relative volume;
    
- RVE;
    
- volatility;
    
- liquidity;
    
- setup status;
    
- ML score where available;
    
- trade recommendation;
    
- position status;
    
- live P&L where applicable.
    

---

# 16. Watchlist Management

Users should be able to:

- add stocks;
    
- remove stocks;
    
- reorder stocks;
    
- pin stocks;
    
- temporarily suspend stocks;
    
- attach notes;
    
- define strategy eligibility;
    
- define whether derivatives are allowed.
    

Maximum active count remains 20.

---

# 17. Opportunity Board

The 20 stocks should compete against each other.

Instead of evaluating:

> Is RELIANCE good?

the system should evaluate:

> Is RELIANCE better than the other available opportunities?

The dashboard should continuously rank candidates from:

**#1 → #20**

based on expected risk-adjusted opportunity quality.

---

# 18. Broker Abstraction

Strategies must never directly depend on Groww, Zerodha or another broker.

Architecture:

```text
Strategy
    ↓
Trade Plan
    ↓
Execution Engine
    ↓
Broker Interface
    ↓
Zerodha / Groww / Other Broker
```

Broker-specific logic exists only inside adapters.

---

# 19. Broker Interface

A normalized broker interface should expose capabilities including:

- authentication;
    
- connection state;
    
- instrument lookup;
    
- quotes;
    
- WebSocket/live feed;
    
- historical candles;
    
- holdings;
    
- positions;
    
- margins;
    
- available capital;
    
- order placement;
    
- order modification;
    
- cancellation;
    
- order status;
    
- trades/fills;
    
- order updates;
    
- broker errors.
    

---

# 20. Zerodha Integration

Zerodha Kite Connect currently provides REST-style APIs covering orders, instruments, portfolios and historical data, alongside WebSocket streaming for live market data and order updates.

The adapter should support:

- authentication;
    
- instrument master import;
    
- quote snapshots;
    
- live WebSocket feed;
    
- depth where available;
    
- order placement;
    
- order modification;
    
- cancellation;
    
- fills;
    
- order status;
    
- holdings;
    
- positions;
    
- margins;
    
- historical candles.
    

---

# 21. Groww Integration

Groww currently exposes APIs for order management, live market snapshots, historical candles and derivatives information including option-chain data. Its order API explicitly supports retrieving the multiple trade fills that can result from a single order.

The adapter should normalize Groww behavior into the same internal broker model.

---

# 22. Broker Capability Registry

Not all brokers support identical functionality.

Maintain capabilities such as:

|Capability|Supported?|
|---|---|
|Live streaming|Boolean|
|Market depth|Boolean|
|Historical data|Boolean|
|Option chain|Boolean|
|Greeks|Boolean|
|Order updates|Boolean|
|GTT|Boolean|
|Smart orders|Boolean|
|Basket orders|Boolean|
|Margin API|Boolean|

Strategies request capabilities rather than brokers.

---

# 23. Broker Health

Track:

- connected;
    
- reconnecting;
    
- authentication failure;
    
- degraded;
    
- unavailable;
    
- rate limited.
    

Measurements include:

- request latency;
    
- order acknowledgement latency;
    
- WebSocket heartbeat age;
    
- reconnect count;
    
- API error frequency;
    
- stale feed duration.
    

---

# 24. Instrument Master

Maintain an internal canonical instrument database.

Potential fields:

```text
instrument_id
exchange
segment
symbol
ISIN
company
sector
industry
tick_size
lot_size
expiry
strike
option_type
underlying
broker_tokens
trading_status
```

Trading logic should refer to internal IDs rather than broker-specific instrument tokens.

---

# 25. Corporate Instrument Mapping

The security master should account for:

- symbol changes;
    
- derivatives expiries;
    
- option strikes;
    
- futures rolls;
    
- splits;
    
- mergers;
    
- bonuses;
    
- delistings;
    
- corporate actions.
    

---

# 26. Market Data Architecture

A central market-data service should ingest and normalize:

- LTP;
    
- trade ticks;
    
- bid;
    
- ask;
    
- depth;
    
- OHLC;
    
- volume;
    
- open interest;
    
- futures data;
    
- options data;
    
- index data.
    

---

# 27. Event Timestamps

Each market event should ideally track:

```text
exchange_timestamp
broker_timestamp
received_timestamp
processed_timestamp
```

This allows:

- feed latency measurement;
    
- replay;
    
- stale-data detection;
    
- execution analysis.
    

---

# 28. Candle Construction

Where needed, construct canonical candles internally from market data.

Supported intervals can include:

- 1 minute;
    
- 3 minutes;
    
- 5 minutes;
    
- 15 minutes;
    
- 30 minutes;
    
- 60 minutes;
    
- daily;
    
- weekly.
    

Candle construction logic must be identical between research and live trading where feasible.

---

# 29. Historical Data Store

The historical platform should retain sufficient information for:

- backtests;
    
- feature reconstruction;
    
- ML training;
    
- transaction simulation;
    
- historical similarity search.
    

Raw vendor data and normalized internal data should remain distinguishable.

---

# 30. Market Context

Individual stocks should never be analyzed in complete isolation.

The system evaluates:

```text
Stock
↕
Sector
↕
Broad Market
```

---

# 31. Broad Market Inputs

Potential references include:

- NIFTY 50;
    
- NIFTY 500;
    
- BANK NIFTY;
    
- other relevant indices;
    
- market breadth;
    
- advance/decline;
    
- volatility measures.
    

Exact features belong in DD-02.

---

# 32. Sector Context

Each stock should be mapped to an appropriate sector benchmark where possible.

Examples include:

- IT;
    
- banks;
    
- automobile;
    
- pharma;
    
- metals;
    
- financial services;
    
- energy.
    

The system should be capable of identifying:

- sector leadership;
    
- sector weakness;
    
- stock-vs-sector divergence.
    

---

# 33. Feature Engine

The feature engine transforms raw market information into deterministic measurements.

Features must:

- have explicit mathematical definitions;
    
- have versions;
    
- support historical reconstruction;
    
- support live calculation;
    
- be timestamp correct.
    

---

# 34. Real Relative Strength

Real Relative Strength — RRS — will be a first-class measurement.

Potential components to research include:

- stock performance vs index;
    
- stock performance vs sector;
    
- volatility-adjusted excess movement;
    
- persistence;
    
- acceleration;
    
- multi-timeframe agreement.
    

The final formula will be defined in DD-02.

---

# 35. RRS Requirements

RRS should support:

- live value;
    
- historical value;
    
- normalized score;
    
- percentile;
    
- trend;
    
- acceleration;
    
- multi-timeframe values.
    

Possible representation:

```text
RRS = +2.41
Percentile = 94
Direction = Increasing
Interpretation = Strong Outperformance
```

---

# 36. Relative Volume

Relative Volume should be calculated contextually.

Naive:

```text
Current Volume / Average Daily Volume
```

is insufficient intraday.

At 10:15 AM, today's cumulative volume should preferably be compared to historical cumulative volume around 10:15 AM.

---

# 37. RVOL Features

Potential measurements include:

- cumulative intraday RVOL;
    
- interval RVOL;
    
- same-time historical baseline;
    
- day-of-week normalization;
    
- volume acceleration;
    
- breakout participation;
    
- abnormal volume percentile.
    

---

# 38. RVE

RVE will be treated as a dedicated indicator rather than an undocumented derived value.

DD-02 must formally define:

- mathematical interpretation;
    
- normalization;
    
- expected range;
    
- historical baseline;
    
- intraday behavior;
    
- relationship to returns.
    

---

# 39. General Indicator Framework

Additional indicators may include:

- ATR;
    
- VWAP;
    
- anchored VWAP;
    
- trend strength;
    
- volatility;
    
- momentum;
    
- moving-average relationships;
    
- gap behavior;
    
- price compression;
    
- breakout magnitude;
    
- spread;
    
- liquidity;
    
- breadth.
    

Indicators must implement a common interface.

---

# 40. Multi-Timeframe Analysis

Strategies should be able to combine multiple horizons.

Example:

```text
Daily trend = bullish
15m trend = bullish
5m setup = pullback
1m trigger = confirmation
```

The engine should distinguish setup timeframe from context timeframe.

---

# 41. Market Regime Engine

The platform should classify market conditions.

Possible regimes:

- strong bullish;
    
- weak bullish;
    
- strong bearish;
    
- weak bearish;
    
- range;
    
- high-volatility range;
    
- low-volatility compression;
    
- gap trend;
    
- gap reversal;
    
- abnormal/dislocated.
    

Exact methodology belongs in DD-02.

---

# 42. Regime-Strategy Compatibility

Each strategy specifies allowed regimes.

Example:

```text
Trend Breakout:
Allowed = bullish trend
Prohibited = low-volatility range

Mean Reversion:
Allowed = range
Prohibited = strong directional trend
```

This prevents one strategy from being indiscriminately applied to every market.

---

# 43. Setup Detection Engine

The system continuously evaluates all 20 stocks for recognized setups.

Potential families to research:

- momentum;
    
- relative-strength continuation;
    
- breakout;
    
- pullback;
    
- opening-range breakout;
    
- VWAP reclaim;
    
- volatility expansion;
    
- mean reversion;
    
- reversal.
    

Only a small subset should initially enter production.

---

# 44. Strategy Registry

Each strategy should include:

```text
strategy_id
version
name
description
setup_family
timeframe
entry_rules
exit_rules
risk_requirements
allowed_regimes
supported_instruments
parameters
deployment_status
```

---

# 45. Strategy Lifecycle

Possible states:

```text
RESEARCH
EXPERIMENTAL
BACKTESTED
VALIDATED
SHADOW
PAPER
LIVE_LIMITED
PRODUCTION
RETIRED
```

---

# 46. Deterministic Setup Output

A valid setup should generate structured information.

Example:

```text
Strategy:
RS_BREAKOUT_V1

Direction:
LONG

Status:
VALID

Entry:
₹1542–₹1546

Stop:
₹1521

Target Reference:
₹1598

Base Reward/Risk:
2.4
```

---

# 47. Near-Setup State

The system should detect opportunities before they trigger.

Statuses may include:

```text
NONE
WATCH
FORMING
NEAR_TRIGGER
VALID
INVALIDATED
```

This supports user visibility and future ML research.

---

# 48. Opportunity Scoring

The system should avoid reducing everything to BUY/SELL.

A deterministic opportunity score may include:

|Factor|Example|
|---|--:|
|Relative Strength|92|
|Relative Volume|84|
|Trend Quality|88|
|Sector Alignment|80|
|Market Alignment|90|
|Liquidity|96|
|Reward/Risk|81|

Exact weighting must be researched rather than arbitrarily selected.

---

# 49. ML Opportunity Layer

After deterministic qualification, the ML layer receives contextual information and estimates opportunity quality.

Potential outputs:

```text
P(target before stop)
Expected R
Expected MFE
Expected MAE
Expected holding time
Confidence
Historical similarity
```

---

# 50. ML Inputs

Features may include:

### Stock

- RRS;
    
- RVOL;
    
- RVE;
    
- volatility;
    
- momentum;
    
- VWAP distance;
    
- ATR;
    
- gap;
    
- liquidity;
    
- spread.
    

### Sector

- sector trend;
    
- sector RRS;
    
- breadth;
    
- leadership.
    

### Market

- NIFTY regime;
    
- market strength;
    
- breadth;
    
- volatility.
    

### Context

- time;
    
- day;
    
- strategy;
    
- setup;
    
- expiry proximity.
    

### Derivatives

- IV;
    
- Greeks;
    
- OI;
    
- option spread;
    
- expiry.
    

---

# 51. Stock-Specific Intelligence

The model should eventually learn that identical indicator combinations produce different outcomes for different stocks.

Example:

```text
RELIANCE

RRS > 1.8
RVOL > 1.7
Market bullish
Energy strong
10:15–11:30

→ historically favorable
```

while the same combination may be mediocre for another security.

---

# 52. Hierarchical ML

Training a completely independent model for every stock may produce insufficient sample sizes.

A better architecture may combine:

```text
Global Model
+
Strategy Behavior
+
Sector Behavior
+
Stock-Specific Evidence
```

Stock-specific influence grows as sufficient evidence accumulates.

---

# 53. Cross-Stock Intelligence

The watchlist itself becomes contextual information.

Potential signals:

- relative ranking;
    
- sector breadth;
    
- correlated moves;
    
- leadership;
    
- laggard behavior;
    
- rotation.
    

For example, a bank breakout may be more credible if several other major banks and BANK NIFTY exhibit aligned strength.

---

# 54. ML Authority Level 0 — Observer

Initially ML has no influence.

```text
Strategy → Risk → Trade

ML separately predicts outcome
```

This establishes whether the model has useful predictive information.

---

# 55. ML Authority Level 1 — Ranking

After validation, ML may rank valid opportunities.

Example:

```text
RELIANCE     89
INFY         84
HDFCBANK     73
TCS          66
```

This helps allocate scarce risk capital.

---

# 56. ML Authority Level 2 — Filtering

ML may eventually reject deterministic valid setups.

Example:

```text
Strategy = VALID
ML probability = 48%

Final = SKIP
```

ML remains incapable of promoting invalid setups.

---

# 57. ML Authority Level 3 — Risk Modifier

After further evidence, ML may influence risk size within predefined boundaries.

Example:

```text
Base strategy risk:
0.50%

Low ML quality:
0.25%

Normal:
0.40%

High:
0.50%

Exceptional:
0.60%
```

The deterministic risk engine maintains the absolute ceiling.

---

# 58. Future ML Authority

More advanced research may investigate:

- adaptive parameters;
    
- entry optimization;
    
- target optimization;
    
- dynamic exits;
    
- execution optimization.
    

Each capability requires independent evidence.

Fully ML-generated strategies must be treated as separate experimental systems.

---

# 59. Recommendation Engine

Final recommendations combine:

```text
Strategy Validity
+
ML Opportunity Quality
+
Portfolio Ranking
+
Risk Permission
```

Potential states:

```text
NO TRADE
WATCH
SETUP FORMING
READY
ENTER
ADD
HOLD
REDUCE
EXIT
EMERGENCY EXIT
```

---

# 60. Recommendation Explainability

Every recommendation should expose three distinct explanations.

## Strategy

Why is this structurally valid?

## ML

Why does history suggest this opportunity is favorable or unfavorable?

## Risk

Why is the proposed position size permitted?

This separation is mandatory.

---

# 61. Historical Similarity

The platform should eventually answer:

> How did this stock behave under historically similar conditions?

Example:

```text
Similar observations: 83

Target before stop:
68%

Median MFE:
1.6R

Median MAE:
-0.48R
```

Similarity methodology should be carefully validated.

---

# 62. Options Analytics

Where derivatives are available, the platform evaluates whether the trade should be expressed through:

- equity;
    
- future;
    
- call;
    
- put;
    
- eventually defined-risk structures.
    

---

# 63. Option Chain

Potential option features:

- strikes;
    
- expiries;
    
- bid;
    
- ask;
    
- volume;
    
- open interest;
    
- implied volatility;
    
- Delta;
    
- Gamma;
    
- Theta;
    
- Vega;
    
- intrinsic value;
    
- extrinsic value.
    

Groww's current API documentation, for example, exposes option-chain information including Greeks, open interest and volume.

---

# 64. Option Liquidity

An excellent underlying signal does not imply an acceptable option trade.

Reject contracts where:

- spread is excessive;
    
- volume is insufficient;
    
- OI is insufficient;
    
- expected slippage is excessive;
    
- expiry risk is unacceptable.
    

---

# 65. Instrument Selection Engine

The strategy should initially define the directional opportunity independently of instrument selection.

Example:

```text
Underlying Opportunity:
RELIANCE LONG
```

Then the instrument engine may compare:

```text
Equity
Future
Call
```

against:

- expected return;
    
- risk;
    
- liquidity;
    
- capital requirement;
    
- costs;
    
- Greeks.
    

---

# 66. Expiry Management

Derivatives require explicit expiry logic.

The system should prevent accidental expiry exposure.

Controls may include:

- expiry warnings;
    
- prohibited trading windows;
    
- forced exit rules;
    
- settlement risk policies;
    
- changing margin awareness.
    

---

# 67. Trade Plan

Every permitted trade creates a trade plan before execution.

The plan contains:

- strategy;
    
- strategy version;
    
- direction;
    
- instrument;
    
- entry;
    
- stop;
    
- target methodology;
    
- expected reward/risk;
    
- quantity;
    
- maximum risk;
    
- setup;
    
- invalidation;
    
- ML evaluation;
    
- portfolio impact.
    

---

# 68. Immutable Trade Intent

Once execution begins, preserve the original trade plan.

Changes should be recorded as separate events rather than rewriting history.

This enables post-trade analysis.

---

# 69. Position Sizing

Position size should primarily derive from permitted risk.

Conceptually:

```text
Maximum Allowed Loss
÷
Loss Per Unit
=
Maximum Units
```

Then apply:

- capital constraints;
    
- liquidity constraints;
    
- lot size;
    
- correlation;
    
- exposure;
    
- derivatives margin;
    
- ML modifier where permitted.
    

---

# 70. Risk Per Trade

Risk should be configurable as:

- percentage of equity;
    
- absolute currency amount;
    
- strategy-specific limit.
    

ML must never override the maximum.

---

# 71. Portfolio Risk

Track:

- gross exposure;
    
- net exposure;
    
- open risk;
    
- sector exposure;
    
- directional exposure;
    
- derivative exposure;
    
- correlated exposure.
    

---

# 72. Correlation Risk

Multiple positions in similar companies may represent one effective bet.

Example:

```text
HDFCBANK
ICICIBANK
AXISBANK
KOTAKBANK
SBIN
```

Portfolio sizing should account for correlation rather than treating these as independent positions.

---

# 73. Drawdown Controls

Risk policies should include:

- daily loss limit;
    
- weekly loss limit;
    
- monthly drawdown;
    
- strategy drawdown;
    
- account drawdown;
    
- consecutive-loss controls.
    

Limits may progressively reduce permitted risk before triggering a complete stop.

---

# 74. Order Management System

Maintain an internal order state independent of broker nomenclature.

Possible lifecycle:

```text
CREATED
↓
SUBMITTED
↓
ACKNOWLEDGED
↓
PARTIAL
↓
FILLED
```

Alternatives include:

```text
REJECTED
CANCELLED
EXPIRED
UNKNOWN
```

---

# 75. Order Record

Store:

```text
internal_order_id
broker_order_id
trade_id
strategy_id
instrument
side
quantity
filled_quantity
requested_price
average_fill
slippage
status
timestamps
broker_response
```

---

# 76. Partial Fills

Partial fills are normal market events.

The OMS must handle:

```text
Requested = 100

Fill 1 = 20
Fill 2 = 50
Fill 3 = 30
```

rather than assuming one order equals one fill.

Groww explicitly documents that one order may produce multiple trade fulfilments.

---

# 77. Idempotent Orders

Duplicate orders are a critical operational risk.

Scenario:

```text
Order submitted
↓
Network timeout
↓
Unknown whether broker accepted
↓
Blind retry
↓
Potential double position
```

Use:

- client references;
    
- reconciliation;
    
- idempotency controls;
    
- UNKNOWN state handling.
    

---

# 78. Execution Engine

The execution engine decides how to implement an approved trade.

It may control:

- market vs limit;
    
- price protection;
    
- slicing;
    
- retries;
    
- re-pricing;
    
- cancellation;
    
- timeout;
    
- partial-fill treatment.
    

Strategy should not manage these directly.

---

# 79. Execution Quality

Measure:

- intended entry;
    
- actual entry;
    
- spread paid;
    
- slippage;
    
- fill duration;
    
- fill percentage;
    
- opportunity cost;
    
- rejected quantity.
    

Execution performance should be analyzed independently from strategy performance.

---

# 80. Position Reconciliation

Internal state cannot blindly be treated as authoritative.

Regularly compare:

```text
Internal Position
vs
Broker Position
```

On mismatch:

```text
Suspend trading
↓
Reconcile
↓
Restore safe state
```

---

# 81. Active Positions

For each open position display:

- symbol;
    
- instrument;
    
- direction;
    
- quantity;
    
- average price;
    
- LTP;
    
- unrealized P&L;
    
- realized P&L;
    
- P&L percentage;
    
- R multiple;
    
- stop;
    
- target;
    
- holding time;
    
- strategy;
    
- ML score;
    
- current recommendation.
    

---

# 82. Continuous P&L

P&L should update continuously using:

```text
Realized P&L
+
Unrealized P&L
-
Estimated / Actual Costs
```

Trade-level and portfolio-level views should both exist.

---

# 83. One-Click Exit

Each position provides:

**EXIT NOW**

The system should:

```text
Cancel conflicting orders
↓
Submit exit
↓
Track fills
↓
Handle partial fills
↓
Confirm flat
↓
Reconcile broker
```

---

# 84. Automatic Exit Engine

Automatic exit causes may include:

- hard stop;
    
- target;
    
- trailing stop;
    
- setup invalidation;
    
- strategy exit;
    
- market reversal;
    
- sector reversal;
    
- time stop;
    
- end of session;
    
- expiry;
    
- portfolio risk event;
    
- infrastructure emergency.
    

---

# 85. Exit Reasons

Every exit receives a structured reason.

Examples:

```text
STOP
TARGET
TRAIL
TIME_STOP
REGIME_CHANGE
STRATEGY_INVALIDATION
RISK_EXIT
MANUAL_EXIT
EMERGENCY_EXIT
```

This becomes valuable research data.

---

# 86. Kill Switch

Global emergency controls should include:

### Stop New Trades

Existing positions remain managed.

### Cancel Pending Orders

Prevents further entries.

### Flatten Algorithm Positions

Exit positions controlled by the platform.

### Emergency Stop

Disable all automated execution.

---

# 87. Circuit Breakers

Automatically pause trading for conditions including:

- daily maximum loss reached;
    
- excessive broker errors;
    
- stale market data;
    
- abnormal slippage;
    
- repeated order rejection;
    
- reconciliation mismatch;
    
- abnormal strategy behavior;
    
- system degradation.
    

---

# 88. Paper Trading

Paper trading should use exactly the same:

```text
Feature Engine
Strategy
ML
Risk Engine
Trade Plan
Position Management
```

as live trading.

Only execution changes.

```text
Live:
BrokerExecutionAdapter

Paper:
SimulationExecutionAdapter
```

---

# 89. Realistic Paper Execution

Paper trading must not execute every trade instantly at LTP.

Model:

- bid/ask spread;
    
- market depth;
    
- slippage;
    
- latency;
    
- partial fills;
    
- insufficient liquidity;
    
- order cancellation;
    
- rejected orders;
    
- price movement during execution.
    

---

# 90. Transaction Costs

Simulation should support configurable Indian-market costs such as applicable:

- brokerage;
    
- STT;
    
- exchange charges;
    
- GST;
    
- SEBI charges;
    
- stamp duty;
    
- other applicable charges.
    

Rates should be configuration/data rather than permanent code constants.

---

# 91. Execution Fault Simulation

Paper trading should support deliberate faults:

- API timeout;
    
- feed disconnect;
    
- order rejection;
    
- partial fill;
    
- delayed fill;
    
- stale quote;
    
- broker outage.
    

This tests operational reliability rather than just strategy profitability.

---

# 92. Backtesting Engine

Backtesting should reuse production strategy logic wherever practical.

Support:

- equities;
    
- futures;
    
- options where reliable datasets permit;
    
- intraday;
    
- multi-day;
    
- multi-stock;
    
- portfolio-level backtests.
    

---

# 93. Point-in-Time Correctness

Backtesting must prevent future information from entering past decisions.

Avoid:

- look-ahead bias;
    
- future volume;
    
- future constituents;
    
- future corporate actions;
    
- future normalization values;
    
- survivorship bias.
    

---

# 94. Transaction-Aware Backtesting

Backtests should model:

```text
Signal Price
≠
Execution Price
```

Results should account for:

- fees;
    
- spread;
    
- slippage;
    
- liquidity;
    
- partial-fill assumptions.
    

---

# 95. Backtest Metrics

Important metrics include:

- net return;
    
- CAGR where relevant;
    
- average R;
    
- expectancy;
    
- win rate;
    
- average winner;
    
- average loser;
    
- profit factor;
    
- maximum drawdown;
    
- Sharpe;
    
- Sortino;
    
- turnover;
    
- exposure;
    
- consecutive losses;
    
- tail loss.
    

Win rate alone should not be treated as strategy quality.

---

# 96. Walk-Forward Testing

Parameter development should separate periods.

Example:

```text
Train
↓
Validate
↓
Out-of-Sample Test
```

Repeated chronological walk-forward windows should be supported.

---

# 97. Monte Carlo Analysis

Trade outcomes can be resampled to estimate:

- likely drawdowns;
    
- loss streaks;
    
- return ranges;
    
- required capital;
    
- risk of ruin;
    
- confidence intervals.
    

---

# 98. Robustness Testing

A strategy should not depend on one magical threshold.

If:

```text
RRS threshold = 1.73
```

works extremely well while:

```text
1.70
1.76
```

collapse, overfitting is likely.

Prefer broad parameter stability.

---

# 99. Strategy Research Framework

Each research experiment should record:

```text
experiment_id
hypothesis
strategy_version
dataset
universe
date_range
parameters
cost_model
results
notes
code_version
```

---

# 100. Strategy Research Direction

DD-02 will investigate established systematic strategy families including:

- momentum;
    
- trend following;
    
- relative strength;
    
- breakout;
    
- volume-confirmed breakout;
    
- opening-range methods;
    
- volatility expansion;
    
- pullbacks;
    
- mean reversion.
    

The objective is not to invent arbitrary indicator combinations.

---

# 101. Fine-Tuning

Possible optimization methods:

- grid search;
    
- random search;
    
- Bayesian optimization;
    
- walk-forward optimization;
    
- sensitivity analysis.
    

Optimization objective should emphasize:

**robust out-of-sample risk-adjusted expectancy**

rather than historical maximum return.

---

# 102. Feature Store

Every relevant market observation should be stored in a structured feature store.

Example:

```text
timestamp
instrument
price

RRS
RVOL
RVE
ATR
VWAP_distance
volatility
momentum

sector_RRS
sector_breadth

market_regime
market_breadth

time_of_day
day_of_week

setup
strategy

spread
liquidity

options_features
```

---

# 103. Feature Versioning

Every feature requires:

```text
feature_name
feature_version
calculation_version
parameters
```

Changing the RRS formula, for example, creates a new feature version rather than silently altering historical meaning.

---

# 104. Setup Observation Dataset

Do not record only executed trades.

Capture:

- executed valid setups;
    
- skipped valid setups;
    
- risk-rejected setups;
    
- ML-rejected setups;
    
- near-setups;
    
- invalid setups;
    
- lower-ranked opportunities.
    

This reduces selection bias.

---

# 105. Outcome Labels

Historical observations should eventually receive labels such as:

```text
return_1m
return_5m
return_15m
return_30m
return_60m
return_close

MFE
MAE

target_1R_hit
target_2R_hit
stop_hit

target_before_stop
time_to_target
time_to_stop
```

---

# 106. ML Dataset Integrity

The ML training pipeline must use point-in-time features.

Prohibit:

- future leakage;
    
- future volume leakage;
    
- future volatility;
    
- future corporate actions;
    
- labels appearing in features;
    
- normalization against future samples.
    

---

# 107. ML Training Pipeline

Conceptually:

```text
Historical Data
↓
Feature Engine
↓
Strategy / Setup Engine
↓
Feature Snapshots
↓
Outcome Labels
↓
Train
↓
Validation
↓
Out-of-Sample Test
↓
Probability Calibration
↓
Model Registry
```

---

# 108. ML Evaluation

Metrics may include:

- calibration;
    
- log loss;
    
- Brier score;
    
- ranking quality;
    
- expected R by confidence bucket;
    
- profitability after costs;
    
- drawdown change;
    
- lift over deterministic baseline.
    

Raw accuracy is insufficient.

---

# 109. ML Baseline Comparison

Every model must be compared against:

**Strategy without ML**

Example:

```text
Deterministic:

Expectancy = +0.20R
Profit Factor = 1.30
Max DD = -12R
```

versus:

```text
Deterministic + ML:

Expectancy = +0.32R
Profit Factor = 1.52
Max DD = -8R
```

ML earns influence only by producing robust out-of-sample improvement.

---

# 110. ML Shadow Mode

Before influencing capital:

```text
Actual Decision:
ENTER

ML:
SKIP
Confidence = 54%
```

Both decisions are recorded.

Later analyze whether the ML recommendation added value.

---

# 111. Model Registry

Store:

```text
model_id
version
algorithm
feature_schema
strategy_compatibility
training_period
validation_period
test_period
metrics
deployment_state
```

---

# 112. ML Deployment Lifecycle

Models progress through:

```text
EXPERIMENT
↓
VALIDATED
↓
SHADOW
↓
PAPER
↓
LIVE_LIMITED
↓
PRODUCTION
↓
RETIRED
```

---

# 113. Model Drift

Monitor:

- feature drift;
    
- prediction drift;
    
- calibration drift;
    
- stock-specific drift;
    
- regime changes;
    
- performance degradation.
    

Models should be downgraded or disabled when confidence in them falls.

---

# 114. Prediction Audit Trail

Each prediction should record:

```text
model_id
model_version
feature_version
strategy_version
timestamp
instrument
input_features
prediction
confidence
final_decision
actual_outcome
```

---

# 115. ML Guardrails

ML cannot override:

- compliance restrictions;
    
- system-health stops;
    
- max trade risk;
    
- max daily loss;
    
- liquidity restrictions;
    
- maximum spread;
    
- correlation limits;
    
- position limits;
    
- expiry policy;
    
- broker reconciliation;
    
- kill switches.
    

---

# 116. Dashboard

The primary dashboard should show:

### Market

- NIFTY;
    
- regime;
    
- breadth;
    
- volatility.
    

### System

- broker state;
    
- market-data state;
    
- autopilot status;
    
- strategy health.
    

### Portfolio

- capital;
    
- exposure;
    
- daily P&L;
    
- open risk.
    

### Opportunity Board

The 20-stock ranking.

### Positions

All active positions and live P&L.

---

# 117. Stock Detail View

Each stock should expose:

- chart;
    
- volume;
    
- RRS;
    
- RVOL;
    
- RVE;
    
- VWAP;
    
- volatility;
    
- sector comparison;
    
- market comparison;
    
- setup;
    
- deterministic reasoning;
    
- ML evaluation;
    
- options;
    
- possible trade plan;
    
- historical similarity.
    

---

# 118. Why Interface

Every recommendation should expose:

## Why Strategy Likes It

Example:

```text
RRS threshold passed
RVOL confirmed
Breakout valid
Sector strong
R:R acceptable
```

## Why ML Likes It

Example:

```text
Stock-specific historical behavior favorable
Time-of-day favorable
Sector participation high
Similar setups performed well
```

## Risks

Example:

```text
Market volatility elevated
Spread above median
```

---

# 119. Trade Journal

Every trade is automatically journaled.

Include:

- setup;
    
- strategy;
    
- entry;
    
- fills;
    
- stop;
    
- modifications;
    
- exit;
    
- P&L;
    
- R;
    
- MFE;
    
- MAE;
    
- strategy explanation;
    
- ML prediction;
    
- execution quality;
    
- human notes.
    

---

# 120. Performance Analytics

Analyze performance by:

- stock;
    
- strategy;
    
- setup;
    
- sector;
    
- regime;
    
- time;
    
- day;
    
- direction;
    
- confidence;
    
- RRS bucket;
    
- RVOL bucket;
    
- RVE bucket;
    
- instrument;
    
- exit reason.
    

The product should reveal actionable patterns.

---

# 121. Decision Logging

Record decisions to trade **and not trade**.

Example:

```text
INFY
10:17:03

Setup:
RS_BREAKOUT

Status:
REJECTED

Reason:
MIN_RR_FAILED
```

This history is critical for research.

---

# 122. Event-Sourced Trading Ledger

Important trading events should be immutable.

Examples:

```text
SIGNAL_CREATED
SETUP_VALIDATED
ML_PREDICTION_CREATED
RISK_APPROVED
TRADE_PLAN_CREATED
ORDER_SUBMITTED
ORDER_ACKNOWLEDGED
ORDER_PARTIAL_FILL
ORDER_FILLED
STOP_UPDATED
EXIT_REQUESTED
POSITION_CLOSED
```

Current state can then be reconstructed.

---

# 123. Alerts

Alert categories include:

- setup forming;
    
- trade ready;
    
- entry;
    
- partial fill;
    
- rejection;
    
- stop;
    
- target;
    
- exit;
    
- risk warning;
    
- broker disconnected;
    
- stale feed;
    
- kill switch;
    
- autopilot disabled.
    

---

# 124. Observability

Monitor:

- market-data latency;
    
- feature latency;
    
- strategy latency;
    
- ML latency;
    
- broker latency;
    
- order latency;
    
- dropped events;
    
- API errors;
    
- service health;
    
- queue lag;
    
- database health.
    

---

# 125. Data Quality Monitoring

Detect:

- missing candles;
    
- duplicate ticks;
    
- impossible prices;
    
- stale LTP;
    
- zero/abnormal volume;
    
- timestamp anomalies;
    
- corrupted option data;
    
- missing index data.
    

Trading should be disabled when required data cannot be trusted.

---

# 126. Security

Broker credentials are highly sensitive.

Requirements:

- encrypted secrets;
    
- TLS;
    
- secret manager;
    
- no credential logging;
    
- restricted service access;
    
- credential rotation;
    
- audit logs;
    
- least privilege.
    

---

# 127. Retail Algo Regulatory Architecture

The platform operates inside an evolving regulated environment.

SEBI's retail algorithmic trading framework became applicable to all stock brokers from **April 1, 2026**, following the implementation timeline established by SEBI.

Therefore compliance must be treated as a platform concern rather than something added after building the algorithm.

---

# 128. Compliance Capabilities

The architecture should accommodate:

- broker algo requirements;
    
- exchange requirements;
    
- order tagging;
    
- API controls;
    
- registration workflows;
    
- algorithm version records;
    
- audit trails;
    
- user authorization;
    
- operational controls.
    

Exact requirements should be verified with applicable broker/exchange/regulatory rules before production deployment.

---

# 129. Market Data Licensing

Private single-user consumption and commercial data redistribution are different legal/commercial use cases.

Before multi-tenant launch, investigate:

- live quote licensing;
    
- historical-data rights;
    
- storage rights;
    
- derived-data rights;
    
- redistribution rights.
    

Do not assume broker market data can automatically be redistributed to customers.

---

# 130. Regulatory Configuration

Regulatory and broker-specific constraints should be configuration-driven where practical.

Examples:

```text
rate_limits
order_limits
allowed_segments
trading_windows
restricted_products
broker_capabilities
```

---

# 131. Trading Calendar

Maintain a canonical calendar including:

- exchange trading days;
    
- holidays;
    
- normal sessions;
    
- pre-open;
    
- special sessions;
    
- derivatives expiry.
    

Algorithms should not infer sessions from weekdays alone.

---

# 132. Corporate Actions

Historical and live systems must correctly account for:

- splits;
    
- bonuses;
    
- dividends;
    
- rights;
    
- mergers;
    
- symbol changes.
    

Incorrect adjustments can invalidate backtesting.

---

# 133. Daily Pre-Market Lifecycle

Before market open:

```text
Authenticate Brokers
↓
Load Instrument Master
↓
Verify Market Data
↓
Reconcile Positions
↓
Load Watchlist
↓
Preload Historical Data
↓
Calculate Reference Features
↓
Validate Risk Limits
↓
Initialize Strategies
```

Autopilot cannot start until all required checks pass.

---

# 134. Market Open Behavior

The opening period should potentially use specific controls because:

- volatility is higher;
    
- spreads may widen;
    
- price discovery is incomplete;
    
- volume baselines behave differently.
    

Exact strategy treatment belongs in DD-02.

---

# 135. Intraday Lifecycle

During normal market operation:

```text
Receive Data
↓
Update Features
↓
Detect Setups
↓
ML Evaluate
↓
Rank
↓
Risk Check
↓
Execute
↓
Manage
↓
Reconcile
```

---

# 136. End-of-Day Lifecycle

At session end:

- enforce intraday exits;
    
- reconcile positions;
    
- calculate final P&L;
    
- calculate costs;
    
- close journal entries;
    
- record strategy outcomes;
    
- produce daily analytics;
    
- persist research observations.
    

---

# 137. Daily Report

Include:

- opportunities detected;
    
- trades;
    
- rejected trades;
    
- wins;
    
- losses;
    
- gross P&L;
    
- costs;
    
- net P&L;
    
- slippage;
    
- expectancy;
    
- best/worst trade;
    
- strategy performance;
    
- ML performance;
    
- operational incidents.
    

---

# 138. Strategy Configuration

Strategy parameters should be configuration-driven and versioned.

Conceptual example:

```text
min_rrs
min_rvol
min_rve
minimum_rr
allowed_regimes
entry_window
stop_method
exit_method
```

---

# 139. Simulation-Live Parity

A core invariant:

```text
Signal
↓
Trade Plan
↓
Risk
```

must be identical between paper and live modes.

Only the final execution adapter changes.

---

# 140. Feature Flags

Controlled rollout should support:

- strategies;
    
- indicators;
    
- ML models;
    
- brokers;
    
- execution algorithms;
    
- UI functionality.
    

---

# 141. Shadow Trading

New strategies should be capable of running without capital.

Shadow mode generates:

- setups;
    
- hypothetical orders;
    
- simulated fills;
    
- hypothetical exits;
    

while production trading continues independently.

---

# 142. A/B Research

Parallel strategy versions may be compared against identical observations.

Example:

```text
RS_BREAKOUT_V2.1
vs
RS_BREAKOUT_V2.2
```

Compare:

- opportunity count;
    
- expected R;
    
- drawdown;
    
- fills;
    
- costs.
    

---

# 143. Failure Recovery

On restart:

```text
Trading Disabled
↓
Authenticate
↓
Read Broker Orders
↓
Read Broker Positions
↓
Rebuild Internal State
↓
Reconcile
↓
Restore Protection
↓
Enable Trading
```

Never assume the system is flat because the application restarted.

---

# 144. High Availability

Future autopilot may require:

- health checks;
    
- redundant infrastructure;
    
- database backups;
    
- automatic restart;
    
- feed reconnect;
    
- broker reconnect.
    

However, redundancy must never cause duplicate orders.

---

# 145. Manual Override

Human authority remains above automation.

The operator can:

- disable a stock;
    
- disable a strategy;
    
- stop new trades;
    
- reject an entry;
    
- reduce a position;
    
- exit a position;
    
- disable broker execution;
    
- stop autopilot;
    
- trigger emergency flatten.
    

---

# 146. Autopilot Permissions

Autopilot should be permission-based rather than simple ON/OFF.

Possible settings:

```text
Allowed Stocks
Allowed Strategies
Allowed Instruments
Max Risk Per Trade
Max Daily Risk
Max Positions
Trading Hours
Options Allowed
Overnight Allowed
```

---

# 147. Autopilot Progressive Rollout

Automation should expand gradually.

Example:

### Stage A

Only one strategy and very small capital.

### Stage B

Additional stocks.

### Stage C

Additional strategies.

### Stage D

Larger risk limits.

Each expansion requires production evidence.

---

# 148. Market Anomaly Protection

Trading may automatically suspend during:

- exchange disruption;
    
- broker outage;
    
- widespread stale data;
    
- abnormal volatility;
    
- impossible prices;
    
- extraordinary spreads.
    

The system does not need to participate in every market condition.

---

# 149. Admin Console

Future internal administration should expose:

- users;
    
- tenants;
    
- broker accounts;
    
- strategies;
    
- deployments;
    
- feature flags;
    
- models;
    
- system health;
    
- compliance state;
    
- audit logs;
    
- incidents.
    

---

# 150. Multi-Tenant Isolation

Future SaaS isolation must cover:

- credentials;
    
- positions;
    
- orders;
    
- portfolio information;
    
- models;
    
- feature personalization;
    
- alerts;
    
- audit trails.
    

Cross-tenant access should be impossible by architecture.

---

# 151. Core Domain Objects

The initial domain should anticipate:

```text
User
Tenant

BrokerAccount
BrokerConnection

Instrument
Watchlist

MarketTick
Candle

FeatureDefinition
FeatureSnapshot

MarketRegime

Strategy
StrategyVersion

SetupObservation
Signal
Recommendation

MLModel
ModelVersion
ModelPrediction
ModelCalibration
ModelDriftEvent

RiskPolicy
RiskDecision

TradePlan

Order
Fill

Position
Trade

PortfolioSnapshot

Backtest
Experiment

OutcomeLabel

Alert
AuditEvent
```

---

# 152. Core Decision Relationship

A critical relationship is:

```text
FeatureSnapshot
↓
StrategyVersion
↓
SetupObservation
↓
ModelPrediction
↓
RiskDecision
↓
TradePlan
↓
Order
↓
Fill
↓
Position
↓
Outcome
```

This lineage should remain traceable.

---

# 153. MVP-0 — Research Foundation

Build first:

- instrument master;
    
- historical data;
    
- feature engine;
    
- RRS;
    
- RVOL;
    
- RVE;
    
- market context;
    
- strategy interface;
    
- backtesting;
    
- transaction-cost modelling;
    
- experiment tracking;
    
- feature snapshots;
    
- outcome labelling.
    

Primary goal:

> Determine whether we have a real statistical edge.

---

# 154. MVP-1 — Trading Workstation

Add:

- single-user application;
    
- one broker;
    
- 20-stock watchlist;
    
- live data;
    
- live features;
    
- deterministic setup detection;
    
- opportunity ranking;
    
- recommendation UI;
    
- paper trading;
    
- trade journal.
    

ML may begin collecting data but does not need production authority.

---

# 155. MVP-2 — Assisted Live Trading

Add:

- live orders;
    
- trade plans;
    
- human approval;
    
- risk engine;
    
- real-time positions;
    
- continuous P&L;
    
- automatic protective exits;
    
- one-click exit;
    
- reconciliation;
    
- kill switch.
    

ML runs in shadow mode.

---

# 156. MVP-3 — Guarded Autopilot

Add:

- automatic entry;
    
- automatic exit;
    
- strategy permissions;
    
- strict daily risk;
    
- circuit breakers;
    
- restart recovery;
    
- operational monitoring.
    

Primary trading logic remains deterministic.

---

# 157. Phase 4A — ML Ranking

First ML authority:

```text
Valid Opportunities
↓
ML Ranking
↓
Capital Priority
```

ML identifies which deterministic setups deserve attention first.

---

# 158. Phase 4B — ML Filtering

Once validated:

```text
Strategy Valid
↓
ML Evaluation
↓
Accept / Skip
```

ML can reduce trade frequency by rejecting lower-quality valid setups.

---

# 159. Phase 4C — ML Risk Adjustment

Only after further evidence:

```text
Strategy Base Risk
↓
ML Quality Modifier
↓
Risk Engine
↓
Final Position Size
```

Hard risk ceilings remain deterministic.

---

# 160. Phase 4D — Stock Intelligence

Build:

- stock-specific adaptation;
    
- sector adaptation;
    
- cross-stock models;
    
- historical similarity;
    
- regime-specific confidence;
    
- model drift monitoring.
    

---

# 161. Phase 5 — Multi-Tenant Platform

Only after regulatory and commercial feasibility is established:

- authentication;
    
- onboarding;
    
- multiple users;
    
- tenant isolation;
    
- multiple brokers;
    
- billing;
    
- strategy permissions;
    
- customer risk policies;
    
- admin tooling;
    
- compliance workflows.
    

---

# 162. Version 1 Non-Goals

Do not initially build:

- HFT;
    
- market making;
    
- thousands of stocks;
    
- dozens of strategies;
    
- arbitrary user-written strategies;
    
- unrestricted ML trading;
    
- social trading;
    
- every broker;
    
- every Indian exchange;
    
- every asset class.
    

Depth is more important than breadth.

---

# 163. Strategy Success Metrics

Evaluate:

- expectancy after costs;
    
- profit factor;
    
- drawdown;
    
- tail loss;
    
- parameter robustness;
    
- out-of-sample stability;
    
- regime stability.
    

---

# 164. Execution Success Metrics

Evaluate:

- fill percentage;
    
- average slippage;
    
- spread paid;
    
- acknowledgement latency;
    
- rejection rate;
    
- reconciliation accuracy.
    

---

# 165. Risk Success Metrics

Evaluate:

- max drawdown;
    
- tail loss;
    
- daily-loss events;
    
- risk violations;
    
- correlated exposure;
    
- risk-adjusted return.
    

---

# 166. ML Success Metrics

Evaluate:

- calibration;
    
- expected R by confidence;
    
- ranking lift;
    
- filter effectiveness;
    
- reduction in poor trades;
    
- impact on drawdown;
    
- performance against deterministic baseline.
    

---

# 167. Reliability Success Metrics

Evaluate:

- feed uptime;
    
- broker connection uptime;
    
- stale-data incidents;
    
- duplicate orders;
    
- unreconciled positions;
    
- restart failures;
    
- critical incidents.
    

---

# 168. Startup North-Star Metric

The product should optimize for:

> **Repeatable positive risk-adjusted expectancy after transaction costs, execution friction and realistic operational failures.**

A spectacular backtest is not sufficient.

---

# 169. Data as a Core Asset

The product should begin recording ML-quality observations before ML becomes a production capability.

Every trading day should increase the value of the research dataset.

This includes situations where no trade is executed.

---

# 170. Every Strategy Has Two Outputs

From DD-02 onward, each deterministic strategy should produce two outputs.

## Trading Output

```text
Valid / Invalid
Direction
Entry
Stop
Target Method
Reward/Risk
Invalidation
```

## Learning Output

```text
Feature Snapshot
Setup State
Strategy State
Market Context
Sector Context
Liquidity Context
Future Outcome Labels
```

The trading engine therefore doubles as a high-quality structured data-generation system.

---

# 171. Long-Term Intelligence Loop

The long-term product loop is:

```text
20 Watchlist Stocks
        ↓
Real-Time Market Data
        ↓
Deterministic Feature Engine
        ↓
RRS / RVOL / RVE / Context
        ↓
Deterministic Strategy
        ↓
Valid Opportunities
        ↓
ML Meta-Model
        ↓
Stock-Specific Opportunity Quality
        ↓
Cross-Stock Ranking
        ↓
Deterministic Risk Engine
        ↓
Instrument Selection
        ↓
Execution
        ↓
Position Management
        ↓
Automatic / Manual Exit
        ↓
Trade Outcome
        ↓
Research Dataset
        ↓
Strategy Research + ML Training
        ↓
Improved Future Decisions
```

---

# 172. Core Product Rule

The architecture can be summarized as:

> **Rules establish validity.  
> ML estimates opportunity quality.  
> Risk controls capital.  
> Execution controls implementation.**

This is a foundational Edge Relative design invariant.

---

# 173. Recommended Design Document Sequence

Following DD-01, development should proceed through:

### DD-02 — Algorithm Research & Strategy Principles

Research:

- traditional systematic strategies;
- momentum;
- relative strength;
- volume;
- market regimes;
- setup families;
- RRS;
- RVOL;
- RVE;
- entries;
- invalidation;
- exits.

This should define the first candidate trading algorithm.

### DD-03 — Risk Management & Position Sizing

Define:

- account risk;
- trade risk;
- portfolio risk;
- stops;
- drawdown;
- correlation;
- derivatives risk;
- risk escalation;
- kill switches.

### DD-04 — Technical Architecture

Define:

- services;
- databases;
- streaming;
- event bus;
- storage;
- APIs;
- deployment;
- broker adapters;
- security boundaries.

### DD-05 — Market Data & Feature Architecture

Define:

- canonical tick model;
- candles;
- historical storage;
- feature definitions;
- data validation;
- replay;
- point-in-time correctness.

### DD-06 — Backtesting & Research Platform

Define:

- event-driven backtests;
- simulations;
- costs;
- slippage;
- walk-forward testing;
- Monte Carlo;
- experiment tracking.

### DD-07 — Paper Trading & Execution Simulation

Define:

- realistic fills;
- depth;
- latency;
- partial fills;
- failures;
- broker simulation.

### DD-08 — Live Execution & Autopilot

Define:

- OMS;
- execution;
- reconciliation;
- recovery;
- health;
- failover;
- live controls.

### DD-09 — Machine Learning & Stock Intelligence

Define:

- feature store;
- labels;
- model architecture;
- hierarchical models;
- ranking;
- stock personalization;
- calibration;
- drift.

### DD-10 — Product UI & Operator Workstation

Define:

- dashboard;
- watchlist;
- opportunity board;
- stock detail;
- trade plan;
- positions;
- auditability.

### DD-11 — Multi-Tenant & Commercial Architecture

Define:

- users;
- organizations;
- broker onboarding;
- data isolation;
- compliance;
- billing;
- commercial operations.

---

# 174. DD-02 Boundary

DD-01 defines **what the platform must do**.

DD-02 must answer:

> **Exactly how should the deterministic trading algorithm behave?**

DD-02 should not begin with coding.

It should first research and formalize:

```text
Market Regimes
+
Relative Strength
+
Relative Volume
+
RVE
+
Price Structure
+
Volume Confirmation
+
Liquidity
+
Reward/Risk
+
Entry Conditions
+
Invalidation Conditions
+
Exit Conditions
```

Only after those rules have been documented should implementation begin.

---

# 175. Final Product Vision

The finished Edge Relative system should be capable of saying:

> RELIANCE currently has a valid relative-strength continuation setup.

Then:

> Similar RELIANCE setups under this combination of market regime, sector strength, relative volume, volatility and time of day have historically performed substantially better than the strategy baseline.

Then:

> This is currently the second-best opportunity among the 20 watched stocks.

Then:

> Portfolio risk permits ₹1,250 of risk and 110 shares.

Then:

> Execution conditions are acceptable.

Then:

> Enter.

And subsequently:

> Conditions remain valid. Hold.

or:

> The original thesis is invalid. Exit.

Every one of those decisions must remain:

- measurable;
    
- reproducible;
    
- auditable;
    
- testable;
    
- risk controlled.
    

That is the foundational product specification for Edge Relative.