# Live market ingestion, ordering and deduplication — audit

Status: executed against the running application and the implemented live in-memory/stream boundary.
Scope classification: Core / inspect implementation. This label does not assert the feature exists.

## Headline finding

There is **no live market-data ingestion pipeline** in this build. DD-05 §34's conceptual chain

```text
Broker/Vendor Feed → Source Adapter → Normalizer → Canonical MarketEvent
```

is **NOT_IMPLEMENTED**: there is no broker streaming client, no canonical `MarketEvent` envelope, no
normalizer, no source identity/sequence, and no live event/persistence boundary. The broker surface is
**pull-only REST snapshots** (`MarketDataBroker`, five synchronous methods) and the only ingestion job
is **historical M1 REST backfill** (audited separately in `history-ingestion-audit.md`).

What *is* implemented and was audited here is (a) the `/ws/features` versioned stream and its
sequence/ordering contract, and (b) the `LiveFeatureStore` in-memory bounded bar state with its
ordering/dedup rules. The requirements in DD-05 §§46–74 are therefore mostly **NOT_IMPLEMENTED** or
**SPEC_GAP**, and this report says so explicitly rather than fabricating coverage.

## Environment and provenance

| Item | Value |
| --- | --- |
| Base commit | `9a5e2a9` plus the fixes/script/report in this change |
| Backend | Spring Boot 4.0.8, `http://localhost:8080` (JDK 26 build, Java 25 target) |
| Persistence | PostgreSQL 18.4 (`edge-relative-mono-postgres-1`), database `edge_relative` |
| Watchlist | 8 instruments: SBIN, RELIANCE, NIFTY, ICICIBANK, HDFCBANK, BHARTIARTL, TCS, ADANIENT |
| Stream | `ws://localhost:8080/ws/features`, envelope `er` v1, plain Spring WebSocket (no STOMP) |
| Live bar state | `LiveFeatureStore` (bounded, one buffer per instrument/timeframe) |
| Rerunnable script | `scripts/live-ingestion-audit.sh` (`BASE=… scripts/live-ingestion-audit.sh`) |
| Fixture provenance | deterministic `AggregatedCandle` bars (`FeatureTestSupport`), no live broker data |
| Clock/seed | no wall-clock dependence in the store/stream tests; live snapshot `observationTime` is canonical history |

Documents: DD01 §26 (market-data architecture: LTP, ticks, bid/ask, depth, OHLC, volume, OI, index),
DD01 §27 (the four timestamps), DD05 §§34–37 (source abstraction/identity/multiple sources/
reconciliation), DD05 §§46–74 (envelope, event types, event identity, timestamps, ordering, late/
duplicate/correction/gap, impossible values, staleness, quality states, in-memory state, async
persistence, bounded queues, capture durability).

## Boundary and endpoint map

| Surface | Path / type | Status | Notes |
| --- | --- | --- | --- |
| Broker market data | `GET /api/v1/brokers/groww/market-data/{quote,ltp,ohlc,option-chain,greeks}` | IMPLEMENTED (pull snapshot) | `POST` → 405; no event push |
| Broker streaming | broker WebSocket/subscription | NOT_IMPLEMENTED | no client class, no capability, no dependency |
| Canonical `MarketEvent` | — | NOT_IMPLEMENTED | no trade/quote/index/heartbeat/status/depth event model |
| Feature stream | `ws://<host>/ws/features` | IMPLEMENTED (snapshot-only) | `feature.snapshot`, `feature.update`, client `feature.resync` |
| Live in-memory state | `LiveFeatureStore.accept(FeatureContextTemplate, AggregatedCandle)` | IMPLEMENTED but unwired | Spring bean; no producer/consumer in production |
| Live tick/quote tables | — | NOT_IMPLEMENTED | none exist |
| `market.market_data_incident` | `V004__market_catalog.sql:44-63` | PORT_ONLY (schema) | no Java references |
| `market.ingestion_checkpoint` | `V004__market_catalog.sql:65-76` | PORT_ONLY (schema) | intended stream checkpoint; unreferenced |
| `market.market_observation_revision` | `V004__market_catalog.sql:26-42` | PORT_ONLY (schema) | unreferenced |

## Scenario table

Live/HTTP+WS evidence: `scripts/live-ingestion-audit.sh` (3 PASS / 0 FAIL / 14 NOT_IMPLEMENTED).
Internal-boundary evidence: `LiveFeatureStoreTest`, `FeatureReplayParityTest`,
`FeatureStreamPublisherTest` (backend) and `feature-stream.test.ts` (client, 7/7).

