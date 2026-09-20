# Historical ingestion, backfill and coverage — audit

Status: executed against the running application and persisted canonical history.
Scope classification: Core / implementation inspection.

## Environment and provenance

| Item | Value |
| --- | --- |
| Commit | `3b45ff4` + the fixes/script/report in this change |
| Backend | Spring Boot 4.0.8 on `http://localhost:8080` |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), `edge_relative` |
| Canonical base | M1 only; higher timeframes derived (`er-m1-base-v1`, `er-aggregate-v1`) |
| Instrument | SBIN (`instrumentId=1`), 472,276 current M1 candles |
| Worker | `BackfillWorker` (single dispatcher, bounded pool/queue, `recoverStaleWork` on startup) |
| Rerunnable script | `scripts/history-audit.sh` (`BASE=… scripts/history-audit.sh`) |

Sources: DD01 §29 (reproducible history, provenance), DD05 (historical store, aggregation,
§§274–282 corrections/revisions), DD04B dataset catalog. Note DD04A is the DB-schema document while
the Dev Stack file self-labels DD-04A.

## Endpoint / boundary map

| Layer | Surface |
| --- | --- |
| Public HTTP | `GET /api/v1/history/coverage`, `GET /api/v1/history/candles`, `POST /api/v1/history/backfill`, `GET /api/v1/history/backfill?instrumentId&limit`, `GET /api/v1/history/backfill/{runKey}`, `POST /api/v1/history/backfill/{runKey}/retry` |
| Application | `HistoricalBackfillService`, `HistoricalBackfillPlanner` (provider-bounded chunks), `BackfillChunkWriter` (atomic per chunk), `HistoryRepository` (idempotent append-only writes, advisory lock, revisions) |
| Source | `HistoricalDataBroker` → Groww via the resiliency layer at BULK priority |
| Worker | `BackfillWorker` (claim → fetch → write; queue backpressure releases the chunk) |

## Scenario table

Live/DB evidence: `scripts/history-audit.sh` (12/12 PASS). Source/edge evidence:
`HistoryBackfillIntegrationTest` (14/14, WireMock).

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| HI-01 | Coverage read (DD05 §274) | GET coverage SBIN M1 | totals + chunk states | 472,276 candles, COMPLETE, 0 failed | PASS | `coverage-read` |
| HI-02 | Only M1 persisted (DD05 §94/§97) | timeframe=M5 | 400 | 400 `HISTORY_INVALID` | PASS | `reject-non-m1` |
| HI-03 | Reversed dates | to < from | 400 | 400 | PASS | `reject-reversed` |
| HI-04 | Watched universe only (DD01 §29) | instrument 999999 | rejected | 409 `HISTORY_INSTRUMENT_NOT_WATCHED` | PASS | `reject-unwatched` |
| HI-05 | Missing fields | no timeframe | 400 | 400 | PASS | `reject-missing-field` |
| HI-06 | Small backfill completes and is readable | already-covered 15-minute range | terminal run, candles readable | COMPLETED, 15 candles | PASS | `backfill-terminal`, `coverage-agrees-with-candles` |
| HI-07 | Repeat request idempotent | same range again | no duplicates | 15 candles | PASS | `repeat-idempotent` |
| HI-08 | Run list/detail/retry | GET/POST backfill | consistent | 200, COMPLETED | PASS | `run-list`, `run-detail`, `retry-completed` |
| HI-09 | Higher timeframes derived, not stored | M5/D1 from M1 | derived, session-anchored | asserted | PASS | `derivesHigherTimeframesFromThePersistedM1Base` |
| HI-10 | Append-only revisions (DD05 §105/§106) | changed bar | new revision, prior non-current | asserted | PASS | `recordsAnAppendOnlyRevisionWhenABarChanges` |
| HI-11 | Gap-aware retry of failed chunks | partial failure | only failed chunks requeued | asserted | PASS | `reDownloadingTheSameRangeOnlyRefetchesFailedChunks` |
| HI-12 | Provider-bounded chunk planning | long range | chunks ≤ provider max window | asserted | PASS | `plansProviderBoundedChunksForIntradayRanges` |
| HI-13 | Chunk claim concurrency | concurrent workers | `FOR UPDATE SKIP LOCKED`, no overlap | asserted | PASS | `claimNextChunk` + integration suite |
| HI-14 | Coverage count agrees on replay | populated chunk re-fetched | recorded accepted count = actual | 5 = 5 | PASS | `coverageCountStaysConsistentWhenAPopulatedChunkIsReplayed` |
| HI-15 | Source/duplicate/missing counters | request manifest | separate counts | only accepted recorded | NOT_IMPLEMENTED | Limitations |
| HI-16 | Dataset manifest/checksum for candles | committed dataset | checksum manifest | `research.dataset` unused by ingestion | NOT_IMPLEMENTED | Limitations |
| HI-17 | Corrupted/partial source file | malformed payload | `BROKER_PROTOCOL_ERROR`, chunk FAILED | asserted | PASS | `GrowwErrorDecoderTest`, partial-failure test |

