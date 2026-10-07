# Edge Relative — Fundamental Analysis

**Document:** DD-06  
**Version:** 1.0  
**Status:** Foundational Decision  
**Product:** Edge Relative  
**Scope:** Company fundamentals, point-in-time storage, deterministic valuation, and advisory presentation for NSE/BSE-listed Indian equities  
**Related Documents:** DD-01 (Product Spec), DD-02 (Algorithm & Strategy), DD-03 (Risk), DD-04 (Dev Stack), DD-05 (Market Data & Features)

---

# 1. Purpose

This document defines how Edge Relative holds and presents **fundamental analysis** for
Indian listed companies.

It exists because fundamentals are genuinely useful operator context, but they are a
different kind of measurement from the intraday price/volume features in DD-05, and they
must not quietly acquire trading authority that DD-02 and DD-03 deliberately withhold
from them.

The design principle is borrowed from FinRobot and is consistent with Edge Relative's own
DD-05 philosophy:

```text
Numbers are code-computed.
Narratives are LLM-assisted.
Every output is provenance-tracked.
```

---

# 2. Authority Boundary (non-negotiable)

Fundamental analysis in V1 is **advisory and research context only**.

It may:

- be displayed to the operator beside technical features;
- inform human judgement and research hypotheses;
- surface an earnings/event window as context.

It must **not**:

- create, qualify, or block a setup;
- change approved risk, position size, stop, or target;
- be promoted to a strategy condition without a formal strategy specification and a Java
  implementation (the DD-02 / research-promotion pipeline);
- be treated as a filled order, a position, or broker truth.

This is the same separation DD-01 §"Core invariant" and DD-02 §"News and Fundamental
Analysis" already require: valuation is not part of trade qualification for
`ER_RS_CONTINUATION_V1`.

---

# 3. Indian Market Constraints

- **Exchange and calendar:** NSE first, BSE as an additional source. Reporting periods
  follow the Indian financial year (April–March).
- **Currency and scale:** INR. Statement values are stored in a fixed scaled unit (rupees,
  scaled integer) and displayed in crore/lakh with the scale stated, never inferred.
- **Reporting basis:** consolidated and standalone statements are distinct records. The
  basis is always explicit; the two are never silently mixed.
- **Point-in-time:** a fundamental value is only knowable from its filing/announcement
  timestamp. Historical research must read it `as_of` an anchor and must never see a
  result before it was published.
- **Corporate actions:** splits, bonuses, and dividends affect per-share figures across
  time (DD-05 corporate-action lineage). Per-share metrics carry the adjustment basis.
- **Data rights:** broker and vendor market/fundamental data cannot automatically be
  redistributed (DD-01 data-licensing note). The chosen source and its permitted uses are
  recorded per source.

---

# 4. Data Sources

The provider is an adapter behind a broker-style port
(`fundamentals-api`), so the source is replaceable without touching strategy, feature, or
UI code.

Free-first default:

- Yahoo Finance quote-summary for NSE/BSE tickers (`.NS` / `.BO`) for orientation and
  convenience fields;
- NSE/BSE corporate filings and XBRL for authoritative statement facts where parseable.

Deferred behind the same port:

- a paid fundamentals vendor, if free coverage proves insufficient for the watchlist.

No source is trusted as authoritative by default: every value carries its source,
revision, and filing timestamp. Missing data is not zero.

---

# 5. Canonical Model

A new `fundamental` schema holds the point-in-time store. It mirrors the revision and
immutability conventions of `market.candle` (DD-05, ADR-001) and
`market.feature_snapshot` (ADR-002).

- `fundamental.data_source` — provider, licence, permitted uses.
- `fundamental.company_profile` — instrument linkage, sector, currency, listing.
- `fundamental.filing` — the point-in-time boundary: filing/announcement timestamp,
  source document reference, revision.
- `fundamental.reporting_period` — fiscal year/quarter, `CONSOLIDATED | STANDALONE`,
  period start/end, and the status (`PRELIMINARY | AUDITED | RESTATED`).
- `fundamental.statement_line` — P&L, balance-sheet, and cash-flow line items with a
  standard line code, the source revision, and a scaled value.
- `fundamental.metric` and `fundamental.metric_value` — derived ratios and valuation
  outputs, versioned by formula and parameter hash.

Rules:

- rows are append-only; corrections create a new revision, they never overwrite;
- a value is visible only after its filing timestamp;
- all monetary values are decimal/scaled, never binary floating point;
- the display scale is explicit metadata, not a presentation guess.

---

# 6. Deterministic Compute versus LLM Narration

All valuation numbers — DCF, DDM, WACC, comparable-company multiples, and simple ratios —
are produced by deterministic, versioned compute operators with full provenance. The
language model only explains and synthesises those numbers.

- Compute lives in the Python research layer (DD-04 separation; Python is not
  authoritative for trading state).
- Every operator is versioned and every result records its inputs, version, and as-of
  cutoff.
- The LLM is invoked through a vendor-neutral `LlmClient` port, implemented first for
  DeepSeek and held server-side (see ADR-007). Its output is advisory text, stored with
  provenance, and cannot mutate trading state.

Promoted from FinRobot under Apache-2.0 with attribution (ADR-006).

---

# 7. Java / Python Split

- **Java** owns ingestion, the point-in-time store, and the read API. This matches the
  existing rule that authoritative reference/observation data is served to the UI and
  research, and that Python reads observations rather than owning them.
- **Python** owns exploratory analytics, valuation operators, screening, and dataset
  construction, writing only to the `research` schema.
- **The browser and the macOS app** display authoritative state and request actions; they
  never hold secrets and never compute authority-bearing numbers.

---

# 8. API and Presentation

- `GET /api/v1/fundamentals/{instrumentId}?asOf=` returns point-in-time fundamentals for
  one instrument, with explicit source, revision, basis, and as-of metadata.
- The operator workstation shows a Fundamentals view beside the technical workspace, with
  every value labelled `code-computed` and every narrative labelled `LLM-assisted`.
- Missing or stale data is stated as unavailable; it is never rendered as zero.
- The macOS app (ADR-004) shows the same views; it holds no API keys and calls only the
  local backend.

---

# 9. Provenance and Lineage

Every returned value can be traced to:

- the source document and revision;
- the filing timestamp that made it knowable;
- the reporting period and basis;
- the formula version and parameter hash for derived metrics;
- the as-of timestamp of the request.

---

# 10. Non-Goals (V1)

- Fundamental valuation never grants trading authority.
- No real-time fundamental feed; fundamentals are periodic and filing-driven.
- No overnight or positional strategy is introduced by this document.
- No redistribution of vendor data without recorded rights.
- No LLM output is treated as a fact or as a trade decision.

---

# 11. Open Questions

- Which paid vendor, if any, is required to cover the 20-stock watchlist adequately.
- How much NSE/BSE XBRL parsing is worth building versus buying.
- Whether an earnings-window event-risk gate should later be specified for strategy context
  (a separate formal specification + Java implementation, not an implicit change).