### Scenario 1 — inject valid events through the real boundary

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| LI-S1-1 | Trade tick normalization (DD05 §48) | trade event | canonical `TRADE` event | no ingestion boundary, no event model | NOT_IMPLEMENTED | script `trade-tick-normalization` |
| LI-S1-2 | Quote vs trade distinct (DD05 §49) | quote event | `QUOTE` event, not a trade | pull snapshot only; no event | NOT_IMPLEMENTED | script `quote-vs-trade-separation`; broker-audit |
| LI-S1-3 | Index values as instruments (DD05 §52) | index event | `INDEX_VALUE` event | NIFTY is a canonical instrument but no event feed | NOT_IMPLEMENTED | script `index-value-event` |
| LI-S1-4 | Heartbeat (DD05 §47) | heartbeat | `SOURCE_HEARTBEAT` event | none | NOT_IMPLEMENTED | script `heartbeat-event` |
| LI-S1-5 | Market status events (DD05 §53) | status event | `MARKET_STATUS` event | session state derived from the calendar only | NOT_IMPLEMENTED | script `market-status-event` |
| LI-S1-6 | Depth / open interest (DD05 §§50/51) | depth/OI event | `DEPTH`/`OPEN_INTEREST` event | `BrokerDepthLevel`/OI exist as quote snapshot fields only | NOT_IMPLEMENTED | script `depth-oi-event` |
| LI-S1-7 | Valid normalized bars advance live state (DD05 §71) | ordered `AggregatedCandle` series | state advances per accepted bar | accepted in close-time order; parity with replay | PASS | `FeatureReplayParityTest`, `LiveFeatureStoreTest` |

### Scenario 2 — replay, ordering and deduplication

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| LI-S2-1 | Duplicate must not double-count volume (DD05 §63) | identical bar replayed | ignored, duplicate counted | bar dropped, `duplicates=1`, volume unchanged | PASS | `identicalReplayIsCountedAsDuplicateSeparatelyFromLateEvents` |
| LI-S2-2 | Equal timestamps, differing values (DD05 §37/§64) | same close time, new price/volume | never silently averaged | rejected as conflicting, accepted bar unchanged | PASS | `sameTimestampWithDifferentValuesIsFlaggedConflictingAndNeverAveragedIn` |
| LI-S2-3 | Late/out-of-order events (DD05 §62) | bar before last close | marked late, not applied | rejected as late, counted | PASS | `outOfOrderEventIsIgnored`, ledger test |
| LI-S2-4 | Hand-ordered ledger reconciliation | mixed ledger | unique volume = sum of distinct accepted bars | 90 == 20+30+40; duplicates=2, late=2 | PASS | `acceptedVolumeMatchesAnIndependentHandOrderedLedger` |
| LI-S2-5 | Source sequence gap detection (DD05 §60/§65) | missing source sequence | structured gap → recovery/resync | no source sequence tracked | NOT_IMPLEMENTED | script `source-sequence-gap-detection` |
| LI-S2-6 | Late-event watermark (DD05 §62) | late event after finalize | live vs corrected state distinguished | no watermark/revision model | NOT_IMPLEMENTED | script `late-event-watermark` |
| LI-S2-7 | Cumulative-volume reset (DD05 §66) | volume decreases | reset detected/flagged | bars carry absolute volume; no cumulative track | NOT_IMPLEMENTED | script `cumulative-volume-reset` |
| LI-S2-8 | Corrections as revisions (DD05 §64) | revised bar | revision lineage preserved | same-time conflict is rejected and counted, not revised | NOT_IMPLEMENTED | `conflictingEvents`; script `correction-revision` |
| LI-S2-9 | Conflicting sources (DD05 §36/§37) | two feeds disagree | authoritative source chosen / divergence flagged | no source identity or authority config | NOT_IMPLEMENTED | script `conflicting-source-reconciliation` |
| LI-S2-10 | Disconnect/reconnect resync | reconnect then `feature.resync` | authoritative snapshot, position preserved | snapshot seq 0, resync snapshot seq 0, no updates | PASS | script `ws-snapshot-resync`; `FeatureDashboardIntegrationTest` |
| LI-S2-11 | Client gap/duplicate/out-of-order handling | envelopes | dedup + gap → resync | store ignores duplicates/older, flags gaps | PASS (API-only) | `frontend/src/stores/feature-stream.test.ts` 7/7 |

### Scenario 3 — invalid and adversarial input, queue pressure

| ID | Requirement / source | Input | Expected | Observed | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| LI-S3-1 | Impossible prices (DD05 §66) | non-positive/absurd price | flagged, no fabricated value | no event validator (feature layer only marks quality) | NOT_IMPLEMENTED | script `impossible-value-validation` |
| LI-S3-2 | Negative quantities (DD05 §66) | negative volume/qty | flagged | no validator | NOT_IMPLEMENTED | script `impossible-value-validation` |
| LI-S3-3 | Crossed quotes (DD05 §66) | bid > ask | flagged beyond auction semantics | no quote event model | NOT_IMPLEMENTED | script `impossible-value-validation` |
| LI-S3-4 | Future timestamps (DD05 §66) | timestamp ahead of now | flagged | no event timestamp validation | NOT_IMPLEMENTED | script `impossible-value-validation` |
| LI-S3-5 | Bounded live state (DD05 §73) | more bars than capacity | bounded, newest retained | size == capacity 10, oldest trimmed, no rejects | PASS | `boundedBufferKeepsTheNewestBarsUnderPressure` |
| LI-S3-6 | Bounded ingestion/persistence queue (DD05 §73) | sustained pressure | capacity/depth/age/dropped/latency exposed | no queue exists | NOT_IMPLEMENTED | script `bounded-ingestion-queue` |

