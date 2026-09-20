# Trading calendar and session lifecycle — audit

Status: executed against the running application and persisted candles, plus a host-timezone test
run. Scope classification: Core / implementation inspection.

## Environment and provenance

| Item | Value |
| --- | --- |
| Commit | `94ca2f5` + the test/script/report in this change |
| Backend | Spring Boot 4.0.8 on `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Calendar | `NseTradingCalendar` (`nse-session-v1`), fixed 09:15–15:30 IST, weekends + configured 2026 holidays (`market.calendar.holidays`) |
| Instrument | SBIN (`instrumentId=1`) |
| Rerunnable script | `scripts/calendar-audit.sh` (`BASE=… PG=… scripts/calendar-audit.sh`) |

Sources: DD01 §§131, 133–136 (session lifecycle, entry/flatten), DD05 §§44–45, 93–104, 115–116
(session semantics, aggregation, host-timezone independence), DD02 (entry and flatten cutoffs).

There is **no public calendar endpoint**. The calendar is a pure application boundary; it was audited
through the canonical candle API (session-anchored slots), direct database evidence, and the unit
test — labelled accordingly.

## Boundary map

| Layer | Surface |
| --- | --- |
| Calendar | `NseTradingCalendar` (`sessionDate`, `sessionOpen/Close`, `sessionMinutes`, `weekStart`, `isTradingDay`), `MarketCalendarProperties.holidays` |
| Aggregation | `CandleAggregator` (intraday buckets anchored at `sessionOpen`, excludes `>= close`, `partial` flag), `SessionModel` (`slotIndex`, `expectedBars`) |
| Strategy | `StrategyEngine.session` gate (`tradingDay`, `openingBlackout`, `entryCutoffReached`, `entryWindowOpen`) |
| Risk | `RiskContext.SessionWindow`; `RiskEvaluator` session/flatten-window preconditions |
| UI | IST offset applied consistently in charts (`PriceChart.IST_OFFSET_SECONDS`) |

## Scenario table

Live evidence: `scripts/calendar-audit.sh` (7/7 PASS). Unit: `NseTradingCalendarTest` (5 tests, run
under two host zones).

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| CAL-01 | Normal session slots (DD05 §93) | SBIN M5 on 2026-09-04 | 75 bars, first 03:45Z, last close 10:00Z | 75, 03:45Z..10:00Z | PASS | `normal-session-slots` |
| CAL-02 | Holiday excluded (DD05 §44) | 2026-09-14 (Ganesh Chaturthi) | no session bars | 0 | PASS | `holiday-no-session` |
| CAL-03 | Weekend excluded | 2026-09-19 | no session bars | 0 | PASS | `weekend-no-session` |
| CAL-04 | Session instants (DD01 §133) | `sessionOpen/Close(2026-01-27)` | 03:45Z / 10:00Z | match | PASS | `NseTradingCalendarTest` |
| CAL-05 | Week boundary | `weekStart(Mon)` | Monday | Monday | PASS | `NseTradingCalendarTest` |
| CAL-06 | Host timezone independence (DD05 §115) | run test under `America/New_York`, `Asia/Kolkata` | identical absolute instants | pass in both | PASS | `host-tz-*` |
| CAL-07 | `sessionDate` boundary | `18:30Z` | next IST day | 2026-09-19 | PASS | `NseTradingCalendarTest` |
| CAL-08 | Session length | `sessionMinutes()` | 375 | 375 | PASS | `sessionLengthIsThreeHundredAndSeventyFiveMinutes` |
| CAL-09 | Session completeness vs M1 | 2026-09-18 SBIN | 75 bars if 375 M1 minutes | M1=370 → M5=74 (real gap) | PASS (observed) | `session-coverage-observed` |
| CAL-10 | Special/shortened sessions (DD05 §104) | half-day / Muhurat | override close, partial final bar | no override exists | NOT_IMPLEMENTED | Limitations |
| CAL-11 | Entry blackout / cutoff / flatten producers (DD02) | intraday | computed from calendar | callers pass false; not derived | NOT_IMPLEMENTED | Continuity |
| CAL-12 | Pre-market readiness & reconciliation lifecycle (DD01 §134–136) | session start | warm-up + reconciliation gate | no lifecycle job/scheduler | NOT_IMPLEMENTED | Limitations |
| CAL-13 | Idempotent/repeated lifecycle jobs | restart mid-session | no duplicate effects | no job to repeat | NOT_IMPLEMENTED | Limitations |

Counts: PASS 9, NOT_IMPLEMENTED 4, FAIL 0.

## Defects

No calendar/session defect was reproduced. CAL-09 is a **data-quality** observation, not a bug: the
canonical store is missing 5 M1 minutes (15:20–15:25 IST) on 2026-09-18, so `CandleAggregator`
correctly produces 74 M5 bars and marks the session incomplete rather than fabricating the missing
slot (`inSession` excludes minutes outside `[open, close)`; `completeness` compares received minutes).

## Independent math / reconciliation

- 09:15 IST = `2026-09-18T03:45:00Z`; 15:30 IST = `2026-09-18T10:00:00Z` (IST = UTC+05:30).
- Session length = 375 minutes → 75 M5 bars (`SessionModel.expectedBars = 375/5`).
- `sessionDate(2026-09-18T18:30:00Z)` = `2026-09-19` (midnight IST).
- Candle slots matched the API exactly on a full session (CAL-01) and were absent on holiday/weekend
  (CAL-02/03).

## Continuity and lineage analysis

- Calendar → candle slots: session-anchored buckets start at `sessionOpen` and the API returned
  exactly `03:45Z..10:00Z` (CAL-01); minutes `>= close` are excluded (`CandleAggregator.inSession`).
- Calendar → RVOL slots: `SessionModel` derives `slotIndex`/`expectedBars` from the same calendar, so
  volume baselines and candle buckets share session semantics.
- Calendar → strategy entry permission: `StrategyEngine.session` enforces `tradingDay`,
  `openingBlackout`, `entryCutoffReached`, `entryWindowOpen` — but **no producer computes the blackout
  or cutoff**; the backtest engine hardcodes `openingBlackout=false, entryCutoffReached=false` and
  `entryWindowOpen=tradingDay`, so DD02 cutoffs are not enforced (NOT_IMPLEMENTED).
- Calendar → risk/UI: `RiskContext.SessionWindow` carries the same flags; the UI applies the IST
  offset for display. `sessionClose` currently reaches plan validity only when a trade-plan policy
  supplies `entryCutoffMinutesBeforeClose` (default unset).
- Host timezone: all session math uses `Asia/Kolkata` explicitly (CAL-06).

## Verification commands

- `scripts/calendar-audit.sh` → 7/7 PASS.
- `TZ=America/New_York ./mvnw -pl application -am test -Dtest=NseTradingCalendarTest` → pass.
- `./mvnw -Denforcer.skip=true verify` → application 189, broker-groww 79.
- Browser: not exercised (no tooling); API/DB/unit only.

## Limitations and next actions

- Add versioned session overrides (half-day, Muhurat, ad-hoc closures) so `sessionOpen/Close` and
  `SessionModel.expectedBars` reflect shortened sessions and the `partial` final bar is reachable.
- Add a pre-market lifecycle job (data warm-up + reconciliation) with idempotent, restart-safe
  execution and an overdue-job guard.
- Derive `openingBlackout`, `entryCutoffReached` and the end-of-session flatten window from the
  calendar/policy in a single producer so strategy entry and risk agree, and reset session risk
  state per trading date.
- Keep the holiday list data-driven and refresh it when the annual NSE circular is published.
