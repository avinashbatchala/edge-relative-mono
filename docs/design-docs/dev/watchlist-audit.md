# Watchlist management and eligibility — audit

Status: executed against the running application and persisted rows.
Scope classification: Core / implementation inspection.

## Environment and provenance

| Item | Value |
| --- | --- |
| Commit | `173ac9c` + the fixes/script/report in this change |
| Backend | Spring Boot 4.0.8 on `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Baseline watchlist | 8 real entries; fixtures `ERWL01..ERWL20`, `ERWLC1/C2` (cleared before and after) |
| Account scope | single operator tenant (no auth layer) |
| Rerunnable script | `scripts/watchlist-audit.sh` (`BASE=… PG=… scripts/watchlist-audit.sh`) |

Sources: DD01 §§14–17 (single active 20-stock watchlist, active vs learning universe), DD02
§§141–142 (entry eligibility), DD05 (active universe controls authority, not historical identity).

## Endpoint / boundary map

| Layer | Surface |
| --- | --- |
| Public HTTP | `GET /api/v1/watchlist`, `POST /api/v1/watchlist/items`, `DELETE /api/v1/watchlist/items/{instrumentId}`, `PUT /api/v1/watchlist/order` |
| Application | `WatchlistService` (cap, duplicate, slot, reorder), `isWatched` guard used by history backfill |
| Schema | `operational.watchlist` (single active per owner), `watchlist_item` (`slot` 1..20, `pinned`, `suspended`, `derivatives_allowed`, `strategy_eligibility`, `notes`) |
| Not exposed | pin / suspend / resume / annotate / derivatives / per-strategy eligibility (columns exist, no endpoints) |

## Scenario table

Evidence: `scripts/watchlist-audit.sh` (14/14 PASS) and `WatchlistIntegrationTest`.

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| WL-01 | Baseline count | GET watchlist | ≤ 20 | 8 | PASS | `baseline-count` |
| WL-02 | Fill to cap (DD01 §14) | add `ERWL01..20` | 20 entries | 20 | PASS | `fill-to-cap-20` |
| WL-03 | Cap at 21 (DD01 §14) | add `ERWL21` | 409 `WATCHLIST_FULL` | 409 | PASS | `cap-21st-rejected` |
| WL-04 | Duplicate (even when full) | re-add `ERWL01` | 409 `WATCHLIST_DUPLICATE` | 409 | PASS | `duplicate-rejected` |
| WL-05 | Concurrent adds at cap | free one slot, two parallel adds | one 201, one 409, count 20 | 201/409, count 20 | PASS | `concurrent-cap` |
| WL-06 | Reorder persisted and reflected | `PUT /order` reversed ids | API order reversed | match | PASS | `reorder-persisted` |
| WL-07 | Removal preserves history identity | delete one fixture | reference row retained, watchlist row gone | retained/gone | PASS | `remove-preserves-identity` |
| WL-08 | Reload consistency | two GETs | identical | identical | PASS | `reload-consistent` |
| WL-09 | Add/list/remove/reorder supported | mixed mutations | consistent | consistent | PASS | script + `WatchlistIntegrationTest` |
| WL-10 | Pin / suspend / resume / annotate / derivatives | `POST .../pin`, `.../suspend`, `PUT .../notes`, `PUT .../derivatives` | defined behaviour | 404 (no endpoint) | NOT_IMPLEMENTED | `unsupported-*` |
| WL-11 | Suspended-entry semantics (DD02 §§141–142) | suspended item | excluded from new-entry eligibility | no suspension state in API | NOT_IMPLEMENTED | Limitations |
| WL-12 | Cross-tenant / unauthorized ownership | other tenant id | rejected/isolated | no auth/tenant header surface | NOT_IMPLEMENTED | Limitations |
| WL-13 | Remove symbol with an open position | position open | protection continues, new entries blocked | no positions/execution module | NOT_IMPLEMENTED | Limitations |
| WL-14 | Broader learning universe (DD05) | non-active instruments | retained for learning only | only the active watchlist is modelled | NOT_IMPLEMENTED | Limitations |

Counts: PASS 9, NOT_IMPLEMENTED 5, FAIL 0.

## Defect register (failed-before → passed-after)

| # | Severity | Impact | Repro | Before → After | Root cause | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- |
| W1 | Low | Re-adding an already-watched symbol while the list was full reported `WATCHLIST_FULL`, hiding the real duplicate and misleading the operator | Fill to 20, re-add an existing symbol | 409 `WATCHLIST_FULL` → 409 `WATCHLIST_DUPLICATE` | capacity check ran before the duplicate check in `WatchlistService.add` | check duplicate first, then capacity | `duplicate-rejected`; `WatchlistIntegrationTest.duplicateTakesPrecedenceWhenTheWatchlistIsFull` |

No cap-overrun defect was reproduced: the race resolves to one 201 and one 409, and the database
unique slot constraint plus the Java cap keep `count <= 20`.

## Independent recount / math

- Authoritative recount from `operational.watchlist_item` equals the API `count` before and after
  every mutation (fill 20, then 20, concurrency `before=19 after=20`).
- Cap = 20 active entries (DD01 §14); the 21st distinct symbol is rejected.
- The race at the cap produced exactly one success (`409/201`) rather than 21 entries.

## Continuity and lineage analysis

- Storage ↔ API order: `slot` is unique per watchlist; reorder rewrites slots and the API returns
  them in slot order (`reorder-persisted`).
- Watchlist ↔ history: removing a watchlist row deletes only `operational.watchlist_item`; the
  canonical `reference.instrument` (and any `market.candle` keyed by `instrument_id`) is retained
  (`remove-preserves-identity`). `isWatched` is a guard for backfill, not identity.
- Watchlist ↔ features/Opportunities: the Feature Dashboard and Opportunities iterate the active
  watchlist; they read canonical identity, so removal changes displayed scope but never rewrites
  history.
- **Gap**: `suspended`, `pinned`, `derivatives_allowed`, `strategy_eligibility` and `notes` exist in
  the schema but have no API or eligibility enforcement; entry eligibility currently means merely
  "on the active watchlist".

## Verification commands

- `scripts/watchlist-audit.sh` → 14/14 PASS.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=WatchlistIntegrationTest` → 7/7.
- `./mvnw -Denforcer.skip=true verify` → application 192, broker-groww 79.
- Browser: not exercised (no tooling); API/DB only.

## Limitations and next actions

- Expose and enforce `pinned`/`suspended`/`derivatives_allowed`/`strategy_eligibility`/`notes`
  (DD02 §§141–142), and make suspended entries non-eligible for new entries while remaining readable.
- Add tenant/account scoping and authorization before any cross-tenant API surface exists.
- Model the broader learning universe separately from the 20-stock active watchlist (DD05), so
  collection does not grant execution eligibility.
- When positions/execution exist, guarantee that removing a watchlist row leaves protective exits
  active while blocking new entries.