Counts: PASS 15, NOT_IMPLEMENTED 2, FAIL 0.

## Defect register (failed-before → passed-after)

| # | Severity | Impact | Repro | Before → After | Root cause | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- |
| H1 | High | Coverage manifest disagreed with the candle store: a chunk re-fetched after an interrupted run recorded `candle_count = 0` despite populated data, so coverage counts understated history and could mask real gaps | Seed candles, leave chunk PENDING, replay | chunk `candle_count=0` → equal to actual (5) | `BackfillChunkWriter` stored `upsertCandles()` affected-row count (0 when all rows already exist) as the chunk count | record the current candles in the chunk range (`countCurrentCandles`) as the accepted count | `coverageCountStaysConsistentWhenAPopulatedChunkIsReplayed` |
| H2 | Medium | Candle range reads were closed-ended while chunks/coverage are half-open: a `[03:45,04:00)` request returned 16 candles (including 04:00), disagreeing with the producing chunk | GET candles for a 15-minute window | 16 → 15 | `HistoryRepository.candles` used `open_time <= to` | use `open_time < to` (half-open, matching chunk/coverage/validity ranges) | `coverage-agrees-with-candles`, `repeat-idempotent`; feature suites re-run |

## Independent math / reconciliation

- 15-minute window `[2026-09-18T03:45:00Z, 2026-09-18T04:00:00Z)` returned exactly **15** candles
  after H2; the chunk's accepted count is the number of current candles with
  `open_time ∈ [chunk_start, chunk_end)`, which matched (HI-14).
- Higher-timeframe derivation is session-anchored: D1 open `03:45Z`, high `104.5`, low `99.5`, close
  `104.25`, volume `50`, `partial=false`, `complete=true`, `qualityState=INCOMPLETE` when only 5 of
  375 session minutes exist (HI-09) — i.e. missing minutes are not treated as zero.

## Continuity and lineage analysis

- Backfill → coverage → candles → features agree after the fixes: the integration suite exercises
  write, derive, revision and coverage in one pipeline, and the feature suites re-ran green.
- Provenance/revisions: writes are append-only; a changed bar becomes a new revision with the prior
  marked non-current (`is_current`), and an advisory lock serialises boundary chunks. Worker
  `recoverStaleWork` requeues `RUNNING` chunks/runs after a restart, so interrupted jobs resume.
- **Gap**: the ingestion path records only the accepted count; it does not separately record
  source-returned, duplicate and missing counts, and the resolver-facing `research.dataset` /
  `dataset_version` checksum catalog is not used by candle ingestion.

## Verification commands

- `scripts/history-audit.sh` → 12/12 PASS.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=HistoryBackfillIntegrationTest` → 14/14.
- `./mvnw -Denforcer.skip=true verify` → application 193, broker-groww 79.
- Browser: not exercised (no tooling); API/DB/test only.

## Limitations and next actions

- Record a per-chunk request manifest distinguishing source-returned, accepted, duplicate and
  missing counts, and surface it in the run response.
- Wire the committed `research.dataset`/`dataset_version` version + checksum so a completed backfill
  points at an immutable dataset manifest.
- Add explicit pagination handling assertions in the Groww client for very large ranges, and an
  interrupted-job/restart test that exercises `recoverStaleWork` end to end.
