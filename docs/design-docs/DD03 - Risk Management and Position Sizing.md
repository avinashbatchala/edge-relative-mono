# Edge Relative — Risk Management & Position Sizing

**Document:** DD-03  
**Version:** 1.0  
**Status:** Foundational Risk Design / Research Specification  
**Product:** Edge Relative  
**Market:** Indian equities and equity derivatives  
**Initial Exchange Focus:** NSE  
**Initial Trading Scope:** Intraday directional equities  
**Primary Methodological Source:** RealDayTrading Wiki, May 2024  
**Related Documents:** DD-01 — Product & Feature Design Specification; DD-02 — Algorithm Research & Strategy Principles; DD-04A — Development Stack & Engineering Standards; DD-06 — Backtesting & Research Platform; DD-08 — Live Execution & Autopilot  

---

# 1. Purpose

DD-02 answers:

> Is there a structurally valid trade?

DD-03 answers:

> Given a structurally valid trade, are we allowed to take it, and if so, how much capital may we place at risk?

The purpose of DD-03 is to convert the risk philosophy of the RealDayTrading methodology into a deterministic, auditable risk engine suitable for an automated trading system.

The document deliberately separates:

```text
Trade Thesis
    ↓
Structural Invalidation
    ↓
Risk Permission
    ↓
Position Size
    ↓
Portfolio Constraints
    ↓
Execution Permission
```

Risk does not create trades.

Risk controls capital allocated to already-valid trades.

---

# 2. Source Hierarchy

This document distinguishes five classes of statements.

## 2.1 RDT-DERIVED

A principle or observation directly supported by the RealDayTrading Wiki.

## 2.2 EDGE-FORMALIZATION

A deterministic rule introduced by Edge Relative to convert discretionary RDT guidance into machine-executable behavior.

## 2.3 SYSTEM-SAFETY

A control required because an automated system can fail in ways a discretionary trader may not experience, including feed failure, duplicate orders, broker ambiguity, reconciliation errors, and software faults.

## 2.4 NSE-ADAPTATION

A modification required because RDT is primarily written around the US market while Edge Relative initially trades NSE securities.

## 2.5 RESEARCH-HYPOTHESIS

A proposed risk rule or threshold that must be validated before receiving production authority.

No percentage, multiplier, volatility cutoff, correlation cutoff, or drawdown threshold becomes production truth merely because it appears in an example or community trading plan.

---

# 3. Source Provenance

The primary RDT material used by this document includes:

- trade planning, entries, exits, stops, and position sizing — approximately pp. 312–368;
- position-sizing discussions by /u/OptionStalker — approximately pp. 317–329 and 375–382;
- risk management and high-win-rate discussion — approximately pp. 369–389;
- risk/reward discussion — approximately pp. 376–384;
- drawdown discussion — approximately p. 389 onward;
- adding to winners and market-condition-dependent exposure — market-analysis and trade-management sections;
- averaging-down discussion — approximately pp. 337 onward;
- walk-away analysis and trade review — approximately pp. 390–408;
- community trading-plan examples with explicit risk-per-trade and daily-loss limits — approximately pp. 539–565.

Community-contributed numerical rules are evidence of practical implementations, not canonical RDT doctrine.

---

# 4. Relationship to DD-01

DD-01 establishes that:

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

DD-03 operationalizes the risk portion of that hierarchy.

A valid setup from DD-02 remains untradeable whenever DD-03 rejects it.

---

# 5. Relationship to DD-02

DD-02 owns:

- setup validity;
- direction;
- structural invalidation;
- trigger;
- target methodology;
- market regime;
- relative strength / weakness;
- strategy exit logic.

DD-03 consumes those outputs.

DD-03 must not move the strategy's invalidation level merely to manufacture a preferred position size.

---

# 6. Relationship to Execution

DD-03 owns permission and quantity.

DD-08 owns:

- order type;
- slicing;
- routing;
- repricing;
- broker submission;
- partial fills;
- cancellation;
- reconciliation.

Execution may reduce quantity if market conditions prevent safe execution.

Execution may never increase quantity above the risk-approved maximum.

---

# 7. Foundational Risk Principle

The primary risk objective is:

> Preserve the ability to continue trading while allowing validated edges sufficient room to express themselves.

Risk management is not designed to eliminate losses.

It is designed to make losses survivable, bounded, explainable, and statistically compatible with the strategy.

---

# 8. Capital Preservation Priority

Edge Relative optimizes for:

```text
Survival
    ↓
Controlled Drawdown
    ↓
Repeatable Expectancy
    ↓
Capital Growth
```

The system must never reverse this order in pursuit of higher short-term returns.

---

# 9. Risk Is Not Capital Deployed

RDT repeatedly distinguishes position notional from actual trade risk.

For an equity trade:

```text
Notional Exposure
=
Entry Price × Quantity
```

is different from:

```text
Planned Loss at Risk Boundary
≈
Loss Per Unit × Quantity
```

A ₹500,000 position is not automatically ₹500,000 of risk.

---

# 10. Risk Is Also Not Guaranteed by the Stop

Edge Relative adds an automated-system qualification:

```text
Stop Price
≠
Guaranteed Fill Price
```

Therefore trade risk must distinguish:

```text
Nominal Risk
Execution-Adjusted Risk
Stress Risk
```

A stop is a risk boundary, not a guarantee of loss amount.

---

# 11. Trade the Thesis, Not the P&L

RDT repeatedly states that entries and exits should follow technical reasoning rather than emotional reaction to monetary P&L.

Edge Relative adopts the machine equivalent:

> A trade is not exited merely because unrealized P&L is negative, and it is not held merely because realizing a loss is undesirable.

The strategy thesis and higher-level risk controls determine action.

---

# 12. Position Size Must Not Distort Decisions

RDT states that if a trader becomes anxious or exits from fear, the position is too large.

For an automated system, the analogous invariant is:

> Position size must be small enough that risk policy does not require premature strategy distortion under ordinary expected adverse excursion.

This is why structural invalidation precedes sizing.

---

# 13. Structural Invalidation Comes First

The order is:

```text
1. Strategy identifies invalidation
2. Risk measures distance to invalidation
3. Risk computes safe quantity
```

Never:

```text
1. Risk wants a larger quantity
2. Stop is tightened to fit the quantity
3. Strategy behavior changes silently
```

---

# 14. Structural Invalidation Definition

DD-02 supplies a structural invalidation level based on the setup thesis.

Examples include:

- failed breakout level;
- opposite side of compression;
- significant M5 pivot;
- major support/resistance;
- defining trendline;
- setup-specific moving-average recross.

The exact reason is stored with the TradeCandidate.

---

# 15. Thesis Exit vs Protective Stop

Edge Relative distinguishes two concepts.

## Thesis Exit

The strategy concludes that the original trade thesis is no longer valid.

## Protective Stop

A hard safety boundary intended to limit catastrophic loss if normal thesis-exit processing is delayed or unavailable.

For V1, the protective stop must never be closer than the strategy's valid structural exit unless the strategy itself defines that boundary.

---

# 16. Why Automated Trading Requires a Protective Stop

RDT discusses mental stops as a tool used by experienced discretionary traders because context can change.

Edge Relative cannot depend on an unrecorded mental decision.

A live automated system must survive:

- process crash;
- network failure;
- broker disconnect;
- stale feed;
- application deadlock;
- delayed strategy evaluation.

Therefore a protective risk boundary is mandatory whenever the broker/instrument permits reliable protective orders.

---

# 17. Protective Stop Buffer

The hard protective level may include a safety buffer beyond structural invalidation.

Conceptually:

```text
LONG:
protective_stop = invalidation - protective_buffer

SHORT:
protective_stop = invalidation + protective_buffer
```

The buffer must be:

- tick-size aware;
- volatility aware;
- liquidity aware;
- empirically calibrated.

---

# 18. Planned Loss Per Unit

For a long equity trade:

```text
PlannedLossPerUnit
=
EntryPrice - ProtectiveStop
```

For a short equity trade:

```text
PlannedLossPerUnit
=
ProtectiveStop - EntryPrice
```

The value must be positive before sizing can proceed.

---

# 19. Execution Loss Allowance

The system adds an execution allowance:

```text
ExecutionAllowancePerUnit
=
ExpectedAdverseSlippage
+
EstimatedExitCosts
```

This is not a prediction of exact loss.

It is a conservative sizing allowance.

