# UI / UX Audit and Redesign Plan

Companion to `../DD07 - UI UX Architecture.md`. Records the current-state audit, the API data-source
gap table, and the phased UI-only implementation plan.

## 1. Current screen audit

| Route / view | Purpose now | Structural problem | Verdict |
|---|---|---|---|
| `/desk` `OverviewView` | tiles + top-5 | shallow; "Open scanner" links to `/setups`; no error UI | Rebuild as command center |
| `/watchlist` `WatchlistView` | quote monitor + CRUD | not decision-oriented | Redesign columns/views |
| `/scanner` `FeatureDashboardView` | feature board | engineering name; embedded in Setups | Keep engine, rename context |
| `/setups` `OpportunitiesView` | setup/risk/plan | name ≠ route; embeds Scanner | Rename Opportunities |
| `/chart` + `/chart/:symbol` | search + tab warehouse | dense, overlapping FeatureTicker | Merge into Instrument Workspace |
| `/trades` `TradesView` | broker account | "Trades" overloaded; broker-generic | Rename Positions/Account |
| `/research` `ResearchDataView` | backfill ops | technical ops in trading area | Keep under Research |
| `/validate` `BacktestsView` | form + list + preview | duplicates `BacktestRunView` | Split launcher/run |
| `/validate/:runKey` `BacktestRunView` | run detail | no funnel/diag/config/lineage | Expand |
| `/catalog` `StrategiesView` | strategy/risk config | no detail route; config vs evidence | Split by audience |
| `/fundamentals` | screener + narration | must not imply intraday authority | Keep, demote timing |
| Shell (sidebar/topbar/statusbar/palette) | minimal | mode only in footer; System/Journal "Soon" | Rebuild |

Duplicates / dead: backtest run detail (two implementations, two catalog cache keys); instrument
search (4 surfaces); `FeatureHistoryPanel` (3×); `FundamentalPanel` (2×); unused `DataTable`,
`QuoteSummary`, `InstrumentHeader`.

## 2. API data-source gap table

| Capability | Source | Status | UI handling |
|---|---|---|---|
| Features RRS/RVOL/RVE/ATR/context | `/api/v1/features/*` | Available | use |
| Market context (structure/efficiency) | features `MARKET_*` (no regime label) | Partial | derive; label "derived" |
| Sector board | `/api/v1/features/dashboard` `sectorCode`/`sectorName` | Available | group client-side; `SectorBoard` |
| Opportunity list | `/api/v1/opportunities` | Available (no rank/score) | deterministic rank rule, labelled |
| Setup detail | `/api/v1/setups/{id}` | Available (no client) | add client |
| Trade plans | `/api/v1/trade-plans/{key}` | Available | use |
| Strategy bindings | `/api/v1/strategy-bindings` | Available (no client) | add client |
| Positions/orders/margin | broker `/portfolio/*`, `/orders/*` | Available (read-only) | use; canonical model absent |
| System readiness | `/actuator/health` (status only) | Partial | aggregate client-side; detail unavailable |
| Trading mode | `/api/v1/system/mode` (declared mode + control state) | Available | authoritative badge |
| Risk Center | `/api/v1/risk/posture` (portfolio/account/control, or unavailable) | Available (empty until producers) | render available/unavailable explicitly |
| Attention/transitions | none | Missing | derive from feature stream diffs |
| Backtest funnel/diagnostics | `metrics.stageCounts`, `/rejections`, `/timeline` | Available | restructure UI |
| Data-health matrix | feature diagnostics + history coverage | Partial | derive per-instrument/meta |
| Fundamentals / LLM | `/api/v1/fundamentals`, `/api/v1/llm` | Available | use |
| Backtest sweep | `/api/v1/backtests/sweep` | Available (no client) | optional |

## 3. Phased implementation (UI-only)

1. **Foundation** — IA/router + redirects; URL state; DD07 vocabulary; semantics tokens;
   `SystemReadinessBar`, `TradingModeBadge`, `DataTrustBadge`; shell groups; add clients for
   `/setups`, `/strategy-bindings`, `/backtests/{run}/rejections`; remove dead components.
2. **Core trading** — Desk; Opportunity Board; SectorBoard; MarketContextPanel; Watchlist redesign;
   Instrument Workspace merge; Positions; Risk Center (honest unavailable).
3. **Forensics** — Why (strategy/ML/risk), `DecisionGateList`, `SetupLifecycle`, `AttentionFeed`,
   `PositionThesisCard`, inline system warnings, severity model.
4. **Research** — Backtest run workspace (summary/funnel/diagnostics/instruments/timeline/config/
   lineage), zero-trade debugging, consolidate duplicate run view and catalog keys.
5. **Operations** — System health matrix, alerts, audit/config stubs.

Status: phases 1–5 are implemented as UI-only changes. Where the gap table reads "Missing" or
"Partial" the UI renders an honest derived or unavailable state rather than a fabricated value.

## 4. Verification

`pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` after each phase. Tests cover decision
semantics (healthy, stale-data blocked, no setups, WATCH/NEAR_TRIGGER/VALID, risk rejected/approved,
plan expired, thesis weakening, zero-trade backtest, feature stale, broker unavailable).
