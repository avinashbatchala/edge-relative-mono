# ADR-002: Feature Snapshot Storage (Scoped)

> **Status:** Accepted for the current single-operator, <50-instrument scope.
> **Date:** 2026-09-20.
> **Related:** DD-05 §120–§158, DD04B §19/§24, `ADR-001-candle-storage.md`.

## Context

DD-05 defines feature definitions, versions, observations, snapshots and schema versions, and is
explicit that derived features must be reconstructable and point-in-time correct. For research to
work without re-ingesting raw data it must be able to read historical feature matrices.

DD04B §19 ("Tables Deliberately Not Created") says the baseline does **not** create relational tables
for "one row per feature per observation", assigning that to Parquet/object storage. DD04B §24
already records a scoped exception for canonical candles, because the current universe is small and
PostgreSQL is authoritative until documented Parquet triggers are reached.

## Decision

For the current scope, derived feature snapshots are persisted in PostgreSQL in
`market.feature_snapshot` (one row per instrument/timeframe/anchor/schema/calculation version) and
`market.feature_snapshot_value` (one row per feature value with quality, availability and parameter
hash). Rows are append-only; database triggers reject updates and deletes. Feature calculations never
read these tables: snapshots are always reconstructed by the one `FeatureEngine` from canonical
candles, and persistence is an asynchronous, bounded, rebuildable side effect.

This is the same bounded exception pattern as ADR-001. It is not a claim that PostgreSQL is a feature
warehouse.

## Triggers to revisit (move to Parquet/object storage)

- the learning universe grows materially beyond the initial <50 instruments;
- feature snapshot volume makes OLTP reads/backups impractical;
- broad analytical history (Tier C / archival universe) is loaded;
- cross-language research needs columnar scans rather than API reads.

Until a trigger is reached, feature matrices are served through the broker-neutral
`/api/v1/features` API (no direct PostgreSQL access from Python), and remain reconstructable from
canonical candles even if the tables are dropped.

## Consequences

- Live, replay, backtest and research share one calculation path and one reconstructable store.
- The `market.feature_snapshot*` tables are derived data: deleting them loses nothing irreversible.
- The immutable triggers preserve historical feature-version meaning; mathematics changes create a
  new parameter hash / semantic version rather than rewriting rows.
- DD04B §19 remains the long-term target; this ADR documents the scoped deviation and its exit
  criteria.
