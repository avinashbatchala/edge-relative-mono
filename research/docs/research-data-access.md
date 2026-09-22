# Research Data Access Layer

- **Status:** implemented (Prompt 03). Dataset construction, manifests and analysis are
  not built yet.
- **Package:** `research/src/edge_relative_research/db/` and
  `research/src/edge_relative_research/repositories/`
- **Companions:** `research-data-contracts.md`, `research-layer-current-state.md`.

This layer reads the existing production schema into typed, immutable rows. It is the
only supported way for Python to obtain research inputs; it never writes production state.

## 1. Authority boundary

| Schema | Python access | Enforcement |
|---|---|---|
| `reference` | read | read-only session (`conn.read_only = True`) |
| `control` | read | read-only session |
| `market` | read | read-only session |
| `operational` | read (observations/evidence only) | read-only session |
| `research` | read now; write in the dataset stage | separate writable session |
| `audit` | not used | n/a |

`ResearchDatabase.read_only()` marks the session read-only, so the server rejects any
write even if a query is mistaken. `test_read_only_connection_rejects_writes` proves an
`INSERT` raises `ReadOnlySqlTransaction`. Python still owns no orders, fills, risk
decisions, trade plans, positions or deployment authority.

## 2. Architecture

```text
DatabaseConfig (env: RESEARCH_DB_* over POSTGRES_*)
      │
      ▼
ResearchDatabase ── read_only() / research_write() ── REPEATABLE READ snapshot
      │
      ▼
QueryExecutor ── parameterized fetch_all / fetch_one / fetch_scalar / stream
      │            └── QueryObserver (duration + row count; in-process only)
      ▼
repositories
  ReferenceRepository   instruments, identifiers, sectors, benchmarks, calendar, CA, universe
  ControlRepository     feature definitions/versions/schemas, strategies, risk policies
  MarketRepository      observations, revisions, quality, candles, coverage, ingestion
  OperationalRepository setups, classifications, trades, orders, fills, plans, predictions, risk
  ResearchRepository    datasets, outcomes, patterns, experiments
```

Repositories return frozen dataclasses (`repositories/models.py`) whose field names match
the SQL aliases. `from_row` fails loudly if a selected column is missing, so schema drift
is caught immediately.

## 3. Query rules

- **Parameterized only.** Every value is bound as a `%s` parameter (or `ANY(%s)` for
  lists). `where_clause` joins pre-written predicates; no value is interpolated into SQL
  text. `test_symbol_lookup_is_parameterized` passes `"RELIANCE' OR 1=1 --"` and gets no
  row.
- **Deterministic ordering.** Every list query has an explicit `ORDER BY` including a
  unique tiebreaker (e.g. `observed_at, setup_observation_id`).
- **Explicit boundaries.** Time ranges are half-open `[from, to)`, so a boundary bar is
  never double counted.
- **As-of temporal reads.** Effective-dated reference reads require an `as_of` date and
  return only rows where `valid_from <= as_of AND (valid_to IS NULL OR valid_to > as_of)`,
  so a future mapping can never be returned early.
- **Streaming.** `QueryExecutor.stream` uses a named server-side cursor with `itersize`,
  exposed as `MarketRepository.iterate_candles` for large series; it is instrumented with
  the number of rows actually consumed.
- **Instrumentation.** Each call records a `QueryStats` (name, SQL, row count, duration,
  streamed flag) to an observer. The default is a no-op; tests use `CollectingObserver`.
- **Consistent snapshot.** Read connections use `REPEATABLE READ`, so a multi-query
  point-in-time assembly sees one snapshot.

## 4. As-of semantics by dataset

| Temporal data | Table | Key columns |
|---|---|---|
| Symbol / identifier change | `reference.instrument_identifier` | `valid_from`, `valid_to` |
| Sector change | `reference.instrument_sector_history` | `valid_from`, `valid_to` |
| Benchmark membership | `reference.benchmark_constituent_history` | `valid_from`, `valid_to` |
| Sector → benchmark | `reference.sector_benchmark_history` | `valid_from`, `valid_to` |
| Universe membership | `reference.universe_membership_history` | `valid_from`, `valid_to` |
| Corporate-action factors | `reference.corporate_action_factor` | `available_at <= as_of` |
| Broker token mapping | `reference.broker_instrument_mapping` | `valid_from`, `valid_to` (timestamptz) |

`ReferenceRepository` exposes `identifier_as_of`, `resolve_symbol`, `sector_as_of`,
`benchmark_constituents_as_of`, `benchmark_membership_for_instrument`,
`corporate_action_factors_as_of`, `universe_members_as_of`, and the calendar/session reads.

## 5. Setup classification

`OperationalRepository.classify_setup` / `setup_classifications` join a setup observation
to its latest risk decision (and first rejection reason), latest trade plan and latest
trade via `LATERAL` subqueries, yielding one row per setup. This lets research distinguish:

| Case | Marker |
|---|---|
| Valid executed | `setup_status='VALID'`, `risk_decision='APPROVE'`, `trade_id` present |
| Valid skipped | `VALID` with no decision or no trade |
| Risk rejected | `risk_decision='REJECT'` + `risk_primary_reason_code` |
| Invalid / blocked | `setup_status` in `NONE/WATCH/BLOCKED` with gate failures in `explanation` |
| Near-trigger | `setup_status='NEAR_TRIGGER'` |
| Missed | `setup_status='MISSED'` |

## 6. Testing

`tests/conftest.py` starts a `postgres:18.4-bookworm` container with the Python
`testcontainers` library, applies every backend Flyway migration in order
(`backend/application/src/main/resources/db/migration/V001…V021` + repeatable), and runs
`tests/fixtures/seed.sql` in a single transaction. The seed exercises temporal changes
(symbol, sector, benchmark membership), a partial fill with two fills, a rejected setup,
market observations/revisions/candles, control versions and research metadata.

If Docker is unavailable the database fixtures `pytest.skip` rather than fail.

Required coverage (all present in `tests/test_db_access.py`):

| Requirement | Test |
|---|---|
| Symbol changed historically | `test_symbol_changed_historically` |
| Sector changed historically | `test_sector_changed_historically` |
| Historical benchmark membership | `test_historical_benchmark_membership` |
| Setup observations ordered | `test_setup_observations_ordered_and_bounded`, `test_deterministic_ordering` |
| Multiple fills for one order | `test_multiple_fills_for_one_order` |
| Partial fill | `test_partial_fill` |
| Skipped/rejected classification | `test_skipped_and_rejected_setup_classification` |
| No future temporal mapping | `test_no_future_temporal_mapping_returned` |
| Parameterized queries | `test_symbol_lookup_is_parameterized` |
| Read-only boundary | `test_read_only_connection_rejects_writes` |
| Instrumentation | `test_query_instrumentation_records_duration_and_rows`, `test_streaming_is_instrumented` |

Unit coverage (`tests/test_db_unit.py`) covers configuration precedence, DSN secret
handling, `where_clause` parameterization, observer totals and missing-column detection.

## 7. Limitations and next steps

- Reads only. Dataset creation, immutable manifests, checksums and commit semantics are
  the next stage; `ResearchRepository` currently only reads metadata.
- The `market.feature_snapshot*` tables have no read adapter yet; feature inputs are
  reconstructed by replaying the Java feature engine or reading snapshots in a later
  stage. Feature snapshots are not persisted by ingestion today.
- The integration seed is intentionally small and synthetic; it is not a research dataset.
- Line length for this package is 100 to keep explicit SQL column lists readable.
- No connection pooling or distributed monitoring is introduced by design; a single
  connection per read scope is sufficient at the current scale.