---

# 20. Effective Loss Per Unit

Canonical V1 sizing uses:

```text
EffectiveLossPerUnit
=
PlannedLossPerUnit
+
ExecutionAllowancePerUnit
```

This is more conservative than sizing only to the visible stop distance.

---

# 21. Stress Loss Per Unit

A separate stress value should estimate a worse but plausible exit:

```text
StressLossPerUnit
=
abs(EntryPrice - StressExitPrice)
+
StressCostsPerUnit
```

Stress assumptions may use historical slippage quantiles, gap-like jumps, spread expansion, or liquidity degradation.

Exact construction belongs to DD-06/DD-07 research.

---

# 22. Three Risk Views

Every proposed trade should therefore expose:

```text
NominalRisk
ExecutionAdjustedRisk
StressRisk
```

All three are useful.

The final trade may be rejected even when nominal risk is acceptable if stress risk is excessive.

---

# 23. Risk Budget Is a Ceiling, Not a Target

A configured maximum risk per trade means:

> The trade may not exceed this amount.

It does not mean:

> Every valid trade should consume this amount.

This is consistent with RDT contributor plans that treat per-trade limits as maximums rather than mandatory size.

---

# 24. No Universal Percentage Is Assumed

RDT explicitly challenges simplistic universal rules such as "always risk 1–2%."

Edge Relative therefore does not hard-code a universal risk fraction in DD-03.

Instead:

```text
base_trade_risk_fraction
```

is a versioned policy parameter that must be validated under realistic backtests and drawdown analysis.

---

# 25. RDT Numerical Examples Are Not Canonical Limits

The Wiki includes community plans using values around:

- approximately 1% maximum risk per trade;
- approximately 3% maximum daily loss;
- volatility- and confidence-adjusted position sizes.

DD-03 treats these as examples of concrete implementation, not as platform defaults.

---

# 26. Risk Reference Equity

Risk percentages need a stable denominator.

Define:

```text
RiskReferenceEquity
```

as the account equity snapshot used to calculate risk budgets for the trading session.

For V1, the recommended default is:

```text
RiskReferenceEquity = StartOfSessionNetLiquidationValue
```

subject to reconciliation before trading begins.

---

# 27. No Intraday Profit Compounding

Unrealized or realized profits during the session do not automatically increase RiskReferenceEquity.

Therefore:

```text
Profitable morning
    X→ larger risk ceiling in afternoon
```

unless an explicit policy revision occurs.

This prevents the system from progressively increasing exposure simply because the day is currently profitable.

---

# 28. Intraday Losses Do Reduce Permission

Although profits do not automatically expand the budget, losses must reduce remaining risk capacity.

Risk permission is asymmetric:

```text
Profit does not automatically increase limits.
Loss can reduce or eliminate limits immediately.
```

---

# 29. Risk Reference Equity Updates

RiskReferenceEquity may be recalculated:

- at the next trading session;
- after deposits/withdrawals;
- after authoritative broker reconciliation;
- after deliberate policy reset.

Every change must be auditable.

---

# 30. Account Risk Vocabulary

DD-03 defines:

```text
RiskReferenceEquity
CurrentNetLiquidationValue
AvailableCash
BuyingPower
MarginUsed
GrossExposure
NetExposure
OpenRisk
StressOpenRisk
RealizedSessionPnL
UnrealizedPnL
SessionDrawdown
```

These values must not be conflated.

---

# 31. Gross Exposure

For equities:

```text
GrossExposure
=
Σ abs(position_notional)
```

Gross exposure measures total directional capital deployed without netting longs and shorts.

---

# 32. Net Exposure

For equities:

```text
NetExposure
=
Σ signed_position_notional
```

Long notional is positive.

Short notional is negative.

---

# 33. Open Risk

For each position:

```text
PositionOpenRisk
=
Quantity × EffectiveLossPerUnitFromCurrentRiskBoundary
```

Portfolio open risk is:

```text
PortfolioOpenRisk
=
Σ PositionOpenRisk
```

This is a planned-loss view, not a complete tail-risk model.

---

# 34. Stress Open Risk

Similarly:

```text
PortfolioStressOpenRisk
=
Σ PositionStressRisk
```

Additional correlated or market-wide stress scenarios may be evaluated separately.

---

# 35. Session Drawdown

For risk-control purposes:

```text
SessionDrawdown
=
max(0,
    RiskReferenceEquity - CurrentNetLiquidationValue)
```

This includes both realized and unrealized losses.

A system must not ignore a large unrealized loss merely because it has not yet been realized.

---

# 36. Realized Loss Tracking

Track separately:

```text
SessionRealizedLoss
StrategyRealizedLoss
SymbolRealizedLoss
SectorRealizedLoss
```

These are useful for risk throttles and diagnostics.

---

# 37. Risk Budget Hierarchy

A candidate trade is constrained by multiple budgets:

```text
Account
  ↓
Session
  ↓
Portfolio
  ↓
Strategy
  ↓
Sector / Correlation Cluster
  ↓
Symbol
  ↓
Trade
```

The strictest applicable constraint wins.

---

# 38. Maximum Trade Risk Budget

Define:

```text
TradeRiskCeiling
=
RiskReferenceEquity × configured_trade_risk_fraction
```

The fraction may vary by:

- strategy;
- deployment stage;
- trading mode;
- instrument class;
- risk state.

---

# 39. Absolute Trade Risk Ceiling

Policy may also define:

```text
absolute_trade_risk_cap
```

Then:

```text
TradeRiskCeiling
=
min(
  percent_based_ceiling,
  absolute_trade_risk_cap
)
```

where both are configured.

---

# 40. Strategy-Specific Risk Ceiling

Each strategy version may have a lower maximum risk.

Example conceptually:

```text
Global ceiling      = 0.50% equity
Strategy ceiling    = 0.30% equity

Effective ceiling   = 0.30% equity
```

The numerical values are illustrative only.

---

# 41. Deployment-Stage Risk Ceiling

Risk should depend on maturity.

Possible stages:

```text
BACKTEST
PAPER
ASSISTED_LIVE
LIVE_LIMITED
PRODUCTION
```

A newly promoted live strategy may have a substantially lower ceiling than a mature production strategy.

---

# 42. Trading-Mode Risk Behavior

## Research / Backtest

No real-capital risk authority.

## Paper

Apply production-like risk rules to validate behavior.

## Assisted Live

Real risk limits apply; entry requires human approval.

## Guarded Autopilot

Real risk limits apply automatically.

---

# 43. Risk State Multiplier

The current account risk state may reduce the base trade ceiling.

Conceptually:

```text
EffectiveTradeRiskCeiling
=
BaseTradeRiskCeiling × RiskStateMultiplier
```

The multiplier is never allowed to exceed the configured hard maximum.

---

# 44. Canonical Risk States

Use:

```text
NORMAL
REDUCED_1
REDUCED_2
NO_NEW_RISK
FLATTEN_ONLY
HALTED
```

These states are deterministic and auditable.

---

# 45. NORMAL

Normal policy applies.

No drawdown or infrastructure condition requires de-risking.

---

# 46. REDUCED_1

New-trade risk is reduced.

Existing positions remain managed according to their plans unless another rule requires reduction.

---

# 47. REDUCED_2

New-trade risk is reduced more aggressively.

Additional restrictions may include:

- fewer simultaneous positions;
- tighter portfolio open-risk budget;
- stronger setup-quality requirement.

---

# 48. NO_NEW_RISK

No new exposure may be opened.

Existing positions may be:

- held;
- reduced;
- exited;
- protected.

Actions that increase risk are prohibited.

---

# 49. FLATTEN_ONLY

Only risk-reducing actions are permitted.

The objective is to reach zero controlled exposure.

---

# 50. HALTED

Automated execution authority is disabled.

Trading may resume only after explicit recovery conditions are satisfied.

---

# 51. Position Sizing Core Formula

For V1 equities:

```text
RiskSizedQuantity
=
floor(
  EffectiveTradeRiskBudget
  /
  EffectiveLossPerUnit
)
```

If the result is less than one share:

```text
REJECT: INSUFFICIENT_RISK_CAPACITY
```

---

# 52. Risk Budget Available to Candidate

Conceptually:

```text
EffectiveTradeRiskBudget
=
min(
  trade_ceiling,
  remaining_session_risk_capacity,
  remaining_portfolio_open_risk_capacity,
  remaining_strategy_risk_capacity,
  remaining_symbol_risk_capacity,
  remaining_sector_or_cluster_capacity
)
```

