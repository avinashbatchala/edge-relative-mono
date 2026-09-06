# Edge Relative — Algorithm Research & Strategy Principles

**Document:** DD-02  
**Version:** 1.0  
**Status:** Foundational Algorithm Design / Research Specification  
**Product:** Edge Relative  
**Market:** Indian equities  
**Initial Exchange Focus:** NSE  
**Initial Strategy Scope:** Intraday directional equities  
**Directions:** Long and Short  
**Primary Methodological Source:** RealDayTrading Wiki, May 2024  
**Related Documents:** DD-01 — Product & Feature Design Specification; DD-03 — Risk Management & Position Sizing; DD-04A — Development Stack & Engineering Standards  

---

# 1. Purpose

DD-01 defines what Edge Relative must do.

DD-02 defines how the first deterministic trading method should reason about the market, select stocks, qualify setups, time entries, invalidate trades, and exit positions.

The purpose of this document is not to claim that a strategy is profitable before evidence exists.

The purpose is to convert a discretionary trading methodology into a deterministic, falsifiable, versioned research specification that can be implemented identically in historical replay, backtesting, paper trading, shadow trading, and live trading.

The first candidate strategy is derived primarily from the RealDayTrading methodology:

```text
MARKET FIRST
    ↓
STOCK SECOND
    ↓
OPTIONS LAST
```

For Edge Relative V1 this becomes:

```text
MARKET FIRST
    ↓
SECTOR CONTEXT
    ↓
STOCK SECOND
    ↓
DETERMINISTIC SETUP
    ↓
RISK
    ↓
EQUITY EXECUTION
```

Options are deliberately excluded from Strategy V1 trading authority. They may later express an already-qualified underlying directional opportunity.

---

# 2. Source Hierarchy

This document distinguishes four kinds of statements.

## 2.1 RDT-DERIVED

A principle, rule, formula, or observation directly supported by the RealDayTrading Wiki.

## 2.2 EDGE-FORMALIZATION

A deterministic definition introduced by Edge Relative to convert qualitative RDT guidance into machine-executable logic.

## 2.3 NSE-ADAPTATION

A change required because RDT is primarily written around the US market and SPY while Edge Relative initially trades NSE securities.

## 2.4 RESEARCH-HYPOTHESIS

A proposition that must be tested before it becomes production trading authority.

No RDT claim is automatically treated as statistically established for the NSE universe.

---

# 3. Source Provenance

The primary source material used by this design includes the following areas of the RealDayTrading Wiki:

- The Method and the Market First / Stock Second / Options Last framework — approximately pp. 51–61;
- Relative Strength / Relative Weakness — approximately pp. 64–80;
- Real Relative Strength formulation — approximately pp. 65–70;
- Market First analysis — approximately pp. 83–145;
- Finding RS/RW stocks and highest-probability setups — approximately pp. 147–173;
- Technical analysis, price action, daily-chart context, and entry timing — approximately pp. 173–214;
- Trading timing and opening-session caution — approximately pp. 254–270;
- compression, sector rotation, trend behavior, and trading techniques — approximately pp. 271–300;
- trade planning, entries, stops, exits, and position management — approximately pp. 312–368;
- volume analysis — approximately pp. 351–357;
- risk/reward discussion — approximately pp. 369–389;
- community trading-plan examples — approximately pp. 539–591;
- scanner and RRS implementation examples — approximately pp. 592–605.

Community-contribution rules are treated as examples of formalization, not as canonical RDT rules unless separately supported by the core methodology.

---

# 4. Foundational Trading Thesis

The first Edge Relative trading hypothesis is:

> A stock exhibiting persistent directional strength or weakness relative to the broad market, especially when its sector and higher-timeframe price structure agree, should provide a higher-quality directional opportunity when traded with rather than against the current market state.

This is a research hypothesis, not an assumed truth.

---

# 5. Core Strategy Invariant

The strategy must never begin with the stock alone.

The decision order is:

```text
Broad Market
    ↓
Market Direction / Regime
    ↓
Sector Context
    ↓
Stock Daily Structure
    ↓
Stock Relative Strength / Weakness
    ↓
Volume / Participation
    ↓
Intraday Structure
    ↓
Entry Confirmation
    ↓
Trade Thesis
```

A strong-looking stock cannot bypass an opposing market prohibition.

---

# 6. Deterministic Strategy V1

The first candidate strategy is named:

```text
ER_RS_CONTINUATION_V1
```

It is a directional continuation strategy based on:

- market alignment;
- relative strength for longs;
- relative weakness for shorts;
- aligned daily structure;
- sufficient market participation / liquidity;
- technical room to move;
- intraday confirmation rather than anticipation.

---

# 7. Strategy V1 Instrument Scope

V1 trades:

> NSE cash equities intraday.

V1 does not itself trade:

- options;
- futures;
- spreads;
- overnight equity positions;
- momentum low-float analogues;
- pure scalps;
- mean reversion;
- counter-trend reversal setups.

The underlying directional signal may later be consumed by an instrument-selection engine.

---

# 8. Strategy V1 Direction Scope

V1 supports both:

```text
LONG
SHORT
```

The logic is intentionally symmetric where market mechanics permit.

Longs seek relative strength in a non-bearish market.

Shorts seek relative weakness in a non-bullish market.

---

# 9. Strategy Authority Boundary

The deterministic strategy answers:

> Is a structurally valid opportunity present?

It does not determine:

- account risk;
- final position size;
- portfolio exposure;
- broker routing;
- execution slicing;
- ML confidence;
- whether an invalid setup should be rescued by a model.

These remain separate system responsibilities.

---

# 10. ML Boundary

ML has no authority inside Strategy V1 qualification.

The invariant remains:

```text
Strategy = INVALID
ML = HIGH CONFIDENCE

Final = NO TRADE
```

ML may later rank or reject already-valid setups under the authority model defined in DD-01.

---

# 11. RDT Claims Are Hypotheses for NSE

The RDT Wiki repeatedly states that approximately 70–80% or more of stocks tend to follow the broad market and presents high win-rate targets for its training methodology.

Edge Relative does not encode those numbers as facts.

They become hypotheses to test on:

- NSE historical data;
- the intended stock universe;
- intraday time horizons;
- realistic transaction costs;
- realistic execution assumptions.

---

# 12. Primary Market Benchmark

**NSE-ADAPTATION**

The initial canonical broad-market benchmark for Strategy V1 is:

```text
NIFTY 50
```

Reason:

- it is a highly liquid, continuously observed broad-market reference;
- it is a practical analogue to the role SPY plays in the source methodology;
- it represents the large-cap market environment most relevant to the initial liquid-stock focus.

This is a versioned strategy decision, not a permanent assumption.

---

# 13. Benchmark Challenger

The principal benchmark challenger is:

```text
NIFTY 500
```

Research must compare whether RRS and market-state signals are more predictive using:

- NIFTY 50;
- NIFTY 500;
- appropriate combinations.

A strategy version must never silently change its benchmark.

---

# 14. Sector Benchmarking

Each stock should map to a canonical sector benchmark where a defensible NSE sector index exists.

Conceptually:

```text
Broad Market
    ↓
Sector
    ↓
Stock
```

The exact stock-to-sector mapping belongs to the reference-data architecture but the trading algorithm consumes it.

---

# 15. Stacked Relative Strength

**EDGE-FORMALIZATION inspired by RDT community top-tier criteria**

For a long candidate, the ideal structure is:

```text
Sector strong vs Market
AND
Stock strong vs Sector
AND
Stock strong vs Market
```

For a short candidate:

```text
Sector weak vs Market
AND
Stock weak vs Sector
AND
Stock weak vs Market
```

Stacked strength is initially a quality factor rather than a universal hard gate.

Research may promote it to a hard gate if it materially improves out-of-sample expectancy.

---

# 16. Canonical Timeframes

Strategy V1 uses:

| Purpose | Timeframe |
|---|---|
| Higher-timeframe structural context | Daily |
| Primary intraday setup | 5-minute |
| Additional context / agreement | 15-minute, 30-minute, 60-minute |
| Execution microstructure | execution layer, not strategy V1 |

The Daily and 5-minute charts are primary because those are repeatedly emphasized by the RDT methodology.