Counts: PASS 7, FAIL 0 (after fixes), NOT_IMPLEMENTED 14. No BLOCKED or SPEC_GAP-only items beyond the
NOT_IMPLEMENTED entries.

## Defect register

| # | Severity | Trading/user impact | Minimal reproduction | Before → after | Root cause | Layer | Fix | Regression |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LI-1 | High | Under concurrent producers a live session could receive sequence 2 before sequence 1 (or have two threads call `sendMessage` on the same non-thread-safe session), so the client sees a gap/duplicate and issues spurious resyncs or drops updates | Two threads call `broadcastUpdate`; the first send is parked until the second completes | delivered `[2,1]` → `[1,2]` | `sequence.incrementAndGet()` and `send` were not atomic and sends were not serialised per session | feature stream (`FeatureStreamPublisher`) | serialise sequence allocation + send under one monitor | `concurrentBroadcastsDeliverSequencesInOrderToASession` |
| LI-2 | Medium (observability) | Duplicates, late events and same-timestamp value conflicts were collapsed into one `rejected` counter, so an operator cannot separate benign duplicate bursts from a real feed conflict/correction (DD05 §63/§37/§64) | Replay one identical bar and one conflicting same-time bar | one aggregate counter → `duplicate`/`late`/`conflicting` counters | single `rejected` field, no value comparison | live in-memory state (`LiveFeatureStore`) | classify before rejecting; keep `rejectedEvents` as the aggregate | `identicalReplayIsCountedAsDuplicateSeparatelyFromLateEvents`, `sameTimestampWithDifferentValuesIsFlaggedConflictingAndNeverAveragedIn` |

Failed-before/passed-after retained: LI-1's regression failed with
`expected "1" but was "2"` and passes after the fix; LI-2's behaviour is new coverage and passes.

## Independent math / reconciliation

Hand-ordered ledger fed to the real `LiveFeatureStore` boundary:

| Arrival | Bar close | Volume | Outcome |
| --- | --- | --- | --- |
| 1 | 03:55 | 20 | accepted |
| 2 | 03:50 | 10 | late (before accepted close) |
| 3 | 03:55 | 20 | duplicate |
| 4 | 04:00 | 30 | accepted |
| 5 | 03:55 | 20 | late |
| 6 | 04:00 | 30 | duplicate |
| 7 | 04:05 | 40 | accepted |

Expected unique accepted state = bars at closes {03:55, 04:00, 04:05} with volume 20+30+40 = **90**.
Observed: accepted exactly those three bars, summed volume **90**, `duplicate=2`, `late=2`,
`rejected=4`. Duplicates and late replays never double-counted and never moved the accepted close
time backwards.

## Continuity and lineage analysis

- **Bars**: `AggregatedCandle` carries `definitionVersion` and `qualityState`, and the canonical M1
  store keeps append-only revision lineage (separate audit). A same-timestamp live conflict is
  **rejected, not averaged** — so the store never silently merges disagreeing values (DD05 §37).
- **Stream**: the server sequence is monotonic once a producer exists; a snapshot reuses the current
  position and does not advance it; the client treats snapshots as authoritative and flags gaps for
  resync. Ordering is now guaranteed per session under concurrency.
- **Missing**: source event identity/sequence through normalization, correction revisions, and
  "live-as-seen vs later-corrected" state are absent. The schema tables that would carry incidents and
  checkpoints (`market_data_incident`, `ingestion_checkpoint`, `market_observation_revision`) exist
  but are unwired, so there is no persisted feed line.

## Verification commands

- `scripts/live-ingestion-audit.sh` → 3 PASS / 0 FAIL / 14 NOT_IMPLEMENTED.
- `./mvnw -Denforcer.skip=true -pl application -am test -Dtest=LiveFeatureStoreTest,FeatureReplayParityTest,FeatureStreamPublisherTest` → 12/12.
- `./mvnw -Denforcer.skip=true verify` → application 198, broker-groww 79.
- `pnpm exec vitest run src/stores/feature-stream.test.ts` → 7/7 (client dedup/gap; API-only, no browser).

## Limitations and next actions

- The pipeline gap is real work, not a test gap: add a broker streaming client and a canonical
  `MarketEvent` envelope (DD05 §46) with source identity (DD05 §35), the four timestamps (DD01 §27/
  DD05 §§55–58), a versioned ordering policy (DD05 §61), source sequence retention and gap recovery
  (DD05 §§60/§65), and impossible-value validation (DD05 §66).
- Add duplicate/conflict metrics by source (DD05 §63) and a real correction/revision path (DD05 §64).
- Add a bounded ingestion queue exposing capacity/depth/oldest-age/dropped/latency (DD05 §73) and a
  raw-capture durability policy (DD05 §74).
- Wire `market_data_incident` and `ingestion_checkpoint`, and define in-progress candle finalization
  with measured latency (DD05 §§101–102). The current `LiveFeatureStore` accepts finalized bars only;
  no producer emits in-progress candle revisions today.
- Browser rendering of the Feature Dashboard during stream updates was not exercised (no browser
  tooling available); stream verification is Node + Java WebSocket clients (API/WS-only).