Other constraints can reduce the final quantity further.

---

# 53. Capital-Sized Quantity

Risk distance alone can produce pathological quantities when stops are very tight.

Therefore define:

```text
CapitalSizedQuantity
=
floor(
  MaxAllowedPositionNotional
  /
  EntryPrice
)
```

---

# 54. Gross-Exposure-Sized Quantity

The proposed trade must fit inside remaining gross exposure.

```text
GrossExposureQuantity
=
floor(
  RemainingGrossExposureCapacity
  /
  EntryPrice
)
```

---

# 55. Net-Exposure-Sized Quantity

Directional portfolio limits may further reduce the position.

The calculation depends on whether the new position increases or reduces current directional exposure.

---

# 56. Liquidity-Sized Quantity

Define:

```text
LiquiditySizedQuantity
```

as the largest quantity that can be entered and exited without exceeding configured liquidity or market-impact limits.

Inputs may include:

- same-time intraday volume;
- recent traded volume;
- spread;
- available depth where reliable;
- average trade size;
- historical slippage.

Thresholds are empirical.

---

# 57. Final Equity Quantity

Canonical V1 quantity is:

```text
FinalQuantity
=
min(
  RiskSizedQuantity,
  CapitalSizedQuantity,
  GrossExposureSizedQuantity,
  NetExposureSizedQuantity,
  LiquiditySizedQuantity,
  SymbolConcentrationQuantity,
  SectorConcentrationQuantity,
  CorrelationQuantity,
  BrokerPermittedQuantity
)
```

Then round down to valid lot/instrument constraints.

---

# 58. Never Round Risk Up

If quantity must be rounded:

> Always round toward lower risk.

Do not round upward merely to meet a convenient lot or notional target.

---

# 59. Maximum Position Notional

Even when stop risk is small, each position may be limited to a maximum percentage or absolute amount of account equity.

This prevents:

```text
Tiny stop
+
large account
=
pathologically large position
```

---

# 60. Maximum Symbol Concentration

Policy must support:

```text
max_symbol_notional_fraction
max_symbol_open_risk_fraction
max_symbol_session_loss
```

A symbol can consume risk through multiple trades, not just one open position.

---

# 61. Repeated Trades in One Symbol

Repeated valid setups can create hidden concentration.

Track:

```text
symbol_realized_loss_today
symbol_risk_consumed_today
symbol_trade_count_today
```

A re-entry is not automatically entitled to a fresh unlimited budget.

---

# 62. Re-Entry Is a New Trade

When a stopped or invalidated trade later qualifies again:

- DD-02 must revalidate the setup;
- DD-03 must issue a new RiskDecision;
- the previous loss remains part of session/symbol budgets.

No automatic revenge re-entry exists.

---

# 63. Strategy Risk Budget

Track aggregate risk consumed by each strategy version.

This supports:

- live-limited rollouts;
- strategy drawdown controls;
- strategy-specific failure containment.

---

# 64. Maximum Simultaneous Positions

Policy should define a maximum number of open positions.

The limit exists because:

- correlated exposure becomes harder to reason about;
- operational management complexity increases;
- broker/API failure blast radius increases.

The V1 numerical limit remains empirical/configurable.

---

# 65. Portfolio Risk Is Not the Sum of Independent Bets

DD-01 explicitly warns that multiple related positions can represent one effective bet.

Example:

```text
HDFCBANK LONG
ICICIBANK LONG
AXISBANK LONG
KOTAKBANK LONG
SBIN LONG
```

is not safely interpreted as five independent hypotheses.

---

# 66. Sector Exposure

For every open position, map the instrument to a canonical sector.

Track:

```text
sector_gross_exposure
sector_net_exposure
sector_open_risk
sector_stress_risk
```

---

# 67. Sector Concentration Limit

Policy should cap:

```text
max_sector_notional
max_sector_open_risk
max_sector_directional_risk
```

The strictest limit wins.

---

# 68. Sector Limit Is a V1 Correlation Proxy

Sector classification is an imperfect but deterministic first-order proxy for common-factor risk.

V1 should use sector limits even before a more sophisticated correlation model is trusted.

---

# 69. Pairwise Correlation Model

A later or parallel V1 risk feature may compute rolling return correlations between watchlist stocks.

Inputs must be point-in-time correct.

Potential outputs:

```text
corr_5m
corr_15m
corr_daily
```

Exact windows and thresholds are research parameters.

---

# 70. Correlation Cluster

Positions may be grouped into a risk cluster when they share:

- sector;
- highly correlated recent returns;
- common index sensitivity;
- common underlying economic exposure.

Cluster membership must be deterministic and versioned.

---

# 71. Correlation Penalty

When a proposed position strongly overlaps existing exposure, its maximum risk may be reduced.

Conceptually:

```text
CorrelationAdjustedBudget
=
BaseBudget × CorrelationModifier
```

where:

```text
0 < CorrelationModifier <= 1
```

No correlation rule may increase risk above the unadjusted ceiling.

---

# 72. Conservative V1 Correlation Policy

Until correlation sizing is validated:

> Use hard sector concentration limits and treat statistical correlation only as an additional reducer, not as a mechanism for increasing exposure.

---

# 73. Market Factor Exposure

Most stocks have common sensitivity to the broad market.

Track approximate portfolio directional sensitivity to the market benchmark.

A simple V1 representation may begin with net notional exposure.

A later version may use rolling beta/factor sensitivity.

---

# 74. Long/Short Balance

A portfolio containing longs and shorts may have low net notional but high gross risk.

Therefore:

```text
Low Net Exposure
≠
Low Risk
```

Gross exposure and open risk remain independently constrained.

---

# 75. Short Risk Is Not Assumed Identical to Long Risk

DD-02 supports both long and short setups.

DD-03 allows direction-specific constraints because short trades can differ in:

- broker availability;
- borrow/segment rules;
- margin;
- execution behavior;
- gap characteristics;
- liquidity.

Actual NSE/broker constraints are provided by capability/configuration layers.

---

# 76. Buying Power Is a Constraint, Not a Risk Budget

Available buying power does not determine what the system should risk.

It merely constrains what can be funded.

Therefore:

```text
High Buying Power
X→ permission to use high risk
```

---

# 77. Margin Constraint

Before approval, the risk engine must verify that projected margin usage remains within configured safety limits.

Do not consume 100% of available broker margin merely because the broker permits it.

---

# 78. Margin Safety Buffer

Maintain configurable unused margin capacity to absorb:

- mark-to-market changes;
- margin recalculation;
- broker changes;
- execution differences;
- other open positions.

Exact buffer size is a policy parameter.

---

# 79. Market Regime Affects Risk

RDT emphasizes that market condition changes how aggressively positions should be managed.

DD-02 already classifies market state.

DD-03 consumes that classification through a risk modifier or prohibition.

---

# 80. Trend Regime

In a strong, aligned trend regime, standard risk limits may apply.

This does not automatically justify exceeding the global risk ceiling.

---

# 81. Neutral / Choppy Regime

In neutral or choppy conditions, the risk engine may:

- reduce trade-risk ceilings;
- reduce simultaneous positions;
- reduce sector concentration;
- prohibit pyramiding;
- require stronger setup quality.

Exact behavior must be empirically validated.

---

# 82. Dislocated Regime

If DD-02 labels the market `DISLOCATED`, V1 should normally reject new exposure.

Risk state becomes at least:

```text
NO_NEW_RISK
```

unless another system-safety rule requires flattening.

---

# 83. Confidence and Position Size — Source Principle

RDT describes position sizing as the final brush stroke after evaluating market, stock, setup, and context.

It also describes experienced traders changing size with confidence and volatility.

Edge Relative preserves the principle but does not convert subjective confidence directly into production leverage without evidence.

---

# 84. Deterministic Quality Modifier

A future version may map validated setup-quality buckets to risk multipliers.

Example structure:

```text
A+ setup → modifier_a
A setup  → modifier_b
B setup  → modifier_c
```

All multipliers remain bounded by hard ceilings.

The mapping is a research hypothesis, not a V1 assumption.

---

# 85. V1 Default on Setup-Quality Sizing

Initial conservative behavior:

> Setup quality may rank opportunities, but all qualifying V1 trades use the same base risk policy unless backtests demonstrate robust benefit from differentiated sizing.