---

# 17. No Look-Ahead Timeframe Rule

Every higher-timeframe feature must be calculated only from information available at the decision timestamp.

Examples:

- the current incomplete daily candle may be used only as an explicitly incomplete candle;
- a 15-minute bar cannot be treated as closed at minute 7;
- confirmed pivots cannot use future candles that had not yet occurred.

---

# 18. Trading Session Configuration

Trading-session times must be configuration-driven from the exchange calendar.

For NSE normal trading, Strategy V1 applies an opening observation period equivalent to the RDT recommendation to avoid rushing into the first 30 minutes.

Conceptually:

```text
SESSION OPEN
    ↓
OBSERVE / BUILD CONTEXT
    ↓
ENTRY ELIGIBILITY AFTER 30 MINUTES
```

The implementation should derive this from exchange-session metadata rather than hard-code wall-clock assumptions throughout the domain.

---

# 19. Opening Blackout

Default V1 rule:

```text
entry_allowed = false
for first 30 minutes of continuous trading
```

The system still calculates:

- market state;
- RRS;
- RVOL;
- sector state;
- setups;
- alerts;
- hypothetical trades.

It simply does not convert them to eligible V1 entries during the blackout.

This parameter must be tested rather than accepted permanently without evidence.

---

# 20. Market State Engine

RDT uses 1OP plus price-action confirmation to determine market bias and timing.

1OP is proprietary and its formula is not available in the supplied source.

Edge Relative therefore will not claim to reproduce 1OP.

Instead V1 defines an independent deterministic Market State Engine based on observable price structure and confirmation.

---

# 21. 1OP Replacement Principle

The replacement must provide the information Edge Relative actually needs:

```text
Direction
Regime
Trend persistence
Pullback / impulse state
Confirmation
Confidence / evidence count
```

It does not need to imitate 1OP numerically.

---

# 22. Market State Outputs

The Market State Engine emits:

```text
market_bias:
    BULLISH
    BEARISH
    NEUTRAL

market_regime:
    BULL_TREND
    BEAR_TREND
    RANGE
    TRANSITION
    DISLOCATED

market_phase:
    BULL_IMPULSE
    BULL_PULLBACK
    BEAR_IMPULSE
    BEAR_PULLBACK
    COMPRESSION
    UNKNOWN
```

Bias controls directional permission.

Regime controls strategy compatibility.

Phase helps time entries.

---

# 23. Confirmed Pivot Definition

**EDGE-FORMALIZATION**

For pivot width `k`:

```text
pivot_high(i) = high(i) is the maximum high from i-k through i+k
pivot_low(i)  = low(i)  is the minimum low from i-k through i+k
```

A pivot becomes confirmed only after the required right-side candles have closed.

This intentional delay prevents look-ahead leakage.

`k` is versioned and calibrated through research.

---

# 24. Price-Structure Trend

Using the latest two confirmed swing highs and swing lows:

```text
Higher High + Higher Low = BULL STRUCTURE
Lower High  + Lower Low  = BEAR STRUCTURE
Otherwise                = MIXED / RANGE
```

This applies independently on Daily and 5-minute timeframes.

---

# 25. Directional Efficiency

**EDGE-FORMALIZATION**

To distinguish trend from chop:

```text
DirectionalEfficiency(n)
=
abs(C_t - C_{t-n})
/
sum(abs(C_i - C_{i-1})) over n bars
```

Range:

```text
0 → highly inefficient / choppy
1 → highly directional
```

Directional efficiency is a research feature and may become part of regime classification after validation.

---

# 26. Daily Market Bias

The Daily market bias uses observable structural evidence including:

- Daily swing structure;
- relationship to major moving averages;
- slope of major trend measures;
- position relative to major support/resistance;
- recent gap and breakout behavior.

The source emphasizes trendlines, horizontal support/resistance, major moving averages, and price patterns for longer-term market analysis.

No single indicator owns the bias.

---

# 27. Intraday Market Bias

The 5-minute market bias emphasizes:

- intraday swing structure;
- breaks and holds of horizontal support/resistance;
- compressions and releases;
- persistence of directional candles;
- quality of pullbacks;
- volume participation;
- relation to important Daily levels.

Major Daily moving averages may remain important levels when projected into the intraday chart, but V1 does not use a large stack of intraday moving averages to classify the market.

---

# 28. Market Bias Combination

Initial deterministic combination:

```text
if D1 strongly bullish and M5 bullish:
    BULLISH

if D1 strongly bearish and M5 bearish:
    BEARISH

if D1 neutral and M5 directional:
    directional but lower-confidence bias

if D1 and M5 conflict:
    NEUTRAL or TRANSITION

if M5 range/chop:
    NEUTRAL unless a confirmed breakout occurs
```

Exact confidence thresholds remain research parameters.

---

# 29. Market Permission Rule

For V1:

```text
LONG prohibited when market_bias = BEARISH
SHORT prohibited when market_bias = BULLISH
```

When market bias is NEUTRAL:

- long and short candidates may be considered;
- only high-quality independent strength/weakness setups are eligible;
- stacked sector alignment becomes more important;
- stricter validation thresholds may apply.

---

# 30. Dislocated Market State

The market can be marked `DISLOCATED` for conditions such as:

- abnormal gap beyond configured historical norms;
- extraordinary volatility;
- price discontinuity;
- exchange disruption;
- trusted-data failure.

Strategy V1 creates no new entries while the market state is DISLOCATED.

Infrastructure-specific causes are enforced by higher-level safety systems.

---

# 31. Real Relative Strength — Concept

RRS attempts to measure how much a stock moved beyond what would have been expected from the broad market move after normalizing both movements for their typical volatility.

This follows the RealDayTrading RRS proposal rather than simple percent-relative-strength or correlation.

---

# 32. Why Simple Percent Difference Is Insufficient

A stock moving +1% while the market moves +0.5% is not necessarily independently strong.

If the stock normally moves much more than the market, its apparent outperformance can simply reflect normal volatility.

Therefore V1 normalizes price movement by ATR.

---

# 33. RRS Raw Formula

For stock `S`, benchmark `M`, and calculation interval `t`:

```text
market_power_t
=
ΔP_M,t / ATR_M,t

expected_stock_move_t
=
market_power_t × ATR_S,t

RRS_raw_t
=
(ΔP_S,t - expected_stock_move_t)
/
ATR_S,t
```

Equivalent:

```text
RRS_raw_t
=
(ΔP_S,t / ATR_S,t)
-
(ΔP_M,t / ATR_M,t)
```

Interpretation:

```text
RRS > 0 → stronger than expected vs market
RRS < 0 → weaker than expected vs market
RRS = 0 → moved approximately as volatility-adjusted market exposure would suggest
```

This preserves the core mathematical idea in the source.

---

# 34. ATR Baseline for RRS

RDT examples discuss longer volatility baselines, including approximately 50 hours for intraday comparison and a TradingView example using 600 five-minute bars.

Edge Relative therefore defines ATR length as a versioned parameter rather than hard-coding one value across all timeframes.

Initial research candidates include:

```text
M5 ATR baseline ≈ 50 trading hours
D1 ATR baseline ≈ 50 trading sessions
```

These are research candidates, not production constants until validated.

---

# 35. RRS Persistence

A single high-RRS bar is insufficient.

The source explicitly identifies the problem of a one-candle burst creating misleading apparent strength.

V1 therefore separates:

```text
RRS_raw
RRS_fast
RRS_slow
RRS_persistence
```

where:

```text
RRS_fast = EMA(RRS_raw, fast_length)
RRS_slow = EMA(RRS_raw, slow_length)
```

Persistence may be expressed through:

- sign agreement;
- fast-vs-slow relationship;
- percent of recent bars with the same sign;
- rolling mean / median RRS.

The RDT example code uses 3- and 8-period EMAs of RRS; Edge Relative will test that pair rather than assume it is optimal.

---

# 36. RRS Trend

RRS trend is distinct from price trend.

Example outputs:

```text
POSITIVE_RISING
POSITIVE_FALLING
NEGATIVE_FALLING
NEGATIVE_RISING
NEUTRAL
```

A long candidate prefers positive and persistent RRS.

A short candidate prefers negative and persistent RRS.

