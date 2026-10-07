# DD-07 — UI / UX Architecture

Status: proposed. This document defines the operator workstation's information architecture,
vocabulary, workspace boundaries, screen contracts, and visual semantics. It constrains what the
frontend may show and how it must behave; it grants no trading authority.

## 1. Product posture

Edge Relative is a small systematic trading desk, not a technical-analysis dashboard. The UI must
express the decision chain and keep risk above opportunity:

```
DATA TRUST → SYSTEM READINESS → MARKET → SECTOR → STOCK → SETUP → OPPORTUNITY RANK
→ RISK → TRADE PLAN → EXECUTION → POSITION → EXIT → REVIEW
```

Operating modes (DD-01 §12): the interface must always state the current mode, what actions are
permitted, whether orders can reach a broker, and whether ML has authority. Until a mode endpoint
exists the badge is a **derived, non-authoritative** display labelled as such.

## 2. Workspaces

Two mental models, never mixed:

- **TRADE** — the operator: is the system safe, what is the market doing, where is relative
  strength/weakness developing, which opportunity matters, can risk permit it, what is the plan,
  what positions exist, has the thesis changed.
- **RESEARCH** — the researcher: is the data complete, how is a feature behaving, how does the
  strategy perform, why were trades generated/rejected, is a strategy version ready.
- **SYSTEM** — readiness, connections, data health, risk posture, configuration, audit.

## 3. Navigation

```
TRADE
  /desk                      command center
  /opportunities             ranked opportunity board
  /watchlist                 decision watchlist
  /positions                 positions + thesis (broker read-only)
  /instrument/:symbol        unified Instrument Workspace
RESEARCH
  /research/data             acquisition / coverage / backfill
  /research/backtests        launcher + runs
  /research/backtests/:key   run workspace
  /research/strategies       strategy catalog (definition/versions/evidence)
SYSTEM
  /system/risk               Risk Center
  /system/health             readiness + data-health matrix
  /system/config             bindings, risk policies, calendars
```

Legacy paths (`/overview`, `/setups`, `/chart/:symbol`, `/features`, `/backtests`, `/strategies`,
`/market`) remain as redirects. Analytical state lives in URL query parameters (sort, direction,
filters, timeframe, selected run/instrument) so views are shareable and reproducible.

## 4. Domain vocabulary (enforced in copy and component names)

| Term | Meaning |
|---|---|
| Feature | A measurement (RRS, RVOL, RVE, ATR, structure). |
| Setup | A strategy lifecycle state (WATCH → FORMING → NEAR_TRIGGER → VALID …). |
| Opportunity | A setup presented/ranked for operator attention. |
| Risk decision | Permission and sizing decision. |
| Trade plan | Frozen, approved trading intent. |
| Order | An execution instruction. |
| Position | Executed exposure. |

Forbidden overloads: "Trades" is not a synonym for broker account (use Positions) or for backtest
trades; "Validate" means Backtests; "Catalog" means Strategies; "Chart" means Instrument.

## 5. Visual semantics (separate axes)

Color is never the only carrier of meaning. These are independent axes:

- **Direction** — LONG / SHORT (glyph + label, not color alone).
- **Numeric direction** — sign of a value (RRS −1.8). Distinct from alignment.
- **Strategy alignment** — whether a value supports the setup (RRS −1.8 may be Strong for a SHORT).
- **Quality / trust** — VALID · WARMING · STALE · MISSING · DEGRADED · INVALID.
- **Severity** — INFO · ATTENTION · WARNING · BLOCKED · CRITICAL.
- **System health** — READY · DEGRADED · BLOCKED · OFFLINE.
- **P&L** — positive/negative.

Missing data must never render as a normal zero.

## 6. Screen contracts

**Desk** — readiness header; market/sector context; ranked opportunity board; active positions; risk
panel; attention feed.

**Opportunity Board** — columns: rank, symbol, direction, setup lifecycle, market/sector alignment,
RRS (+ momentum), RVOL, RVE, risk state, plan state, entry proximity, freshness. Rank is a
**deterministic rule** over maturity/proximity/momentum, labelled "deterministic", never an invented
score.

**Watchlist** — decision-first columns with saved views (Decision, Momentum, Risk, Position, Data,
Raw) and intent sort (rank, maturity, RRS, acceleration, RVOL, RVE, proximity, freshness).

**Instrument Workspace** — persistent header (price, sector, setup, rank, position, risk, data age);
decision summary (Market/Sector/Stock/Setup/Risk/Plan); Why (Strategy | ML | Risk separated); chart
with lifecycle/entry/stop/target markers; evidence tabs below.

**Positions** — per position: direction, quantity, average fill, current price, unrealized/realized
P&L, current R, protective stop vs structural invalidation (visually distinct), thesis state, and an
explicit "Why hold?" with exit conditions.

**Risk Center** — net liquidation value, daily/realized/unrealized P&L, session drawdown, risk
used/limit/remaining, gross/net exposure, reserved/open risk, concentration by sector/symbol/strategy,
blocked reasons; each metric as CURRENT / LIMIT / REMAINING. **When the live risk context producer is
absent, the screen states that explicitly and shows only per-plan/candidate risk that exists.**

**Backtest Run** — Summary, Equity/DD, Funnel, Diagnostics, Instruments, Trades, Timeline,
Configuration, Lineage. Zero-trade runs must be the most informative state, not the emptiest.

**System Health** — per-source data-health matrix (status/age/required), connections, engine
readiness, with honest availability.

## 7. Honesty rules

- No fabricated values, probabilities, or ML confidence. Deterministic scores are labelled as such.
- Where a producer or endpoint does not exist, the UI says so and names the reason; it never
  substitutes a plausible-looking default.
- Data freshness, observed-at, valid-until, and expiry are shown wherever a value has a short life.
- Execution is disabled in the current product; all mutation surfaces are labelled advisory.

## 8. Accessibility, density, responsiveness

- Keyboard-first: command palette, table navigation, focus trapping in sheets/dialogs, ARIA labels,
  reduced-motion support.
- Density tokens shared across tables (compact for trading/scanner, comfortable for investigation).
- Desktop-first (1440–1920+); small screens prioritize monitoring, alerts, position and risk status.

## 9. Component architecture

Domain components extracted from page monoliths: `SystemReadinessBar`, `TradingModeBadge`,
`MarketContextPanel`, `SectorBoard`, `OpportunityTable`, `SetupLifecycle`, `DecisionGateList`,
`RiskCapacityPanel`, `TradePlanSummary`, `PositionThesisCard`, `FeatureMetric`, `DataTrustBadge`,
`AttentionFeed`, `BacktestFunnel`, `BacktestTimeline`, `RunLineagePanel`.

## 10. API dependencies

The UI may only present data that exists. Current availability (see `dev/ui-ux-audit.md` §API gap):
features, watchlist, opportunities, trade plans, broker positions/orders/margin, history coverage,
backtests (metrics/rejections/timeline/aggregate/universe), catalog, fundamentals, strategy bindings,
`/actuator/health`. Missing producers: live portfolio risk context, current trading mode, sector
aggregation, global market regime, attention events, canonical position/order lifecycle.