This prevents arbitrary confidence from becoming hidden leverage.

---

# 86. ML Risk Authority

ML has no risk-sizing authority initially.

Default:

```text
MLRiskModifier = 1.0
MLRiskAuthority = DISABLED
```

---

# 87. Future ML Risk Modifier

After DD-09 validation, ML may influence risk within deterministic boundaries.

Conceptually:

```text
MLAdjustedBudget
=
DeterministicBaseBudget × MLRiskModifier
```

Then apply all hard limits again.

---

# 88. ML Cannot Override Hard Ceilings

Invariant:

```text
FinalRisk
<=
All Deterministic Hard Limits
```

regardless of model confidence.

---

# 89. ML Can Be More Conservative

ML may eventually reduce risk or reject an otherwise valid trade when authorized.

This is consistent with DD-01's asymmetric ML authority.

---

# 90. Probability and Reward/Risk

RDT rejects interpreting reward/risk without probability.

Edge Relative formalizes expectancy as:

```text
EV
=
P(win) × AvgWin
-
P(loss) × AvgLoss
-
ExpectedCosts
```

A nominal 3:1 trade is not automatically superior to a 1:1 trade.

---

# 91. Structural Reward/Risk Is Descriptive

DD-02 computes:

```text
StructuralRR
=
RewardDistance / RiskDistance
```

DD-03 records it.

It is not automatically a universal hard gate.

---

# 92. Break-Even Probability

Ignoring costs, if average win is `W` and average loss is `L`:

```text
BreakEvenWinRate
=
L / (W + L)
```

Costs increase the required win rate.

This is useful for evaluating strategy families and risk policies.

---

# 93. Setup-Specific Historical Probability

RDT repeatedly recommends using one's journal/history to understand how often a setup succeeds.

Edge Relative should eventually use point-in-time historical data grouped by:

- strategy version;
- setup family;
- market regime;
- direction;
- stock/sector context;
- relevant quality bucket.

---

# 94. Expectancy Gate

Once sufficient independent data exists, DD-03 may reject trades whose conservative expected value after costs is non-positive.

Preferred structure:

```text
LowerConfidenceBound(EV_after_costs) > 0
```

The statistical method belongs to DD-06.

---

# 95. No Fake Precision

If a setup has insufficient sample size, the engine must not pretend to know its exact win probability.

Possible state:

```text
EXPECTANCY_UNKNOWN
```

Unknown expectancy may require lower risk or no live authority depending on deployment stage.

---

# 96. Win Rate Is Not the Sole Risk Metric

RDT emphasizes high win rate for consistency.

Edge Relative tracks it but also requires:

- average win;
- average loss;
- expectancy;
- profit factor;
- drawdown;
- tail loss;
- slippage;
- exposure.

A high win rate with catastrophic rare losses is unacceptable.

---

# 97. Position Sizing Cannot Rescue a Bad Strategy

RDT explicitly states that traders should work on systematic trade selection before obsessing over sizing.

Edge Relative invariant:

> A strategy without validated positive expectancy does not earn live risk merely because a sophisticated sizing formula exists.

---

# 98. V1 Averaging Down Policy

RDT discusses averaging down only as an advanced, context-dependent technique and explicitly warns that most traders will misuse it.

For `ER_RS_CONTINUATION_V1`:

```text
AVERAGING_DOWN = PROHIBITED
```

---

# 99. Definition of Averaging Down

For a long position, adding quantity at a lower price while the same trade thesis remains open is averaging down.

For a short position, adding quantity at a higher price while the same thesis remains open is the symmetric case.

V1 prohibits both.

---

# 100. Why V1 Prohibits Averaging Down

Automation should not transform an invalid or deteriorating thesis into a larger position merely because price moved adversely.

The prohibition simplifies:

- risk accounting;
- strategy attribution;
- drawdown control;
- failure analysis.

---

# 101. New Setup After Loss Is Not Averaging Down

If the original trade is fully closed and a later independent DD-02 setup becomes valid, it may be treated as a new trade.

It still consumes symbol/session risk budget and must pass all controls again.

---

# 102. Adding to Winners — RDT Principle

RDT strongly encourages learning to add to winners in favorable trend conditions rather than reflexively cutting profitable positions.

Edge Relative preserves this as a research direction.

---

# 103. V1 Pyramiding Policy

Initial production behavior:

```text
PYRAMIDING = DISABLED
```

unless explicitly enabled for a validated strategy version.

---

# 104. Pyramiding Research Model

A future strategy may add only when:

- the original position is not structurally impaired;
- DD-02 produces a valid continuation/add trigger;
- market regime remains compatible;
- total post-add risk is re-evaluated;
- portfolio constraints still pass.

---

# 105. Every Add Requires a New Risk Decision

No blind scaling.

For each proposed add:

```text
CurrentPosition
+
ProposedAdd
+
UpdatedStopStructure
    ↓
Full DD-03 evaluation
```

---

# 106. Risk After Adding

After a proposed add, recompute:

```text
weighted_average_entry
current_quantity
new_quantity
protective_stop
open_risk
stress_risk
sector_risk
portfolio_risk
```

The add is rejected if any ceiling is violated.

---

# 107. Risk Freed by Stop Advancement

A profitable trade whose protective boundary has advanced may consume less open risk.

That released risk capacity may be available to the portfolio only according to explicit policy.

Do not automatically recycle every rupee of reduced open risk into new trades.

---

# 108. No Risk Increase From Unrealized Profit Alone

A higher market price does not by itself justify adding.

A new deterministic setup/continuation condition must exist.

---

# 109. Partial Reduction

Risk may require reducing an existing position without declaring the strategy thesis invalid.

Examples:

- portfolio concentration increases;
- market state degrades;
- risk state becomes REDUCED;
- liquidity deteriorates;
- operational confidence decreases.

The reduction reason must be recorded as a risk action, distinct from strategy exit.

---

# 110. Risk May Override HOLD

If DD-02 says:

```text
HOLD
```

but DD-03 requires:

```text
REDUCE
```

or:

```text
EXIT
```

DD-03 wins.

A lower authority cannot override a higher risk prohibition.

---

# 111. Drawdown Is Not Automatically Thesis Failure

RDT makes a useful distinction:

```text
Position Underwater
≠
Trade Thesis Invalid
```

Edge Relative preserves this distinction.

However, portfolio/account drawdown may still force risk reduction even while an individual thesis remains valid.

---

# 112. Trade-Level Adverse Excursion

Track:

```text
MAE
MFE
MAE_R
MFE_R
```

for every position.

This data is required to determine whether protective buffers and sizing assumptions are realistic.

---

# 113. Session-Level Drawdown Controls

Risk policy must support:

```text
daily_drawdown_warn
daily_drawdown_reduce_1
daily_drawdown_reduce_2
daily_drawdown_stop_new
daily_drawdown_flatten
```

Not every deployment needs every threshold enabled.

---

# 114. Progressive De-Risking

DD-01 explicitly anticipates progressive reduction before a full stop.

Preferred model:

```text
NORMAL
  ↓
REDUCED_1
  ↓
REDUCED_2
  ↓
NO_NEW_RISK
  ↓
FLATTEN_ONLY / HALTED
```

This is preferable to remaining at full risk until one cliff threshold is hit.

---

# 115. Daily Loss Threshold Values

DD-03 does not hard-code a percentage.

A 3% daily-loss threshold appears in RDT community trading-plan examples, but it is not treated as universal RDT doctrine.

Edge Relative must calibrate its own thresholds using realistic drawdown distributions and risk-of-ruin analysis.

---

# 116. Weekly Drawdown

Track equity relative to the week's reference equity.

Policy may reduce risk after sustained weekly drawdown even if no individual day hits the daily stop.

---

# 117. Monthly Drawdown

Track equity relative to a monthly or rolling high-water reference.

Monthly drawdown may trigger:

- lower risk ceiling;
- strategy review;
- live-to-paper downgrade;
- temporary suspension.

---

# 118. Account Drawdown

Track drawdown from account high-water mark:

```text
AccountDrawdown
=
(HighWaterEquity - CurrentEquity)
/
HighWaterEquity
```

This is distinct from daily/weekly loss.

---

# 119. Strategy Drawdown

Each strategy version maintains its own cumulative performance and drawdown state.

A failing strategy can be disabled without necessarily halting unrelated strategies.