---

# 37. RRS Acceleration

RRS acceleration is:

```text
RRS_acceleration_t
=
RRS_fast_t - RRS_fast_{t-1}
```

or a smoothed slope over a configurable lookback.

It is initially a quality feature, not a hard entry requirement.

---

# 38. Multi-Timeframe RRS

RRS is calculated independently on:

- Daily;
- 5-minute;
- optional 15-minute;
- optional 30-minute;
- optional 60-minute.

The source repeatedly treats multi-timeframe relative strength/weakness as higher-quality evidence.

V1 hard qualification requires Daily and 5-minute directional agreement unless research demonstrates a superior alternative.

---

# 39. Daily RRS Rule

Long candidate:

```text
D1_RRS_state = POSITIVE
```

Short candidate:

```text
D1_RRS_state = NEGATIVE
```

Magnitude thresholds must be learned from NSE evidence.

Sign alone is the initial structural rule; persistence and magnitude contribute to quality.

---

# 40. Intraday RRS Rule

Long candidate:

```text
M5_RRS_persistence > long_threshold
```

Short candidate:

```text
M5_RRS_persistence < short_threshold
```

The thresholds are versioned research parameters.

The strategy must not use arbitrary values such as 1.8 merely because they look plausible.

---

# 41. Sector Relative Strength

The same RRS formula is reused for:

```text
Sector vs Broad Market
Stock vs Sector
```

This avoids introducing a second incompatible definition of relative strength.

---

# 42. RRS Percentile

For ranking and research, each RRS measurement may also expose a rolling historical percentile:

```text
RRS_percentile
```

The percentile must be computed point-in-time using only prior observations.

It is not initially a validity gate.

---

# 43. Relative Volume — Source Principle

RDT treats volume as meaningful only relative to what is normal for the same instrument.

It also emphasizes that intraday volume should be interpreted in relation to the normal volume for that time of day.

Edge Relative therefore does not use raw absolute volume as a standalone trading signal.

---

# 44. Daily RVOL

Initial Daily RVOL definition:

```text
RVOL_D1_t
=
Volume_t
/
AverageDailyVolume(previous N sessions)
```

RDT commonly references a 50-period Daily volume baseline.

`N=50` is therefore a primary research candidate.

---

# 45. Intraday Interval RVOL

**EDGE-FORMALIZATION aligned with RDT time-of-day guidance**

For a 5-minute slot `s`:

```text
RVOL_interval(t,s)
=
Volume_current_session(t,s)
/
MeanVolume_same_slot(previous N valid sessions)
```

This prevents the normal opening and closing volume curve from being misclassified as abnormal participation.

---

# 46. Intraday Cumulative RVOL

At session time `τ`:

```text
RVOL_cumulative(τ)
=
CumulativeVolume_today_to_τ
/
MeanCumulativeVolume_to_τ(previous N sessions)
```

This is the preferred answer to questions such as:

> Is the stock trading unusually heavily by 11:00 relative to what is normal by 11:00?

---

# 47. RVOL Baseline Robustness

Research must compare:

- arithmetic mean;
- median;
- trimmed mean;
- exponentially weighted baseline.

Corporate-event sessions and abnormal historical days may materially distort a simple mean.

The baseline method is versioned.

---

# 48. Directional Volume

RDT emphasizes comparing volume on candles moving with the trade against volume on candles moving against it.

Edge Relative records:

```text
DirectionalVolumeRatio_long
=
volume_on_up_bars / volume_on_down_bars

DirectionalVolumeRatio_short
=
volume_on_down_bars / volume_on_up_bars
```

over a configurable recent window.

This is initially a quality feature.

---

# 49. RVE — Edge Relative Definition

The supplied RDT Wiki does not define an indicator named RVE.

DD-01 nevertheless requires RVE to become a first-class feature.

DD-02 therefore assigns the working meaning:

> **RVE = Relative Volume Expansion**

This is an Edge Relative-specific feature, not an RDT-derived indicator.

---

# 50. RVE Formula

RVE measures whether abnormal participation is expanding or contracting.

Let `RVOL_t` be time-of-day-normalized interval RVOL.

Define:

```text
fast_t = EWMA(log(RVOL_t), fast_length)
slow_t = EWMA(log(RVOL_t), slow_length)

RVE_t = fast_t - slow_t
```

Equivalent interpretation:

```text
RVE > 0 → relative volume expanding
RVE < 0 → relative volume contracting
RVE ≈ 0 → relative volume stable
```

The logarithm makes multiplicative changes symmetric and reduces scale distortion.

---

# 51. RVE Authority

RVE is not a Strategy V1 hard gate until it independently demonstrates value.

Initially it is:

- recorded;
- displayed;
- used in research;
- included in deterministic opportunity quality;
- available later to ML.

If RVE does not add out-of-sample information beyond RVOL, it should not become a production condition merely because DD-01 named it.

---

# 52. Volume Confirmation Rule

A valid V1 setup should show sufficient participation.

At least one of the following must be true under calibrated thresholds:

```text
Daily RVOL elevated
OR
Intraday interval RVOL elevated
OR
Cumulative RVOL elevated
```

Higher-quality setups have both elevated RVOL and directional volume aligned with the trade.

---

# 53. Liquidity Is Separate from RVOL

High relative volume does not necessarily mean sufficient absolute liquidity.

A stock can trade at 3× its normal volume and still be unsuitable.

Therefore V1 maintains separate liquidity hard gates.

---

# 54. Liquidity Inputs

Candidate liquidity inputs include:

- median daily traded value;
- median 5-minute traded value;
- quoted spread in basis points;
- available top-of-book size;
- short-term depth;
- trade frequency;
- slippage estimates.

Thresholds are NSE-specific and must be empirically calibrated.

---

# 55. Liquidity Hard Gate

A setup is invalid when liquidity does not meet the minimum production policy.

This is true even if every directional trading signal is favorable.

```text
Great setup + bad liquidity = NO TRADE
```

---

# 56. Daily Chart Principle

RDT repeatedly emphasizes using the Daily chart as a source of trend alignment and staying power.

V1 therefore requires Daily structural alignment.

The algorithm does not use the phrase "great daily chart" without a machine definition.

---

# 57. Daily Trend State

Long-aligned Daily structure requires, at minimum:

```text
Close > EMA8
AND
EMA8 slope > 0
AND
confirmed Daily swing structure is not bearish
```

Short-aligned structure requires:

```text
Close < EMA8
AND
EMA8 slope < 0
AND
confirmed Daily swing structure is not bullish
```

Research may strengthen or relax these gates.

---

# 58. Major Daily Moving Averages

The source repeatedly references major moving averages including 50, 100, and 200-period averages as support/resistance context.

V1 calculates:

```text
SMA50
SMA100
SMA200
EMA8
```

They are not all universal directional gates.

Their primary algorithmic roles are:

- trend quality;
- barrier detection;
- structure classification;
- invalidation context.

---

# 59. Daily Moving-Average Stack

Quality feature for longs:

```text
Price > SMA50 > SMA100 > SMA200
```

Quality feature for shorts:

```text
Price < SMA50 < SMA100 < SMA200
```

This is a strong-trend checkbox, not an unconditional requirement.

---

# 60. Extension from EMA8

The RDT material warns against heavily extended stocks and community criteria frequently reference distance from the 8-day EMA.

Edge Relative records both:

```text
extension_pct
=
abs(price - EMA8) / EMA8

extension_atr
=
abs(price - EMA8) / ATR_D1
```

The production limit should be volatility-aware and empirically calibrated.

The commonly cited 10% value becomes a research benchmark, not an unquestioned constant.

---

# 61. Daily Trend Cleanliness

To formalize "gappy/choppy" vs "clean" Daily charts, V1 records:

- directional efficiency;
- gap frequency;
- gap magnitude normalized by ATR;
- overlap ratio between consecutive candles;
- swing consistency;
- moving-average slope consistency.

A future `DailyTrendQuality` score may use these inputs after research.

---

# 62. Horizontal Support and Resistance

V1 derives horizontal levels from confirmed pivot clusters.

A level is formed when multiple confirmed pivot highs or lows occur within a configurable price tolerance.

The tolerance should be expressed in:

- ATR units;
- basis points;
- tick-size-aware minimums.

This is preferable to exact-price equality.

---

# 63. Trendline Formalization

RDT uses trendline breaches as entry confirmation.

V1 may generate deterministic trendlines using confirmed pivots only.

For a bearish resistance trendline:

- connect two or more confirmed pivot highs;
- require negative slope;
- require subsequent price interaction within tolerance.

For bullish support trendlines:

- connect confirmed pivot lows;
- require positive slope.

Trendline generation parameters must be frozen by strategy version.

---

# 64. Algo Lines

The RDT Wiki contains material on "Algo Lines" and high-volume origin points.

Because the supplied material does not provide one canonical machine definition suitable for V1, Algo Lines are deferred to research.

They must not become a hidden discretionary input.

---

# 65. Technical Void

RDT highest-probability discussions emphasize having room before important support/resistance.

Edge Relative defines:

```text
void_distance
=
abs(next_barrier - proposed_entry)
```

and normalized forms:

```text
void_pct
void_atr
```

A trade can be rejected when the nearest opposing barrier leaves insufficient room.

The required room is a research parameter.

---

# 66. Compression

RDT describes compression as a consolidation / coiled-spring state whose breakout can provide a high-quality continuation entry.

The proprietary compression tools in the source cannot be copied directly.

V1 therefore defines its own deterministic compression candidate.

---

# 67. Compression Candidate Definition

A rolling `n`-bar region is a compression candidate when it satisfies calibrated limits on:

```text
Range(n) / ATR
DirectionalEfficiency(n)
Average candle overlap
Optional Bollinger bandwidth percentile
```

Typical characteristics:

- narrow normalized range;
- low directional efficiency;
- substantial bar overlap;
- repeated failure to escape the zone.

No single threshold is fixed before research.

---

# 68. Compression Breakout

Long breakout confirmation:

```text
M5 close > compression_high + breakout_buffer
```

Short breakout confirmation:

```text
M5 close < compression_low - breakout_buffer
```

The breakout buffer is tick-size-aware and may include a small ATR fraction.

A wick through the level without a confirming close does not qualify.

---

# 69. 3/8 EMA Trigger

RDT community trading plans repeatedly use the 3/8 EMA relationship on the 5-minute chart as an entry-timing technique.

V1 implements it as an optional trigger:

Long:

```text
EMA3 crosses above EMA8
OR
EMA3 retests EMA8 and separates upward
```

Short:

```text
EMA3 crosses below EMA8
OR
EMA3 retests EMA8 and separates downward
```

It is a trigger, not a standalone strategy.

---

# 70. VWAP

VWAP is recorded as intraday context.

Preferred alignment:

```text
Long → price above or reclaiming VWAP
Short → price below or rejecting VWAP
```

VWAP is initially a quality factor unless the selected setup explicitly uses a VWAP reclaim/rejection trigger.

---

# 71. Heikin-Ashi

The source frequently uses Heikin-Ashi trend continuation/reversal as additional confirmation.

V1 calculates standard Heikin-Ashi candles as a research/quality feature.

HA is not a hard Strategy V1 gate because the source also treats it as one of several possible confirmations.

---

# 72. Previous-Day Levels

V1 records:

```text
previous_day_high
previous_day_low
previous_day_close
```

Quality factors:

- long stock trading and holding above previous-day high;
- short stock trading and holding below previous-day low.

These also become potential support/resistance and target references.

---

# 73. Setup Families in V1

ER_RS_CONTINUATION_V1 may be triggered through one of a controlled set of entry patterns:

```text
M5_COMPRESSION_BREAKOUT
M5_TRENDLINE_BREAK
M5_3_8_CONFIRMATION
M5_HORIZONTAL_LEVEL_BREAK
M5_PULLBACK_RESUMPTION
```

All share the same higher-level market, Daily, RRS, volume, liquidity, and risk prerequisites.

The trigger family is recorded explicitly.

---

# 74. Setup State Machine

Every stock transitions through:

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

Alternative terminal states:

```text
INVALIDATED
EXPIRED
MISSED
BLOCKED
```

This state machine is deterministic and auditable.

---

# 75. WATCH State

A stock enters WATCH when:

- it is eligible and liquid;
- Daily chart is directionally aligned;
- Daily RRS has the correct sign;
- market direction does not prohibit the trade.

No intraday trigger needs to exist yet.

---

# 76. FORMING State

A WATCH candidate becomes FORMING when:

- M5 RRS is aligned;
- sufficient volume participation is present or developing;
- a recognized setup structure is forming;
- no immediate technical blocker invalidates the setup.

---

# 77. NEAR_TRIGGER State

A FORMING candidate becomes NEAR_TRIGGER when current price approaches a deterministic trigger level within a configured distance.

Examples:

- compression boundary;
- trendline;
- horizontal resistance/support;
- 3/8 resumption point.

This state is useful for UI alerts and dataset capture.

---

# 78. VALID State

A candidate becomes VALID only when:

1. all hard prerequisites remain true;
2. opening blackout has ended;
3. market has not turned against the candidate;
4. trigger is confirmed;
5. entry is not excessively extended from the trigger;
6. structural invalidation can be identified;
7. liquidity remains acceptable;
8. risk engine is capable of evaluating the trade.

VALID means strategy-qualified.

It does not mean risk-approved or executed.

---

# 79. Confirmation, Not Anticipation

RDT repeatedly emphasizes waiting for confirmation.

V1 therefore does not enter merely because price is close to a breakout.

For a price-level trigger, confirmation requires a close beyond the level plus a configurable buffer.

The strategy may intentionally be late rather than early.

---

# 80. No-Chase Rule

A breakout can become `MISSED` if price moves too far beyond the planned trigger before execution eligibility.

Define:

```text
entry_extension
=
abs(current_price - trigger_price) / ATR_M5
```

If entry extension exceeds the calibrated maximum:

```text
status = MISSED
```

The system waits for a new setup rather than chasing.

---

# 81. Trigger Expiration

A confirmed trigger remains eligible only for a bounded number of bars or until a defined invalidating event occurs.

Examples of expiration:

- price becomes extended;
- RRS disappears;
- market flips;
- volume collapses;
- structure breaks;
- session approaches entry cutoff.

Expiration parameters are versioned.

---

# 82. Long Setup Hard Requirements

A V1 long candidate must satisfy:

```text
market_bias != BEARISH
AND market_regime != DISLOCATED
AND DailyStructure = LONG_ALIGNED
AND Daily RRS positive
AND M5 RRS sufficiently positive/persistent
AND liquidity valid
AND volume participation valid
AND technical void adequate
AND opening blackout complete
AND long trigger confirmed
AND structural invalidation identifiable
AND no strategy-level event prohibition
```

Sector alignment is initially a quality condition unless promoted by evidence.

---

# 83. Short Setup Hard Requirements

A V1 short candidate must satisfy:

```text
market_bias != BULLISH
AND market_regime != DISLOCATED
AND DailyStructure = SHORT_ALIGNED
AND Daily RRS negative
AND M5 RRS sufficiently negative/persistent
AND liquidity valid
AND volume participation valid
AND technical void adequate
AND opening blackout complete
AND short trigger confirmed
AND structural invalidation identifiable
AND no strategy-level event prohibition
```

---

# 84. Neutral-Market Rule

When market bias is NEUTRAL, V1 requires stronger evidence of stock independence.

Candidate requirements may tighten through:

- stronger absolute RRS;
- stronger RRS persistence;
- stacked sector alignment;
- higher RVOL;
- cleaner Daily structure;
- stronger technical void.

The exact neutral-market thresholds must be calibrated separately from trend-market thresholds.

---

# 85. Event Risk

The source warns against blindly trading around binary events and repeatedly emphasizes awareness of earnings/news.

V1 introduces:

```text
event_risk_state:
    CLEAR
    BLOCKED
    UNKNOWN
```

Known scheduled company events may place the stock into BLOCKED status under the initial conservative policy.

The event-data implementation belongs elsewhere, but the strategy consumes the state.

---

# 86. Structural Invalidation

Every trade plan must identify the condition that proves the setup thesis wrong.

A stop is not chosen because a desired monetary loss happens to be convenient.

The source repeatedly argues that technical structure should drive the exit logic.

