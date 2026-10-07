# ADR-005: Fundamental Storage (Point-in-Time, PostgreSQL)

- **Status:** Accepted for the current single-operator, <50-instrument scope
- **Date:** 2026-10-07
- **Related:** DD-06, DD-05 point-in-time rules, `ADR-001-candle-storage.md`, `ADR-002-feature-snapshot-storage.md`

## Context

DD-06 introduces company fundamentals (statements, ratios, valuation inputs) as advisory
context. Fundamentals are periodic, filing-driven, and revision-prone, and — like candles
and feature snapshots — must be point-in-time correct and reproducible. A fundamental
value is only knowable from its filing/announcement timestamp, and restatements must not
rewrite history.

DD-04B assigns large append-oriented analytical history to Parquet/object storage, with
PostgreSQL keeping manifests and relational anchors. The same scoped exception already
applied to canonical candles (ADR-001) and feature snapshots (ADR-002) applies here.

## Decision

1. Store canonical fundamentals in a new `fundamental` PostgreSQL schema (company profile,
   filing, reporting period, statement line, metric/value), append-only and revisioned.
2. Every value records its source, source revision, filing timestamp, reporting period,
   reporting basis (`CONSOLIDATED | STANDALONE`), and display scale.
3. Java owns ingestion and serves PIT reads through `/api/v1/fundamentals`; a value is
   invisible before its filing timestamp.
4. Monetary values use decimal/scaled representations, never binary floating point.
5. Python research reads fundamentals read-only and writes only to the `research` schema.

## Triggers to revisit (move broad analytical history to Parquet/object storage)

- the fundamental universe grows materially beyond the watchlist;
- historical fundamentals become wide matrices (many periods × many metrics × many
  companies) that make OLTP reads impractical;
- cross-language research needs columnar scans rather than API reads.

## Consequences

- One point-in-time store is shared by the UI, strategy context (read-only), and research.
- Restatements are additive revisions; a committed filing is never silently rewritten.
- The `fundamental` tables hold authoritative-but-advisory reference data; deleting them
  loses history, so backup/retention follows the reference data policy.
- DD-04B's Parquet target remains the long-term goal; this ADR documents the scoped
  deviation and its exit criteria.