---

# 120. Symbol Drawdown / Loss Streak

Repeated losses in one symbol may indicate:

- regime change;
- temporary stock-specific behavior;
- poor data;
- setup incompatibility.

Policy may reduce or stop new entries for that symbol after excessive session loss.

---

# 121. Consecutive Losses

DD-01 requires consecutive-loss controls.

Track consecutive losses by:

- account;
- strategy;
- symbol;
- setup family.

A loss streak is a risk signal, not proof the next trade will lose.

---

# 122. Loss-Streak Response

Possible responses:

```text
NONE
REDUCE_RISK
REQUIRE_HIGHER_QUALITY
NO_NEW_RISK
REVIEW_REQUIRED
```

Thresholds must be calibrated against the strategy's expected loss-streak distribution.

---

# 123. Avoid Gambler's-Fallacy Sizing

The system must never increase size merely because several losses have occurred and a win is "due."

Similarly, it must not reduce size arbitrarily after an expected loss streak unless policy specifies a validated drawdown response.

---

# 124. Risk of Ruin

DD-06 should use Monte Carlo / resampling to estimate:

- probability of severe drawdown;
- probability of hitting risk stops;
- loss-streak distributions;
- capital requirements;
- recovery time.

DD-03 consumes validated policy outputs from that research.

---

# 125. Tail Loss

Average loss is insufficient.

Track:

```text
p95_loss
p99_loss
worst_loss
expected_shortfall
```

where sample size supports interpretation.

---

# 126. Stop Slippage Distribution

For live/paper execution, record:

```text
planned_stop
trigger_price
actual_fill_price
slippage
```

This distribution feeds future `ExecutionAllowancePerUnit` and stress models.

---

# 127. Gap Risk

Although V1 is intraday-only, abrupt price jumps can occur intraday.

Stress models must allow price to move through the protective level.

Risk cannot assume continuous prices.

---

# 128. Overnight Risk

`ER_RS_CONTINUATION_V1` must flatten before the configured session cutoff.

Therefore overnight gap risk is prohibited by strategy scope.

If a future strategy permits overnight positions, DD-03 requires a separate overnight-risk model.

---

# 129. End-of-Session Risk

The risk engine must enforce V1 flattening before market close.

If normal exit logic has not closed the position by the configured cutoff:

```text
RISK_EXIT: SESSION_FLATTEN
```

---

# 130. Event Risk

DD-02 supplies event-risk state.

For `BLOCKED` events:

```text
new_trade_permission = false
```

For `UNKNOWN`, initial live policy should be conservative and configurable.

---

# 131. Liquidity Risk Is Separate From Strategy Quality

An excellent setup may still be untradeable.

Risk rejects trades when expected exit liquidity is insufficient.

The strategy does not get to override this because RRS/RVOL are strong.

---

# 132. Spread Risk

Wide or unstable spread increases:

- entry cost;
- exit cost;
- stop slippage;
- mark-to-market noise.

Spread constraints may reduce quantity or reject the trade.

---

# 133. Market Impact

A position may be too large even in a liquid stock if Edge Relative's order is a meaningful fraction of recent volume/depth.

The system should bound participation rate.

Exact values belong to execution research.

---

# 134. Data Quality Risk

If required price, volume, benchmark, or position data is stale or untrusted:

```text
UNKNOWN RISK = NO NEW TRADE
```

The system must not estimate around missing authoritative data when money is at risk.

---

# 135. Broker Health Risk

New exposure must be blocked when broker state is materially degraded.

Examples:

- authentication failure;
- repeated API timeouts;
- order status ambiguity;
- stale order-update stream;
- severe rate limiting.

---

# 136. Reconciliation Risk

If internal positions differ from broker positions:

```text
risk_state >= NO_NEW_RISK
```

If the mismatch could imply uncontrolled exposure:

```text
risk_state = FLATTEN_ONLY or HALTED
```

according to recovery policy.

---

# 137. Unknown Order State

An order in `UNKNOWN` state consumes conservative risk capacity as though it may have filled until reconciliation proves otherwise.

Never assume a timed-out submission failed.

---

# 138. Duplicate-Order Risk

Duplicate execution can double exposure unexpectedly.

Risk authority must integrate with idempotency/fencing controls from DD-08.

If duplicate-order protection becomes unreliable:

```text
NO_NEW_RISK
```

or stronger action is required.

---

# 139. Single Execution Authority

DD-04A requires one active live execution authority until robust fencing exists.

DD-03 treats violation of that invariant as an immediate critical risk event.

---

# 140. Infrastructure Risk Can Only Reduce Permission

Infrastructure health is asymmetric.

Good infrastructure does not justify larger trading risk.

Bad infrastructure may reduce or eliminate trading authority.

---

# 141. Circuit Breaker Categories

Circuit breakers may be triggered by:

- daily drawdown;
- abnormal slippage;
- repeated order rejection;
- stale market data;
- broker degradation;
- reconciliation mismatch;
- impossible prices;
- strategy anomaly;
- risk-service failure.

---

# 142. Stop New Trades

First-level safety action:

```text
NO_NEW_RISK
```

Existing positions remain protected and managed.

---

# 143. Cancel Pending Entries

When risk state prohibits new exposure, all pending entry orders that could increase exposure should be cancelled where safely possible.

Protective exit orders must not be cancelled accidentally.

---

# 144. Reduce Exposure

A risk event may require deterministic reduction rather than immediate flattening.

Reduction priority may consider:

- weakest current thesis;
- highest marginal risk;
- highest correlation concentration;
- worst liquidity;
- largest stress contribution.

Exact liquidation priority is a future research/implementation decision.

---

# 145. Flatten Controlled Positions

A stronger circuit breaker may require exiting all positions under Edge Relative control.

The system must:

```text
Cancel conflicting entries
    ↓
Submit exits
    ↓
Track partial fills
    ↓
Confirm flat
    ↓
Reconcile broker
```

---

# 146. Emergency Halt

The strongest control disables automated execution after attempting required protective actions.

Recovery requires explicit state validation.

---

# 147. Manual Risk Controls

The operator may:

- stop new trades;
- reduce a position;
- exit a position;
- cancel pending entries;
- disable a strategy;
- disable broker execution;
- emergency flatten.

These are risk-reducing controls.

---

# 148. Manual Override Cannot Bypass Hard Risk Limits

A UI action must not allow an operator to click through a hard risk rejection during normal operation.

Increasing a hard limit requires an explicit versioned policy/configuration change with audit trail.

This prevents an "override" button from nullifying the risk architecture.

---

# 149. Risk Policy Versioning

Every decision references:

```text
risk_policy_id
risk_policy_version
```

Historical decisions must remain reproducible under the policy in force at that time.

---

# 150. Immutable Risk Decision

Once a RiskDecision authorizes a trade intent, preserve it.

Subsequent changes create new events/decisions rather than rewriting history.

---

# 151. RiskDecision Inputs

Minimum conceptual input:

```text
RiskDecisionRequest

trade_candidate_id
strategy_id
strategy_version
instrument_id
direction
proposed_entry
structural_invalidation
protective_stop_reference
target_reference
market_regime
sector_id
setup_quality
liquidity_state
event_risk_state

risk_reference_equity
current_equity
available_cash
buying_power
margin_state
open_positions
pending_orders
portfolio_open_risk
portfolio_stress_risk
session_pnl
session_drawdown
risk_state
broker_health
data_health
reconciliation_state
```

---

# 152. RiskDecision Outputs

Canonical output:

```text
RiskDecision

APPROVE
REDUCE
REJECT
EXIT_REQUIRED
HALT_REQUIRED
```

with structured details.

---

# 153. APPROVE

Trade may proceed up to:

```text
approved_quantity
approved_risk
approved_notional
```

Execution may use less, never more.

---

# 154. REDUCE

The candidate is valid but requested/proposed quantity exceeds risk capacity.

The engine returns a smaller approved maximum.

---

# 155. REJECT

No new position may be established.

A reason code is mandatory.

---

# 156. EXIT_REQUIRED

An existing position must be reduced to zero or to a specified safer quantity because risk authority has changed.

---

# 157. HALT_REQUIRED

The system must stop automated trading authority and enter the appropriate recovery workflow.

---

# 158. Core Rejection Reason Codes

Initial reason taxonomy:

```text
RISK_STATE_BLOCKED
DAILY_DRAWDOWN_LIMIT
WEEKLY_DRAWDOWN_LIMIT
MONTHLY_DRAWDOWN_LIMIT
ACCOUNT_DRAWDOWN_LIMIT
STRATEGY_DRAWDOWN_LIMIT
CONSECUTIVE_LOSS_LIMIT
TRADE_RISK_LIMIT
PORTFOLIO_OPEN_RISK_LIMIT
PORTFOLIO_STRESS_RISK_LIMIT
GROSS_EXPOSURE_LIMIT
NET_EXPOSURE_LIMIT
SYMBOL_CONCENTRATION_LIMIT
SECTOR_CONCENTRATION_LIMIT
CORRELATION_LIMIT
MAX_POSITIONS_LIMIT
LIQUIDITY_LIMIT
SPREAD_LIMIT
MARGIN_LIMIT
BUYING_POWER_LIMIT
EVENT_RISK_BLOCKED
DATA_QUALITY_BLOCKED
BROKER_HEALTH_BLOCKED
RECONCILIATION_MISMATCH
ORDER_STATE_UNKNOWN
INVALID_STOP_DISTANCE
INSUFFICIENT_RISK_CAPACITY
SESSION_FLATTEN_WINDOW
COMPLIANCE_BLOCKED
```

---

# 159. Risk Adjustment Reason Codes

Examples:

```text
RISK_REDUCED_DRAWDOWN
RISK_REDUCED_MARKET_REGIME
RISK_REDUCED_SECTOR_CONCENTRATION
RISK_REDUCED_CORRELATION
RISK_REDUCED_LIQUIDITY
RISK_REDUCED_MARGIN
RISK_REDUCED_DEPLOYMENT_STAGE
RISK_REDUCED_ML
RISK_REDUCED_MANUAL
```

---

# 160. RiskDecision Lineage

Every executed order should be traceable through:

```text
FeatureSnapshot
    ↓
SetupObservation
    ↓
TradeCandidate
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

---

# 161. Risk Snapshot

At each material decision, store a point-in-time risk snapshot containing:

- account equity;
- open risk;
- stress risk;
- gross/net exposure;
- sector exposure;
- margin;
- risk state;
- drawdowns;
- relevant limits;
- remaining capacities.

This enables exact post-trade explanation.

---

# 162. Explainability

The UI should be able to say:

```text
Strategy:
VALID LONG

Risk:
Base trade ceiling: ₹X
Reduced by sector concentration: ₹Y
Reduced by remaining daily capacity: ₹Z
Final permitted risk: ₹A
Stop-adjusted loss/share: ₹B
Approved quantity: N
```

No opaque "risk score" should replace this explanation.

---

# 163. Position Risk Recalculation

Open positions must be re-evaluated when:

- stop changes;
- quantity changes;
- price gaps materially;
- liquidity deteriorates;
- market regime changes;
- portfolio changes;
- broker/margin state changes;
- risk state changes.

---

# 164. Risk Is Event-Driven

Do not calculate risk only at entry.

Risk changes throughout the position lifecycle.

The system should react to material events rather than wait for periodic manual review.

---

# 165. Risk Budget Reservation

Once an order is approved and submitted, reserve risk capacity for the unfilled quantity conservatively.

Otherwise multiple simultaneous orders could each assume the same remaining budget.

---

# 166. Partial Fill Risk

After a partial fill:

- filled quantity consumes position risk;
- remaining live order consumes reserved risk;
- cancelled quantity releases reserved risk only after cancellation is authoritative.

---

# 167. Cancel Ambiguity

If cancellation status is uncertain, continue reserving the relevant risk until broker reconciliation confirms the order cannot fill.

---

# 168. Risk Reservation Release

Risk capacity may be released when:

- order is definitively cancelled/rejected;
- position is definitively reduced/closed;
- protective stop advances and policy permits reuse;
- reconciliation confirms exposure no longer exists.

---

# 169. Portfolio Stress Scenarios

In addition to per-position stops, evaluate scenarios such as:

- broad market shock against net exposure;
- sector shock against concentrated sectors;
- spread widening;
- delayed stop execution;
- correlated stop-through events.

DD-06 determines scenario calibration.

---

# 170. Market Shock Stress

A simple initial scenario may apply a benchmark shock and estimated stock sensitivities.

The exact factor model is research work.

Until validated, conservative notional/open-risk caps remain primary controls.

---

# 171. Sector Shock Stress

For concentrated sectors, assume multiple positions can move adversely together.

This is why sector caps exist even when individual trade risks appear small.

---

# 172. Liquidity Shock Stress

Stress exits should consider:

- wider spread;
- reduced depth;
- larger slippage;
- delayed fill.

Paper/live simulation should collect evidence for these assumptions.

---

# 173. V1 Equity Risk Scope

The fully specified production sizing model in DD-03 applies first to:

> Intraday NSE cash equities used by `ER_RS_CONTINUATION_V1`.

This keeps the initial risk engine tractable.

---

# 174. Futures Risk — Future Extension

Futures require additional inputs:

```text
contract_multiplier
lot_size
margin_requirement
mark_to_market_behavior
basis
expiry
roll
price_limit_behavior
```

The same portfolio risk hierarchy remains, but loss-per-unit and margin semantics differ.

---

# 175. Options Risk — Future Extension

Options require explicit handling of:

- premium at risk;
- defined vs undefined loss;
- Delta;
- Gamma;
- Vega;
- Theta;
- implied volatility;
- spread liquidity;
- expiry;
- settlement/assignment mechanics;
- multi-leg execution.

---

# 176. Defined-Risk Option Structures

For a defined-risk spread, maximum theoretical loss can inform risk sizing.

However:

```text
TheoreticalMaxLoss
≠
CompleteOperationalRisk
```

because execution, legging, liquidity, and broker failures remain relevant.

---

# 177. Naked / Undefined-Risk Derivatives

Undefined-risk structures should not receive production authority without a dedicated risk design and explicit hard constraints.

They are outside V1 scope.

---

# 178. Expiry Risk

Future derivative policies must support:

- expiry-day restrictions;
- forced-exit windows;
- settlement controls;
- margin changes;
- instrument-specific prohibited periods.

---

# 179. Risk Policy Configuration

Risk parameters must be configuration-driven and versioned.

Conceptual groups:

```text
trade_limits
portfolio_limits
sector_limits
symbol_limits
correlation_limits
drawdown_limits
margin_limits
liquidity_limits
stress_parameters
operational_circuit_breakers
recovery_rules
```

---

# 180. Policy Immutability for Historical Decisions

Changing a risk parameter creates a new policy version.

Historical trades retain the old version.

Never silently reinterpret past risk decisions under current configuration.

---

# 181. Research vs Production Parameters

Each parameter should have a state:

```text
EXPERIMENTAL
VALIDATED
PAPER
LIVE_LIMITED
PRODUCTION
RETIRED
```

A parameter discovered through optimization does not become production immediately.

---

# 182. Backtest Requirements for Risk Rules

Risk-policy backtests must model:

- actual strategy entries/exits;
- transaction costs;
- slippage;
- partial fills where applicable;
- position concurrency;
- portfolio concentration;
- daily/weekly/monthly paths;
- realistic capital and margin.

Single-trade analysis is insufficient.

---

# 183. Walk-Forward Risk Validation

Risk thresholds should be tested chronologically.

Avoid tuning drawdown limits or risk fractions to the full historical sample and then reporting the same sample as evidence.

---

# 184. Monte Carlo Risk Validation

Trade-sequence resampling should estimate:

- loss streaks;
- drawdown distribution;
- risk-of-ruin-like outcomes;
- probability of circuit-breaker activation;
- capital recovery time.

---

# 185. Parameter Robustness

A viable risk policy should not depend on one magical value.

Example:

```text
0.42% risk works
0.40% fails
0.44% fails
```

would indicate instability or overfitting.

Prefer broad regions of acceptable behavior.

---

# 186. Risk Objective Function

Risk optimization should not maximize historical return alone.

Preferred objective considers:

```text
Expectancy after costs
+
Drawdown
+
Tail loss
+
Risk-adjusted return
+
Stability
+
Survival
```

---

# 187. Candidate Risk Fraction Research

Research should compare multiple base trade-risk fractions rather than assume a textbook number.

Metrics include:

- CAGR / net return where relevant;
- max drawdown;
- ulcer-like measures;
- expected shortfall;
- capital efficiency;
- stop frequency;
- probability of daily/weekly limits.

---

# 188. Daily Loss Policy Research

Test whether daily loss controls improve long-run risk-adjusted performance or merely truncate statistically normal recovery opportunities.

Possible policy families:

```text
hard stop only
progressive reduction
loss-streak-based reduction
regime-conditioned reduction
```

---

# 189. Correlation Policy Research

Compare:

```text
sector caps only
pairwise correlation reducer
factor/beta model
sector + correlation hybrid
```

The simplest robust model should win.

---

# 190. Pyramiding Research

Test RDT's adding-to-winners principle under NSE conditions.

Questions include:

- does adding in trend regimes improve expectancy after costs?;
- what continuation trigger is required?;
- does total drawdown rise disproportionately?;
- should risk be maintained constant by advancing stops?;
- which setup families benefit?

---

# 191. No-Averaging-Down Baseline

All initial backtests should use:

```text
AVERAGING_DOWN = OFF
```

Advanced averaging-down experiments, if ever conducted, must be isolated from the production baseline.

---

# 192. Risk Success Metrics

DD-01's risk success metrics are mandatory:

- maximum drawdown;
- tail loss;
- daily-loss events;
- risk violations;
- correlated exposure;
- risk-adjusted return.

DD-03 adds:

- planned vs actual loss;
- stop slippage;
- percentage of risk ceiling used;
- risk rejection rate;
- risk reduction rate;
- circuit-breaker activation rate;
- risk reservation accuracy.

---

# 193. Planned vs Actual Loss

For stopped/forced exits:

```text
LossError
=
ActualLoss - PlannedExecutionAdjustedRisk
```

Persistent positive error indicates underestimation of slippage/costs or failure in stop execution.

---

# 194. Risk Utilization

Track:

```text
actual_approved_risk / hard_trade_ceiling
```

by:

- strategy;
- setup;
- regime;
- stock;
- day.

This reveals whether the system routinely operates near maximum risk or rarely needs it.

---

# 195. Risk Rejection Analytics

Record every rejected valid trade and its reason.

Later compare its hypothetical outcome.

This answers:

> Did the risk rule protect capital, or did it unnecessarily suppress positive expectancy?

---

# 196. Risk-Reduction Analytics

For trades whose size was reduced, store:

- unconstrained risk quantity;
- final approved quantity;
- each binding constraint;
- hypothetical unconstrained outcome;
- actual constrained outcome.

This enables causal evaluation of risk rules.

---

# 197. Circuit-Breaker Analytics

For each activation, record:

```text
trigger
risk_state_before
risk_state_after
open_positions
pending_orders
account_equity
market_state
broker_state
recovery_time
counterfactual_outcome
```

---

# 198. Promotion Criteria

A risk policy may move toward live authority only when:

- backtests are point-in-time correct;
- transaction costs are realistic;
- results are robust across periods;
- drawdown is acceptable;
- parameter neighborhoods are stable;
- paper behavior matches expectations;
- operational controls have been fault-tested.

---

# 199. Paper Trading Parity

Paper trading must use the same:

```text
RiskReferenceEquity logic
RiskPolicy
Position sizing
Portfolio constraints
Drawdown state machine
RiskDecision contract
```

as live trading.

Only execution simulation differs.

---

# 200. Shadow Risk Decisions

Future risk-policy variants may run in shadow mode.

Example:

```text
Production RiskDecision:
APPROVE 120 shares