---

# 87. Invalidation Level Selection

Depending on trigger family, invalidation may reference:

- opposite side of a compression;
- most recent confirmed M5 swing low/high;
- breakout level after failed retest;
- major horizontal support/resistance;
- Daily support/resistance when appropriate;
- 3/8 recross if the 3/8 relationship was central to the entry thesis.

The selected invalidation basis is recorded before execution.

---

# 88. Protective Stop vs Thesis Exit

Edge Relative distinguishes:

```text
Protective Stop
```

from:

```text
Thesis Exit
```

The protective stop protects against catastrophic adverse movement and execution delay.

A thesis exit may occur earlier because the original setup is no longer valid.

Automated trading cannot rely solely on an unrecorded "mental stop."

---

# 89. Stop Buffer

Structural levels require a small buffer to reduce exact-level noise.

Conceptually:

```text
long_stop = invalidation_level - stop_buffer
short_stop = invalidation_level + stop_buffer
```

The buffer must be:

- tick-size-aware;
- volatility-aware;
- research-calibrated.

---

# 90. Risk/Reward Philosophy

RDT explicitly argues against forcing arbitrary fixed risk/reward ratios onto trades.

Edge Relative adopts the same principle.

The process is:

```text
Structure determines invalidation
Structure determines realistic target / room
Then compute implied reward/risk
```

Not:

```text
Choose 2R target first
Then distort stop/target to fit it
```

---

# 91. Structural Reward

Potential reward is measured to realistic technical objectives such as:

- next resistance/support;
- prior swing high/low;
- Daily level;
- measured breakout zone;
- session extreme;
- trailing thesis continuation.

A target does not have to exist for every trade if the exit method is explicitly trailing/thesis-based.

---

# 92. Implied Reward/Risk

After entry, invalidation, and target reference are known:

```text
RiskDistance = abs(entry - invalidation)
RewardDistance = abs(target_reference - entry)

StructuralRR = RewardDistance / RiskDistance
```

This is recorded for analysis.

It is not automatically a hard gate such as `StructuralRR >= 2`.

---

# 93. Break-Even Probability

Ignoring costs:

```text
RequiredWinProbability
=
RiskDistance
/
(RiskDistance + RewardDistance)
```

With costs and slippage, the required probability increases.

This allows strategy validation to compare actual historical setup win probability with the probability required by its structural payoff profile.

---

# 94. Expectancy Gate — Future Production

Once sufficient out-of-sample history exists, a setup family may require:

```text
LowerConfidenceBound(ExpectedValueAfterCosts) > 0
```

or a similarly conservative criterion.

This is preferable to an arbitrary universal reward/risk threshold.

The exact production risk gate belongs with DD-03 and DD-06 validation policy.

---

# 95. Exit Hierarchy

For V1, the first applicable exit condition wins.

Priority conceptually:

```text
1. Emergency / risk exit
2. Protective structural stop
3. Strategy thesis invalidation
4. Market reversal against position
5. Explicit target, if configured
6. Time / session exit
```

Risk and safety layers may always override strategy exits.

---

# 96. Thesis Invalidation Exit

Long examples:

- persistent RRS becomes materially negative;
- breakout fails and price closes back through invalidation structure;
- 3/8 relationship reverses when it was a defining trigger;
- key volume participation disappears together with price failure;
- Daily or M5 structure experiences a meaningful bearish violation.

Short logic is symmetric.

---

# 97. RRS Loss Exit

A single RRS tick through zero should not necessarily force an exit.

To avoid noise, V1 uses hysteresis/persistence.

Example structure:

```text
exit long when RRS_persistence < exit_threshold
for N_confirm consecutive completed bars
```

Threshold and confirmation count are research parameters.

---

# 98. Market Reversal Exit

If market bias changes against an open trade, V1 reassesses immediately.

Initial conservative behavior:

```text
LONG + market becomes BEARISH → exit or mandatory reduction signal
SHORT + market becomes BULLISH → exit or mandatory reduction signal
```

For Strategy V1, prefer full exit to preserve deterministic simplicity.

Portfolio hedging exceptions belong to later strategies, not V1.

---

# 99. Major Technical Violation Exit

A confirmed violation of the structural level that justified the trade is an exit.

Examples:

- failed compression breakout;
- break through meaningful pivot support/resistance;
- loss of a major Daily level;
- structural trend reversal.

The algorithm must record the exact violated level and reason.

---

# 100. Target Exit

If a fixed technical target was defined before entry and the target is reached, V1 may exit the full position.

V1 avoids partial-exit complexity initially unless research demonstrates a clear need.

Later versions may support scaling out and trailing a remainder.

---

# 101. Time Stop

A setup may be exited when it fails to behave within a reasonable time window.

The source recognizes scratch/time-stop behavior when a trade stops doing what was expected.

V1 records:

```text
bars_since_entry
MFE
MAE
progress_toward_target
```

A formal time-stop threshold must be learned per setup family rather than guessed.

---

# 102. End-of-Session Exit

Strategy V1 is intraday-only.

Therefore all positions are closed before the configured session flatten cutoff.

No V1 position becomes an accidental swing trade.

---

# 103. No Averaging Down

V1 does not add to losing positions merely because price is more favorable.

Any future scale-in rule must be a predefined strategy action tied to new confirmation and bounded risk.

Unplanned averaging down is prohibited.

---

# 104. Adding to Winners

The source often discusses adding to confirmed winners.

V1 deliberately omits pyramiding in its first validation version.

Reason:

- isolate the quality of the base signal;
- simplify attribution;
- simplify execution simulation;
- avoid confusing setup edge with scaling edge.

Pyramiding can be tested as a separate strategy version.

---

# 105. Deterministic Hard Gates vs Quality Factors

V1 separates binary validity from quality ranking.

## Hard gates

Examples:

- market permission;
- Daily alignment;
- RRS direction;
- liquidity;
- volume participation;
- technical room;
- opening blackout;
- confirmed trigger;
- identifiable invalidation;
- event eligibility.

## Quality factors

Examples:

- stacked sector strength;
- multi-timeframe RRS agreement;
- rising RRS;
- elevated RVOL;
- positive RVE for longs / expansion in short-side participation for shorts;
- Daily MA stack;
- HA confirmation;
- directional volume;
- previous-day high/low break;
- clean compression.

---

# 106. Checkbox Principle

RDT repeatedly describes high-probability trades as having more aligned "checkboxes."

Edge Relative preserves that concept without immediately inventing arbitrary weights.

Initial deterministic quality may be represented as:

```text
quality_checkbox_count
quality_checkbox_vector
```

rather than one opaque score.

---

# 107. Initial Deterministic Ranking

Before ML authority exists, valid opportunities can be ranked lexicographically by:

1. market alignment quality;
2. stacked sector alignment;
3. multi-timeframe RRS persistence;
4. volume participation;
5. Daily trend quality;
6. technical void;
7. liquidity quality;
8. trigger quality.

This order is a research starting point, not a final optimized weighting model.

---

# 108. Why Not Optimize Weights Immediately

If dozens of weights are optimized on the same historical sample, overfitting risk becomes severe.

V1 should first test whether the major structural hypotheses work independently and in simple combinations.

Only then should weighting be optimized.

---

# 109. Long Candidate Example

Conceptually:

```text
Market: BULLISH / BULL_TREND
Sector: Strong vs NIFTY
Stock D1: Long aligned
D1 RRS: Positive
M5 RRS: Positive and persistent
RVOL: Elevated
RVE: Expanding
Structure: M5 compression
Void: Adequate
Trigger: M5 close above compression high
Invalidation: Compression failure / pivot low
Liquidity: Valid

→ Strategy VALID LONG
```

---

# 110. Short Candidate Example

Conceptually:

```text
Market: BEARISH / BEAR_TREND
Sector: Weak vs NIFTY
Stock D1: Short aligned
D1 RRS: Negative
M5 RRS: Negative and persistent
RVOL: Elevated
RVE: Expanding participation
Structure: M5 compression
Void: Adequate
Trigger: M5 close below compression low
Invalidation: Compression failure / pivot high
Liquidity: Valid

→ Strategy VALID SHORT
```

---

# 111. No-Trade Example — Opposing Market

