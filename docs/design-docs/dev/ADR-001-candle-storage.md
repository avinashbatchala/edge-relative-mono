# ADR-001: Canonical Candle Storage (PostgreSQL first, Parquet deferred)

- **Status:** Accepted
- **Date:** 2026-09-20
- **Supersedes:** the blanket "historical candles belong in Parquet" reading of
  `DD04B` for the current single-operator scope. It does not change the DD-05
  logical candle contract.

## Context

DD-04A/DD-04B assign large append-oriented analytical history (raw ticks, quotes,
depth, `M1/M5/... candles`, feature matrices, embeddings, outcome labels) to
Parquet/object storage, with PostgreSQL keeping manifests and sparse relational
anchors (`DD04B` §2.2, §16; `DD05` §76). DD-05 still fixes the logical candle
contract and construction rules (`DD05` §91–§106).

The current product scope is one operator, one primary account, and a trading
watchlist bounded below 50 instruments (typically ~30). The canonical base is M1;
higher timeframes are derived on read (`DD05` §94, §97). At this scale M1 is:

- ~94k rows per instrument per year (375 session minutes × ~250 sessions);
- <10M rows for 5 years across ~30 instruments — comfortably within PostgreSQL.

Parquet's benefits (columnar compression, predicate pushdown, analytics-native
reads, immutable versioned files, offloading the operational database) are only
material at a much larger universe or with wide feature/ML matrices.

## Decision

1. **PostgreSQL is the authoritative canonical candle store for the watched
   universe.** No Parquet writes and no dual-write at present.
2. **M1 is the only persisted series**; every higher timeframe is derived in
   process by the shared deterministic aggregator.
3. **Parquet adoption is deferred** until a trigger below is met. When adopted,
   PostgreSQL remains the manifest/anchor authority and Parquet becomes the
   analytical history store; the DD-05 candle contract and lineage rules carry
   over unchanged.
4. The candle contract is implemented in PostgreSQL **storage-agnostically**:
   `candle_definition_version`, `close_time`, `is_complete`, `quality_state`,
   `source_revision`, and revision lineage are modelled now so the logical model
   does not change when storage moves.

### Deferral triggers (any one)

- Learning universe grows beyond ~200 instruments (research breadth).
- Persisted candle rows exceed ~100M, or M1 history depth grows well beyond the
  current range.
- Python/ML research needs multi-symbol, multi-year scans that make PostgreSQL
  round-trips the bottleneck.
- Reproducible dataset manifests/checksums/compaction are required for ML or
  regulated lineage.
- PostgreSQL backup/vacuum/size becomes operationally painful.

## Consequences

- The transaction/constraint safety of PostgreSQL applies to candles during this
  phase, which is a net positive for an early, small system.
- This is a deliberate, documented deviation from "candles live in Parquet"; it
  must be revisited at the triggers above rather than silently expanded.
- The candle contract stays as specified in DD-05, so a later storage change is a
  physical migration, not a semantic redefinition.
- Retention/partitioning of `market.candle` is deferred (consistent with
  `DD04B` §15); introduce measured partitioning only if a trigger is reached.