Shadow Policy V2:
APPROVE 80 shares
```

Later compare outcomes before changing capital authority.

---

# 201. Risk Recovery

After a HALTED or FLATTEN_ONLY event, trading must not automatically resume merely because time passed.

Recovery must prove required invariants.

---

# 202. Recovery Preconditions

Depending on trigger, require:

- broker connected;
- market data current;
- orders reconciled;
- positions reconciled;
- margin known;
- protective orders restored;
- data quality valid;
- risk policy loaded;
- single execution authority confirmed.

---

# 203. Drawdown Recovery

A drawdown-triggered halt may require:

- next-session reset;
- cooldown;
- manual review;
- reduced-risk re-entry;
- paper/shadow validation.

The exact rule is policy-configurable.

---

# 204. Strategy Drawdown Recovery

A disabled strategy should not automatically resume because account-level conditions improved.

Strategy-specific re-enable criteria must be satisfied.

---

# 205. Risk Service Failure

If the risk engine cannot produce a trustworthy decision:

```text
FAIL CLOSED
```

No new live trade is permitted.

---

# 206. Risk Data Staleness

Risk input freshness must be explicit.

Examples:

```text
position_age
quote_age
margin_age
broker_state_age
portfolio_snapshot_age
```

Excessive staleness blocks new exposure.

---

# 207. Atomicity of Risk Reservation and Trade Intent

Where practical, approval/reservation and authoritative trade intent should be recorded atomically before broker submission.

This prevents two concurrent candidates from overspending the same risk budget.

---

# 208. Concurrency Invariant

For any two simultaneous candidate trades:

> The sum of approved/reserved risk must never exceed the applicable portfolio/session capacity because of a race condition.

Implementation belongs to DD-04/DD-08, but the invariant belongs here.

---

# 209. Deterministic Replay

Given identical:

- account state;
- positions;
- pending orders;
- market state;
- TradeCandidate;
- RiskPolicy version;

DD-03 must generate the same RiskDecision.

---

# 210. Clock Discipline

Risk rules based on sessions, cooldowns, or daily resets must use the canonical trading clock and NSE calendar.

Never infer a new risk day from host-server midnight.

---

# 211. Risk Day

Define the risk day by exchange trading session.

All daily limits reset only according to explicit session lifecycle rules after reconciliation.

---

# 212. Pre-Market Risk Initialization

Before enabling new trades:

```text
Authenticate Broker
    ↓
Read Account / Margin
    ↓
Read Orders
    ↓
Read Positions
    ↓
Reconcile
    ↓
Set RiskReferenceEquity
    ↓
Load RiskPolicy
    ↓
Initialize Risk State
    ↓
Enable Trading
```

---

# 213. Session Risk Reporting

Real-time risk dashboard should display at minimum:

- RiskReferenceEquity;
- current equity;
- session drawdown;
- realized/unrealized P&L;
- open risk;
- stress risk;
- gross/net exposure;
- sector concentrations;
- margin usage;
- risk state;
- remaining daily capacity;
- active circuit breakers.

---

# 214. Per-Position Risk Display

For each position show:

- current quantity;
- average entry;
- structural invalidation;
- protective stop;
- planned risk;
- current open risk;
- stress risk;
- sector/cluster;
- binding risk constraints;
- risk exit status.

---

# 215. Alerts

Risk alerts include:

```text
RISK_STATE_REDUCED
RISK_STATE_NO_NEW_RISK
DAILY_DRAWDOWN_WARNING
PORTFOLIO_RISK_WARNING
SECTOR_CONCENTRATION_WARNING
MARGIN_WARNING
STOP_SLIPPAGE_WARNING
BROKER_RISK_WARNING
RECONCILIATION_RISK
FLATTEN_REQUIRED
RISK_HALTED
```

---

# 216. Risk Events Are Audit Events

Important risk actions are immutable audit events, not merely log messages.

Examples:

```text
RISK_DECISION_CREATED
RISK_RESERVATION_CREATED
RISK_RESERVATION_RELEASED
RISK_STATE_CHANGED
POSITION_REDUCTION_REQUIRED
CIRCUIT_BREAKER_TRIGGERED
KILL_SWITCH_ACTIVATED
RISK_POLICY_CHANGED
RISK_RECOVERY_COMPLETED
```

---

# 217. Canonical Equity Risk Algorithm

Conceptually:

```text
function evaluate(candidate, portfolio, policy):

    assert candidate.strategy_valid

    if compliance_blocked:
        REJECT

    if infrastructure_or_data_untrusted:
        REJECT / HALT

    if risk_state prohibits new risk:
        REJECT

    if candidate.event_risk == BLOCKED:
        REJECT

    loss_per_unit =
        abs(candidate.entry - candidate.protective_stop)
        + execution_allowance_per_unit

    if loss_per_unit <= 0:
        REJECT

    trade_budget = hard_trade_ceiling(policy)
    trade_budget = min(trade_budget, remaining_session_capacity)
    trade_budget = min(trade_budget, remaining_portfolio_capacity)
    trade_budget = min(trade_budget, remaining_strategy_capacity)
    trade_budget = min(trade_budget, remaining_symbol_capacity)
    trade_budget = min(trade_budget, remaining_sector_capacity)

    trade_budget *= risk_state_modifier
    trade_budget *= validated_regime_modifier
    trade_budget *= validated_quality_modifier
    trade_budget *= validated_ml_modifier

    risk_qty = floor(trade_budget / loss_per_unit)

    final_qty = min(
        risk_qty,
        capital_qty,
        liquidity_qty,
        gross_exposure_qty,
        net_exposure_qty,
        symbol_qty,
        sector_qty,
        correlation_qty,
        broker_qty
    )

    if final_qty < 1:
        REJECT

    stress_test(final_qty)

    if stress_limits_fail:
        REDUCE or REJECT

    reserve_risk(final_qty)

    APPROVE final_qty