```text
Stock: Very strong
D1: Bullish
M5 RRS: Strong
RVOL: High

Market: BEARISH

→ NO LONG TRADE
```

V1 deliberately prefers missing an exceptional counter-market move over weakening its core discipline.

---

# 112. No-Trade Example — Weak Participation

```text
Market: Bullish
Stock: Bullish
RRS: Positive
Breakout: Yes
RVOL: Low
Liquidity: Thin

→ NO TRADE
```

A price move without sufficient participation does not meet the intended V1 profile.

---

# 113. No-Trade Example — No Void

```text
Everything aligned
but major Daily resistance is immediately overhead

→ NO TRADE
```

The setup has insufficient technical room.

---

# 114. No-Trade Example — Chasing

```text
Trigger confirmed
Stock immediately expands far beyond trigger
Entry extension exceeds limit

→ MISSED
```

The strategy does not convert FOMO into a new entry rule.

---

# 115. Strategy Output Contract

Each evaluation emits a structured object conceptually containing:

```text
strategy_id
strategy_version
instrument_id
timestamp
direction
setup_state
setup_family
market_bias
market_regime
market_phase
sector_state
daily_structure
RRS_D1
RRS_M5
RRS_multitimeframe
RVOL_D1
RVOL_interval
RVOL_cumulative
RVE
liquidity_state
trigger_type
trigger_level
invalidation_type
invalidation_level
target_method
target_reference
structural_rr
hard_gate_results
quality_factors
validity
invalid_reason
```

---

# 116. Learning Output Contract

Every setup observation also records:

```text
feature_snapshot_id
market features
sector features
stock features
price structure
volume structure
setup state
trigger distance
bar timestamps
future outcome labels when known
```

This fulfills the DD-01 principle that each strategy is also a structured data-generation engine.

---

# 117. Invalid Reason Codes

Invalidation should never be free text only.

Example codes:

```text
MARKET_OPPOSING
MARKET_DISLOCATED
DAILY_NOT_ALIGNED
RRS_D1_FAILED
RRS_M5_FAILED
RVOL_FAILED
LIQUIDITY_FAILED
NO_TECHNICAL_VOID
OPENING_BLACKOUT
NO_CONFIRMED_TRIGGER
ENTRY_EXTENDED
EVENT_BLOCKED
NO_STRUCTURAL_INVALIDATION
TRIGGER_EXPIRED
DATA_INVALID
```

---

# 118. Exit Reason Codes

Example codes:

```text
PROTECTIVE_STOP
THESIS_RRS_LOST
THESIS_STRUCTURE_FAILED
MARKET_REVERSAL
TARGET_REACHED
TIME_STOP
SESSION_FLATTEN
RISK_EXIT
MANUAL_EXIT
EMERGENCY_EXIT
```

---

# 119. Feature Versioning

Every mathematical feature must carry a version.

Examples:

```text
RRS_V1
RVOL_INTRADAY_V1
RVOL_DAILY_V1
RVE_V1
COMPRESSION_V1
PIVOT_STRUCTURE_V1
MARKET_STATE_V1
DAILY_TREND_QUALITY_V1
```

Changing a formula creates a new feature version.

---

# 120. Strategy Parameter Versioning

Parameters must be immutable for a strategy version.

Examples:

```text
rrs_atr_length
rrs_fast_length
rrs_slow_length
rvol_baseline_sessions
rve_fast_length
rve_slow_length
pivot_width
compression_window
compression_threshold
minimum_void
maximum_entry_extension
minimum_liquidity
opening_blackout
trigger_expiry
exit_confirmation_bars
```

---

# 121. Research Before Thresholds

DD-02 defines formulas and decision relationships first.

It does not pretend that arbitrary constants are known.

Thresholds must be derived through:

```text
Train
↓
Validation
↓
Out-of-sample test
↓
Walk-forward evaluation
↓
Sensitivity analysis
```

A threshold should survive nearby values.

---

# 122. Parameter Stability Rule

If a strategy works only at one exact parameter value and collapses nearby, the parameter is suspect.

Prefer broad stable regions.

Example:

```text
Good:
1.2, 1.3, 1.4 all viable

Suspicious:
1.31 excellent
1.30 poor
1.32 poor
```

---

# 123. Primary Research Hypothesis H1

> Market-aligned RS/RW stock trades outperform otherwise similar stock trades without market alignment.

This is the foundational RDT thesis that Edge Relative must independently test on NSE data.

---

# 124. H2 — RRS vs Simple Relative Return

> ATR-normalized RRS provides better predictive separation than simple stock-minus-index percentage return.

Compare:

- simple excess return;
- ratio relative strength;
- RRS raw;
- persistent/rolling RRS.

---

# 125. H3 — RRS Persistence

> Persistent relative strength/weakness is more predictive than one-bar relative strength/weakness.

Measure outcomes by:

- sign persistence;
- EMA fast/slow state;
- rolling mean;
- percentile;
- acceleration.

---

# 126. H4 — Daily Alignment

> Intraday RS/RW setups aligned with the Daily trend outperform identical intraday setups that are not Daily-aligned.

This tests the source's "lean on the Daily chart" principle.

---

# 127. H5 — Sector Stack

> Sector-vs-market and stock-vs-sector alignment improves expectancy beyond stock-vs-market RRS alone.

This is an Edge Relative extension strongly motivated by RDT sector-strength discussions.

---

# 128. H6 — Time-of-Day RVOL

> Same-time-of-day-normalized intraday RVOL provides better information than a generic rolling-bar volume average.

This directly tests the contextual-volume requirement in DD-01.

---

# 129. H7 — RVE

> Positive relative-volume expansion improves continuation probability after an otherwise-valid RS/RW setup.

If false, RVE remains observational and should not enter validity rules.

---

# 130. H8 — Opening Blackout

> Avoiding the first 30 minutes improves risk-adjusted expectancy for this specific continuation strategy.

Compare at least:

- no blackout;
- 15 minutes;
- 30 minutes;
- 45 minutes;
- context-dependent opening rules.

---

# 131. H9 — Compression Breakout

> RS/RW stocks that compress while the market moves against them and then break with market support outperform generic breakouts.

This is one of the most direct machine-testable translations of the RDT compression logic.

---

# 132. H10 — Technical Void

> Requiring room to the next meaningful support/resistance barrier improves realized expectancy and reduces immediate reversals.

Test void in:

- percentage terms;
- ATR terms;
- expected slippage-adjusted R terms.

---

# 133. H11 — Neutral Market

> Exceptional independent RS/RW setups retain edge in neutral/choppy markets, but require stricter quality than aligned trend-market trades.

This should be tested separately rather than pooled with trend environments.

---

# 134. H12 — Structural Exit vs Fixed Stop/Target

> Exiting on thesis/structure failure outperforms arbitrary fixed percentage stops and targets.

Compare:

- structural invalidation;
- ATR stop;
- fixed percentage;
- fixed R multiple;
- RRS-loss exit;
- combined state-machine exit.

---

# 135. Experimental Cohorts

Every historical setup should belong to explicit cohorts such as:

```text
Market regime
Direction
Sector aligned/not aligned
D1 aligned/not aligned
RRS bucket
RVOL bucket
RVE bucket
Setup family
Time of day
Liquidity bucket
Void bucket
```

This prevents average results from hiding important regime differences.

---

# 136. Outcome Labels

At minimum record:

```text
return_5m
return_15m
return_30m
return_60m
return_close
MFE
MAE
time_to_MFE
time_to_MAE
1R_hit
2R_hit
stop_hit
target_hit
target_before_stop
```

Labels must be defined relative to the exact historical strategy observation timestamp.

---

# 137. Setup-Level Evaluation Metrics

Evaluate:

- trade count;
- win rate;
- average R;
- median R;
- expectancy after costs;
- profit factor;
- maximum adverse excursion;
- maximum favorable excursion;
- drawdown;
- tail losses;
- parameter stability;
- regime stability.

No single metric establishes an edge.

---

# 138. RDT Win-Rate Targets

The RDT training material often references 75%+ win rates and profit-factor targets for traders.

Edge Relative records those as source context only.

Strategy acceptance must be based on its own out-of-sample expectancy after costs and risk characteristics.

