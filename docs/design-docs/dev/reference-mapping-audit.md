# Instrument master and temporal reference mapping — audit

Status: executed against the running application and persisted reference tables.
Scope classification: Core / implementation inspection.

## Environment and provenance

| Item | Value |
| --- | --- |
| Commit | `f963b9b` + the fixes in this change |
| Backend | Spring Boot 4.0.8 on `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Fixtures | disposable symbols `ERREF`, `ERREF2`, `ERREF_NOSEG`, `ERREF_ZEROTICK`, `ERREF_ZEROLOT`; cleared before and after each run |
| Rerunnable script | `scripts/reference-audit.sh` (`BASE=… PG=… scripts/reference-audit.sh`) |

Sources: DD01 §§24–25 (stable economic identity, temporal mappings), DD05 §§38–43 (canonical
reference and point-in-time correctness), DD04B §§3, 6–7 (physical schema, half-open ranges,
overlap exclusion).

## Boundary map

| Layer | Surface |
| --- | --- |
| Public HTTP | `POST/GET/DELETE /api/v1/watchlist/items` (creates canonical instrument + broker mapping), `GET /api/v1/watchlist`, `GET /api/v1/brokers/groww/instruments?query=&limit=` |
| Application | `CanonicalInstrumentService.ensureInstrument` / `ensureBrokerMapping`, `WatchlistService`, `FeatureReferenceResolver` (point-in-time sector), `SectorReferenceInitializer` |
| Reference tables | `instrument`, `instrument_identifier` (DATERANGE, EXCLUDE), `broker_instrument_mapping` (TSTZRANGE, EXCLUDE), `instrument_sector_history`, `derivative_contract` |
| No public endpoint | temporal identifier resolution, broker-token rotation for an existing instrument, derivative validation — driven at the application/DB boundary and labelled as such |

## Scenario table

Live/DB evidence: `scripts/reference-audit.sh` (12/12 PASS). Regression: `WatchlistIntegrationTest`.

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| RF-01 | Import instrument (DD01 §24) | POST watchlist item `ERREF` | 200/201, canonical row | 201, `instrument_id` resolved | PASS | `import-create`, `canonical-id-resolved` |
| RF-02 | Duplicate request is safe | re-POST `ERREF` | 409, no partial mutation | 409 `WATCHLIST_DUPLICATE`, token intact | PASS | `duplicate-add-no-mutation` |
| RF-03 | Broker token change / alias continuity (DD04B §7) | reuse token A on `ERREF2` | close A on ERREF, open A on ERREF2 | closed=1, open=1 | PASS | `token-reuse-half-open` |
| RF-04 | Half-open adjacency | A ranges | `ERREF.valid_to = ERREF2.valid_from` | `t` (adjacent, no overlap/gap) | PASS | `token-reuse-half-open` |
| RF-05 | Current token resolved | GET watchlist | current alias only | `brokerSymbol=NSE_ERREF_A` | PASS | `current-token-resolved` |
| RF-06 | DB overlap protection (DD04B §6) | overlapping mapping insert | EXCLUDE violation | rejected | PASS | `mapping-overlap-rejected` |
| RF-07 | Half-open `[valid_from, valid_to)` | identifier `[2026-01-01, 2026-07-01)` | in at start, out at end | start=1, end=0 | PASS | `half-open-boundary` |
| RF-08 | Unknown identifier explicit | search `ZZZ_NO_SUCH…` | empty, not whole master | 0 results | PASS | `unknown-identifier-empty` |
| RF-09 | Missing segment rejected | POST without segment | 400 | 400 `WATCHLIST_INVALID` | PASS | `missing-segment-400` |
| RF-10 | Zero tick rejected | `tickSize=0` | 400 | 400 | PASS | `zero-tick-400` |
| RF-11 | Zero lot rejected | `lotSize=0` | 400 | 400 | PASS | `zero-lot-400` |
| RF-12 | Regression suite | `WatchlistIntegrationTest` | green | 6/6 | PASS | backend `verify` |

Counts: PASS 12, FAIL 0.
Not implemented (see below): RF-13 symbol-change alias identity, RF-14 derivative contract validation.

## Defect register (failed-before → passed-after)

| # | Severity | Impact | Repro | Before → After | Root cause | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D1 | High | A duplicate watchlist add silently discarded a broker-token change (the mapping update was rolled back with the duplicate rejection), so historical alias rotation never applied | Re-add an existing watched instrument with a new `brokerSymbol` | 409 and mapping unchanged, but the update/mutation occurred then rolled back → now no mutation attempted after guards | `ensureBrokerMapping` ran before the duplicate check inside one `@Transactional` method | reorder: validate → guards → reference mutation | `duplicate-add-no-mutation`, `token-reuse-half-open` |
| D2 | Medium | Missing `segment` raised a NullPointerException → HTTP 500 | POST watchlist item without `segment` | 500 → 400 `WATCHLIST_INVALID` | `validate` did not require `segment`; `ensureInstrument` call `.trim()` on null | require `segment` | `missing-segment-400`, `WatchlistIntegrationTest` |
| D3 | Medium | `tickSize=0` hit the DB `tick_size > 0` check → HTTP 500 | POST with `tickSize=0` | 500 → 400 | no boundary validation vs reference constraint | reject non-positive tick | `zero-tick-400` |
| D4 | Medium | `lotSize=0` was silently coerced to 1, creating a different contract than requested | POST with `lotSize=0` | 201 (coerced) → 400 | `ensureInstrument` defaulted `lot<=0` to 1 | reject non-positive lot | `zero-lot-400` |

## Independent math / temporal reconciliation

- Half-open range `[valid_from, valid_to)` verified with `validity @> DATE` at the exact start
  (included) and exact end (excluded): `start=1, end=0`.
- Adjacency verified by equality `A.valid_to = B.valid_from` on the token-reuse rotation
  (`adjacent=t`), so there is no gap and no overlap.
- Overlap protection is database-enforced by `ex_gist` EXCLUDE constraints on
  `broker_instrument_mapping`, `instrument_identifier` and `instrument_sector_history`
  (confirmed by a rejected overlapping insert).

## Continuity and lineage analysis

- Identity is the deterministic `instrument_key` (`exchange:segment:type:symbol`); the instrument id
  is stable across duplicate calls (`canonical-id-resolved`, RF-01/RF-02).
- Historical candle reads (`market.candle`) are keyed by `instrument_id`, so they never resolve via a
  current alias; the broker-symbol join is display-only and filtered to `valid_to IS NULL`
  (`WatchlistService.fetchEntries`, `HistoryRepository.candles` metadata).
- Sector membership is resolved point-in-time (`FeatureReferenceResolver`: `valid_from <= date AND
  (valid_to IS NULL OR date < valid_to)`), so features do not fall back to today's sector.
- **Gap**: `reference.instrument_identifier` (temporal aliases, ISIN, …) is defined but **unused** by
  any Java code. A canonical-symbol change therefore derives a new `instrument_key` and creates a
  second economic instrument — contrary to DD01 §§24–25. Marked NOT_IMPLEMENTED.
- **Gap**: `reference.derivative_contract` has no producer/consumer; underlying/expiry/strike/type
  consistency is not validated. Marked NOT_IMPLEMENTED.
- Broker-token rotation for an existing instrument has no dedicated public endpoint (only the
  watchlist import path, which cannot re-add a watched instrument); temporal rotation was therefore
  exercised via token reuse across a second instrument and direct DB boundary checks.

## Coverage and non-applicable classes

Exercised: create idempotency, duplicate rejection, token reuse/rotation, current-alias resolution,
half-open boundaries, EXCLUDE overlap protection, unknown identifier, and invalid segment/tick/lot.
Non-applicable / not implemented: temporal identifier resolution endpoint (none exists), derivative
contract validation, unauthorized ownership (no auth layer), symbol-change continuity.

## Verification commands

- `scripts/reference-audit.sh` → 12/12 PASS.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=WatchlistIntegrationTest` → 6/6.
- `./mvnw -Denforcer.skip=true verify` → application 189, broker-groww 79.
- Browser: not exercised (no tooling); API/DB only.

## Limitations and next actions

- Implement temporal identifier resolution (`instrument_identifier`) and route `ensureInstrument`
  through ISIN/alias identity so a symbol change does not create a second economic instrument
  (DD01 §§24–25).
- Add a deliberate broker-token rotation operation and a derivative-contract import/validation path.
- Add an authentication/ownership layer before any temporal-resolution endpoint is exposed.