```

---

# 218. Canonical Existing-Position Risk Loop

Conceptually:

```text
for each open position:

    update current risk boundary
    update open risk
    update stress risk
    update liquidity state
    update portfolio concentrations

    if emergency safety condition:
        EXIT_REQUIRED
    else if account risk state requires flatten:
        EXIT_REQUIRED
    else if portfolio constraint requires reduction:
        REDUCE
    else:
        defer thesis management to DD-02
```

---

# 219. Risk Authority Hierarchy

When multiple decisions conflict:

```text
Compliance Prohibition
    ↓
Emergency / Infrastructure Safety
    ↓
Account Risk State
    ↓
Portfolio Risk
    ↓
Strategy / Symbol / Sector Risk
    ↓
Trade Risk
    ↓
ML Modifier
    ↓
Execution Optimization
```

Higher authority wins.

---

# 220. What DD-03 Resolves

DD-03 establishes:

- RDT-derived thesis-first risk philosophy;
- risk vs notional distinction;
- structural invalidation before sizing;
- automated protective-stop requirement;
- execution-adjusted loss per unit;
- stable session risk reference equity;
- no automatic intraday profit compounding;
- trade-risk ceilings as maximums, not targets;
- deterministic position-sizing formula;
- capital, liquidity, exposure, sector, correlation, and margin constraints;
- portfolio open-risk and stress-risk concepts;
- progressive drawdown risk states;
- symbol/strategy/sector loss accounting;
- no averaging down for V1;
- pyramiding disabled initially but preserved as a research direction;
- ML risk authority disabled initially;
- kill-switch and operational-risk hierarchy;
- fail-closed behavior;
- versioned RiskDecision contract;
- risk reservation for pending orders;
- recovery requirements;
- research and validation requirements.

---

# 221. What Remains Empirical

The following values must be determined from data rather than opinion:

- base trade-risk fraction;
- absolute trade-risk cap;
- maximum portfolio open risk;
- stress-risk cap;
- maximum gross exposure;
- maximum net exposure;
- maximum symbol notional/risk;
- maximum sector exposure/risk;
- maximum simultaneous positions;
- correlation windows and threshold;
- correlation modifier curve;
- margin safety buffer;
- execution slippage allowance;
- stress exit model;
- liquidity participation cap;
- drawdown thresholds;
- progressive risk-state multipliers;
- weekly/monthly/account drawdown limits;
- loss-streak thresholds;
- market-regime risk modifiers;
- setup-quality risk modifiers;
- pyramiding rules;
- risk recovery cooldowns.

These are intentionally research-derived policy parameters.

---

# 222. What Remains Outside DD-03

## DD-04 / DD-04A

- concrete module boundaries;
- concurrency primitives;
- transaction implementation;
- database tables;
- deployment/fencing implementation.

## DD-05

- market-data schemas;
- data-quality implementation;
- canonical liquidity features.

## DD-06

- backtest engine;
- Monte Carlo engine;
- statistical confidence methods;
- parameter optimization;
- risk-policy experiment framework.

## DD-07

- paper slippage/fill models;
- stress execution simulation.

## DD-08

- OMS;
- broker protective orders;
- partial fills;
- reconciliation;
- live kill-switch execution;
- restart recovery.

## DD-09

- ML probability estimates;
- ML risk modifier validation.

---

# 223. Research Hypothesis R1 — Base Risk Fraction

> There exists a broad, robust range of per-trade risk fractions that preserves positive after-cost expectancy while keeping drawdown and tail risk inside acceptable bounds.

Do not seek a single magical percentage.

---

# 224. Research Hypothesis R2 — Progressive Daily De-Risking

> Progressive reduction after session drawdown produces better risk-adjusted outcomes than either no daily control or a single hard cliff.

---

# 225. Research Hypothesis R3 — Sector Concentration

> Sector-level risk caps reduce tail drawdown from correlated trades without materially destroying strategy expectancy.

---

# 226. Research Hypothesis R4 — Statistical Correlation Modifier

> A rolling correlation reducer adds value beyond deterministic sector caps.

If it does not, omit it.

---

# 227. Research Hypothesis R5 — Market-Regime Sizing

> Reducing risk in choppy/uncertain regimes improves after-cost expectancy and/or drawdown compared with constant risk sizing.

---

# 228. Research Hypothesis R6 — Pyramiding Winners

> Adding to validated winners during trend regimes improves total expectancy without unacceptable tail-risk expansion.

This directly tests an important RDT principle.

---

# 229. Research Hypothesis R7 — Execution-Adjusted Sizing

> Position sizing that includes empirical stop slippage and exit costs produces materially better realized risk control than stop-distance-only sizing.

---

# 230. Research Hypothesis R8 — Stress-Risk Gate

> A stress-risk gate prevents a meaningful subset of severe losses while preserving most ordinary positive-expectancy trades.

---

# 231. Research Hypothesis R9 — Loss-Streak De-Risking

> Strategy/symbol loss-streak throttles improve risk-adjusted results after accounting for the natural distribution of random loss streaks.

---

# 232. Research Hypothesis R10 — Quality-Based Sizing

> Deterministic setup-quality sizing improves risk-adjusted returns versus equal-risk sizing.

Until proven, equal-risk sizing remains the cleaner baseline.

---

# 233. Research Order

Recommended sequence:

```text
1. Implement equal-risk baseline
2. Calibrate execution-adjusted loss per unit
3. Map portfolio drawdown across base risk fractions
4. Add gross/net/symbol/sector hard caps
5. Validate daily progressive drawdown states
6. Add stress-risk scenarios
7. Test correlation reducer
8. Test market-regime risk modifier
9. Test strategy/symbol loss-streak controls
10. Test quality-based sizing
11. Test pyramiding winners
12. Test future ML risk modifier last
```

This minimizes confounding.

---

# 234. First Risk Research Milestone

The first milestone is answering:

> Given the finalized DD-02 V1 strategy, what equal-risk-per-trade range produces acceptable drawdown, tail loss, and risk-adjusted expectancy under realistic NSE costs and slippage?

Until this is known, sophisticated sizing is premature.

---

# 235. Second Risk Research Milestone

Then answer:

> How much incremental protection comes from portfolio concentration, daily drawdown, and stress-risk controls, and what positive-expectancy opportunities do those controls suppress?

---

# 236. Third Risk Research Milestone

Only after a robust baseline exists:

> Does dynamic sizing based on regime, setup quality, correlation, or validated ML improve out-of-sample risk-adjusted performance?

---

# 237. Final Risk Principle

The foundational DD-03 rule is:

> **The strategy defines why the trade exists and where that thesis fails. Risk determines how much capital may be exposed to that thesis, constrains the portfolio when multiple valid trades combine into one larger bet, reduces authority as losses or operational uncertainty increase, and always fails closed when the true risk cannot be known.**

In system form:

```text
Valid Setup
    ↓
Structural Invalidation
    ↓
Execution-Adjusted Loss Per Unit
    ↓
Trade Risk Ceiling
    ↓
Session / Portfolio / Strategy / Symbol / Sector Constraints
    ↓
Liquidity / Margin / Correlation / Stress Constraints
    ↓
Final Quantity
    ↓
Risk Reservation
    ↓
Trade Plan
    ↓
Execution
```

And throughout the position lifecycle:

```text
Thesis controls the trade.
Risk controls the capital.
Safety can always reduce authority.
Nothing can silently exceed a hard risk ceiling.
```

That is the foundational risk management and position-sizing specification for Edge Relative.