A strategy with a lower win rate can still be superior if its payoff distribution is better.

---

# 139. Costs and Friction

All Strategy V1 research must eventually include:

- brokerage;
- exchange fees;
- taxes/charges;
- bid/ask spread;
- slippage;
- latency;
- partial fills where applicable.

Detailed simulation belongs to DD-06/DD-07, but DD-02 does not accept frictionless results as final evidence.

---

# 140. Survivorship and Point-in-Time Integrity

Research must not use:

- future index membership;
- future sector mapping;
- future corporate-action knowledge;
- future normalization samples;
- completed future candles;
- delisted-stock omission.

---

# 141. Watchlist Interaction

DD-01 caps the active watchlist at 20 stocks.

Strategy V1 evaluates each independently and then compares valid opportunities.

The watchlist does not alter whether a setup is structurally valid.

It affects relative opportunity priority.

---

# 142. Dynamic Candidate Discovery vs Active Watchlist

RDT uses scanners continuously because opportunities change throughout the day.

Edge Relative separates:

```text
Universe / discovery
```

from:

```text
Active 20-stock watchlist
```

Future discovery logic can continuously suggest replacements, but Strategy V1 only has trading authority over the active eligible set.

---

# 143. Candidate Ranking Without ML

Before ML earns authority, deterministic ranking should expose why one setup ranks above another.

Example:

```text
1. HDFCBANK — 8/9 quality confirmations
2. RELIANCE — 7/9
3. INFY — 6/9
```

The UI should show the individual factors rather than only the total.

---

# 144. Candidate Ranking With ML Later

Once validated, ML may consume:

- all deterministic features;
- setup family;
- stock identity;
- sector identity;
- market regime;
- time of day;
- historical similarity.

It may rank valid opportunities but cannot manufacture invalid ones.

---

# 145. Strategy Explainability

Every VALID decision must be able to produce a human-readable explanation such as:

```text
Market:
Bullish M5 structure; Daily not opposing.

Sector:
Bank sector outperforming NIFTY.

Stock:
Daily long-aligned; above EMA8; no nearby Daily resistance.

Relative Strength:
Positive on D1 and persistent on M5.

Volume:
1.7× expected volume for this time of day; participation rising.

Trigger:
5-minute compression breakout confirmed on close.

Invalidation:
Close back below compression support / last confirmed pivot.
```

---

# 146. No Hidden Discretion

The following phrases are prohibited in final strategy code unless backed by explicit features:

```text
Looks strong
Great chart
Good momentum
Nice setup
Market feels weak
Clean breakout
Institutional buying is obvious
```

Each must resolve to deterministic measurements or remain research commentary only.

---

# 147. Institutional Activity Interpretation

RDT interprets RS/RW, persistent price behavior, and volume as evidence consistent with institutional accumulation/distribution.

Edge Relative may use that interpretation in explanations.

However, V1 does not claim to directly observe institutional identity.

The algorithm observes price/volume behavior, not the legal identity of the trader behind it.

---

# 148. News and Fundamental Analysis

V1 is a technical short-horizon strategy.

Fundamental valuation is not part of trade qualification.

News may matter as:

- event risk;
- abnormal-regime context;
- cause of gaps/volume;
- reason to block an otherwise-unmodeled binary event.

V1 does not trade a headline simply because it exists.

---

# 149. Options Last Principle

RDT explicitly treats options as downstream of getting market and stock direction right.

Edge Relative preserves this separation.

Future flow:

```text
Underlying strategy produces LONG/SHORT opportunity
        ↓
Risk permits exposure
        ↓
Instrument Selection compares Equity / Future / Option
```

The options engine cannot rescue a poor underlying setup.

---

# 150. Swing Trading Boundary

RDT often uses a strong Daily chart to create staying power and occasionally turn day trades into swings.

Strategy V1 does not do this.

All positions are intraday.

However, the research dataset should record hypothetical overnight continuation labels so a future swing strategy can be studied separately.

---

# 151. Counter-Trend Trades

Counter-trend trading is excluded from V1.

A future reversal strategy must have its own:

- hypothesis;
- entry rules;
- risk model;
- validation;
- lifecycle.

It must not be smuggled into a continuation strategy through exceptions.

---

# 152. Momentum/Scalping Boundary

RDT distinguishes its core RS/RW technical method from low-float momentum/scalping approaches.

V1 likewise excludes fast gap-chasing momentum logic.

A stock can move quickly and still qualify, but it must satisfy the V1 structural rules rather than a separate scalp method.

---

# 153. Market Regime Compatibility

Initial compatibility matrix:

| Market Regime | Long RS Continuation | Short RW Continuation |
|---|---:|---:|
| BULL_TREND | Preferred | Prohibited except future hedge strategy |
| BEAR_TREND | Prohibited except future hedge strategy | Preferred |
| RANGE | High-quality only | High-quality only |
| TRANSITION | Restricted / research | Restricted / research |
| DISLOCATED | Prohibited | Prohibited |

---

# 154. Quality Escalation in Range

When the market is in RANGE, require more independent evidence rather than lowering standards.

Candidate additions include:

- stronger RRS percentile;
- sector stack;
- higher RVOL;
- clearer breakout structure;
- larger void;
- tighter liquidity.

Exact requirements require research.

---

# 155. Transition Regime

TRANSITION means market evidence is conflicting or rapidly changing.

V1 should initially prefer:

```text
NO NEW TRADE
```

until a stable directional or range classification forms.

This conservative behavior can later be relaxed only with evidence.

---

# 156. Data Quality Dependency

A trading decision is invalid if required market, sector, or stock data is stale or missing.

The strategy should emit:

```text
DATA_INVALID
```

rather than extrapolate silently.

---

# 157. Deterministic Replay Invariant

Given identical:

```text
market events
feature versions
strategy version
parameters
calendar
reference data
```

Strategy V1 must produce identical:

```text
setup states
validity decisions
trigger timestamps
invalidations
exit decisions
```

---

# 158. Research/Production Parity

The canonical Java production implementation must ultimately be the reference for production-grade backtests.

Python can explore hypotheses rapidly, but production rules must be frozen into explicit specifications and parity-tested.

---

# 159. Required Shared Fixtures

At minimum create fixtures for:

- RRS raw formula;
- RRS persistence;
- Daily RVOL;
- intraday slot RVOL;
- RVE;
- pivots;
- compression;
- market state;
- long qualification;
- short qualification;
- invalidation;
- exits.

Java and Python should match within defined numerical tolerance.

---

# 160. RRS Fixture Example

A fixture should explicitly contain:

```text
stock prices
market prices
stock ATR
market ATR
expected market-normalized stock move
expected RRS
```

Tests must include:

- market up / stock up faster;
- market up / stock flat;
- market down / stock flat;
- market down / stock down faster;
- market flat / stock directional;
- one-candle spike followed by stagnation.

---

# 161. RVOL Fixture Example

Tests should include:

- opening high volume that is normal for opening time;
- genuinely abnormal opening volume;
- midday low absolute volume but high same-time RVOL;
- missing prior sessions;
- holiday-shortened sessions if applicable;
- corporate-event outliers.

---

# 162. State-Machine Fixture Example

A canonical M5 sequence should demonstrate:

```text
WATCH
→ FORMING
→ NEAR_TRIGGER
→ VALID
→ ENTERED
→ THESIS_INVALIDATED
```

Another should demonstrate:

```text
WATCH
→ FORMING
→ NEAR_TRIGGER
→ MISSED
```

because price extended beyond the chase limit.

---

# 163. Initial Strategy Promotion Criteria

ER_RS_CONTINUATION_V1 cannot advance based on attractive charts or anecdotal examples.

Required progression:

```text
RESEARCH
→ HISTORICAL BACKTEST
→ OUT-OF-SAMPLE
→ WALK-FORWARD
→ SHADOW LIVE
→ PAPER
→ ASSISTED LIVE
```

Exact quantitative promotion thresholds belong in the research and risk documents.

---

# 164. Minimum Evidence Before Live Capital

Before live capital, evidence should include:

- sufficient trade sample size across multiple regimes;
- positive expectancy after realistic costs;
- acceptable drawdown;
- no dependence on a tiny parameter island;
- long and short results analyzed separately;
- sector concentration analyzed;
- time-of-day behavior analyzed;
- simulated execution viability;
- live-shadow parity.

---

# 165. Strategy Failure Criteria

The strategy should be rejected or materially redesigned if:

- RRS does not outperform simpler baselines;
- market alignment adds no value;
- Daily alignment adds no value;
- costs erase the edge;
- results disappear out of sample;
- edge exists only in a small historical regime;
- parameters are unstable;
- execution requirements are unrealistic.

A failed hypothesis is a valid research outcome.

---

# 166. RDT Fidelity vs Edge Relative Independence

Edge Relative should first test the source methodology faithfully enough to answer:

> Does the underlying idea work on our market and universe?

Only after establishing that baseline should we test improvements.

This prevents creating a heavily modified strategy and then incorrectly attributing its success or failure to RDT.

---

# 167. Baseline Strategy Family

Research should maintain at least two clear baselines.

## Baseline A — Simple RDT-style

```text
Market aligned
Daily aligned
RS/RW
High relative volume
Technical confirmation
```

## Baseline B — Edge Relative enhanced

```text
Baseline A
+
ATR-normalized RRS persistence
+
sector stack
+
time-of-day RVOL
+
RVE
+
formalized void
+
formalized compression
```

The incremental value of each addition must be measurable.

---

# 168. Ablation Testing

For every proposed enhancement, test the system with and without it.

Examples:

```text
Full model - sector filter
Full model - RVE
Full model - opening blackout
Full model - Daily alignment
Full model - technical void
```

This reveals which features actually contribute.

---

# 169. Avoiding Indicator Soup

A feature does not belong in production merely because it is available.

The final strategy should prefer a small number of defensible dimensions:

```text
Market
Relative Strength
Daily Structure
Volume
Sector
Price Structure
Liquidity
```

Additional indicators must earn inclusion.

---

# 170. Canonical V1 Decision Flow

```text
Receive completed relevant market data
        ↓
Validate data quality
        ↓
Update broad market state
        ↓
Update sector state
        ↓
Update stock Daily/M5 features
        ↓
Check Daily directional alignment
        ↓
Check RRS Daily + M5
        ↓
Check RVOL / participation
        ↓
Check liquidity
        ↓
Check technical void
        ↓
Detect setup structure
        ↓
Wait for confirmation trigger
        ↓
Reject if chased / expired / market flipped
        ↓
Create VALID strategy opportunity
        ↓
Send to Risk Engine
```

---

# 171. Canonical V1 Long Logic

Conceptually:

```text
IF data_valid
AND session_entry_allowed
AND market_bias != BEARISH
AND market_regime not in {DISLOCATED, restricted TRANSITION}
AND daily_long_aligned
AND d1_rrs > 0
AND m5_rrs_persistent > calibrated_long_threshold
AND volume_valid
AND liquidity_valid
AND technical_void_valid
AND event_risk != BLOCKED
AND confirmed_long_trigger
AND not_extended_from_trigger
AND invalidation_defined
THEN
    strategy_valid = true
    direction = LONG
ELSE
    strategy_valid = false
```

---

# 172. Canonical V1 Short Logic

Conceptually:

```text
IF data_valid
AND session_entry_allowed
AND market_bias != BULLISH
AND market_regime not in {DISLOCATED, restricted TRANSITION}
AND daily_short_aligned
AND d1_rrs < 0
AND m5_rrs_persistent < calibrated_short_threshold
AND volume_valid
AND liquidity_valid
AND technical_void_valid
AND event_risk != BLOCKED
AND confirmed_short_trigger
AND not_extended_from_trigger
AND invalidation_defined
THEN
    strategy_valid = true
    direction = SHORT
ELSE
    strategy_valid = false
```

---

# 173. Canonical V1 Exit Logic

Conceptually:

```text
IF emergency_or_risk_exit:
    EXIT
ELSE IF protective_stop_hit:
    EXIT
ELSE IF structural_thesis_failed:
    EXIT
ELSE IF market_bias_reversed_against_trade:
    EXIT
ELSE IF configured_target_reached:
    EXIT
ELSE IF formal_time_stop_hit:
    EXIT
ELSE IF session_flatten_time:
    EXIT
ELSE:
    HOLD
```

---

# 174. What DD-02 Resolves

DD-02 establishes:

- RDT as the primary methodology source;
- market-first directional logic;
- long and short V1 scope;
- intraday equity V1 scope;
- a non-proprietary market-state replacement for 1OP;
- NIFTY 50 as the initial canonical market benchmark;
- NIFTY 500 as a benchmark challenger;
- sector context;
- RRS mathematical definition;
- RRS persistence requirements;
- Daily and M5 multi-timeframe structure;
- contextual RVOL;
- an Edge Relative RVE definition;
- Daily-chart formalization direction;
- compression formalization;
- entry confirmation rules;
- opening blackout;
- no-chase behavior;
- structural invalidation;
- structural rather than arbitrary reward/risk;
- deterministic exit hierarchy;
- explicit setup state machine;
- research hypotheses and validation plan.

---

# 175. What Remains Empirical

The following must be determined from data rather than opinion:

- final market benchmark choice if NIFTY 500 beats NIFTY 50;
- RRS ATR lengths;
- RRS persistence lengths;
- RRS thresholds;
- RVOL baseline length;
- minimum RVOL;
- RVE fast/slow lengths;
- RVE usefulness;
- pivot width;
- compression thresholds;
- technical void threshold;
- liquidity thresholds;
- entry buffer;
- chase threshold;
- trigger expiry;
- time stop;
- neutral-market tightening;
- exit persistence;
- quality ranking order/weights.

These are not design omissions. They are intentionally research-derived parameters.

---

# 176. What Remains Outside DD-02

## DD-03

- maximum risk per trade;
- account risk;
- portfolio risk;
- position sizing;
- drawdown limits;
- correlation limits;
- kill switches.

## DD-05

- canonical tick/candle schemas;
- data ingestion;
- timestamp semantics;
- feature storage and replay mechanics.

## DD-06

- exact backtest engine behavior;
- transaction simulation;
- walk-forward framework;
- Monte Carlo;
- experiment registry implementation.

## DD-07 / DD-08

- paper fills;
- broker execution;
- OMS;
- reconciliation;
- live recovery.

## DD-09

- ML ranking;
- calibration;
- stock-specific modeling;
- historical similarity.

---

# 177. Research Order

The recommended research sequence is:

```text
1. Validate market-following premise on NSE
2. Implement simple relative return baseline
3. Implement RRS_V1
4. Compare simple RS vs RRS
5. Add RRS persistence
6. Validate Daily alignment
7. Validate time-of-day RVOL
8. Validate market-alignment rules
9. Validate compression / trigger families
10. Validate sector stack
11. Validate technical void
12. Validate RVE
13. Validate structural exits
14. Combine minimal winning components
15. Walk-forward and out-of-sample validation
```

This order is designed to minimize confounding.

---

# 178. First Research Milestone

The first meaningful milestone is not a polished full strategy.

It is answering:

> On NSE equities, does persistent volatility-adjusted relative strength/weakness versus a broad benchmark contain statistically useful information about subsequent intraday returns?

If the answer is no, the foundation must be reconsidered before building more layers.

---

# 179. Second Research Milestone

If RRS demonstrates useful separation:

> Does conditioning RRS on market direction, Daily trend, sector strength, and contextual volume improve after-cost expectancy robustly out of sample?

Only then should the complete V1 setup logic be promoted.

---

# 180. Final Strategy Principle

The first Edge Relative strategy can be summarized as:

> **Trade the market first, then trade the stock that is proving it can outperform or underperform that market, prefer sector agreement and strong participation, require Daily and intraday structural alignment, wait for technical confirmation, define invalidation from the thesis, and exit when the thesis is no longer true.**

In system form:

```text
Market establishes permission.
Sector strengthens context.
RRS identifies independent stock behavior.
Volume validates participation.
Daily structure establishes directional quality.
M5 structure establishes timing.
Confirmation creates the setup.
Risk determines capital.
Execution implements the trade.
ML may later rank the valid opportunity.
```

That is the foundational algorithm and research specification for `ER_RS_CONTINUATION_V1`.
