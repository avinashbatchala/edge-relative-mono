#!/usr/bin/env bash
# Edge Relative — live market ingestion, ordering and deduplication audit (rerunnable).
#
# There is no live market-data ingestion boundary in the current build: the broker surface is
# pull-only REST snapshots and `/ws/features` is snapshot-only with no event producer. This script
# verifies that negative and exercises the implemented live boundary that does exist (the versioned
# feature stream), so a reader can see exactly what is and is not present.
#
# Raw trade/quote/index/heartbeat/status/depth event normalization, sequencing and dedup, and
# in-memory bar finalization are NOT_IMPLEMENTED and are asserted by the JUnit suites named below:
#   LiveFeatureStoreTest, FeatureReplayParityTest, FeatureStreamPublisherTest.
#
# Usage: BASE=http://localhost:8080 scripts/live-ingestion-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
WS="${BASE/http/ws}/ws/features"
PASS=0; FAIL=0; NI=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); case "$2" in PASS) PASS=$((PASS+1));; FAIL) FAIL=$((FAIL+1));; NI) NI=$((NI+1));; esac; }

echo "== Live market ingestion audit against $BASE =="

# The documented broker market-data surface is read-only snapshots: an ingestion POST is rejected.
code=$(curl -sS -m 20 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/brokers/groww/market-data/quote")
if [ "$code" = "405" ] || [ "$code" = "404" ]; then
  record "no-raw-ingestion-endpoint" PASS "POST quote -> HTTP $code (read-only surface)"
else
  record "no-raw-ingestion-endpoint" FAIL "POST quote -> HTTP $code"
fi

# The only ingestion boundary is historical REST backfill; no live stream producer exists.
code=$(curl -sS -m 20 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/market/events")
if [ "$code" = "404" ]; then
  record "raw-event-endpoint-absent" PASS "POST /api/v1/market/events -> 404"
else
  record "raw-event-endpoint-absent" FAIL "unexpected HTTP $code"
fi

# Exercise the real `/ws/features` boundary: connect -> snapshot -> feature.resync -> snapshot, and
# confirm no unsolicited `feature.update` is produced (no live producer).
cat > /tmp/er-ws-audit.mjs <<'JS'
const url = process.argv[2];
const messages = [];
const ws = new WebSocket(url);
let resyncSent = false;
let settleTimer = null;
function finish(code) {
  try { ws.close(); } catch (e) {}
  console.log(JSON.stringify({ messages }));
  process.exit(code);
}
const hardStop = setTimeout(() => finish(0), 15000);
ws.onmessage = (event) => {
  let parsed;
  try { parsed = JSON.parse(event.data); } catch (e) { return; }
  messages.push(parsed);
  if (messages.length === 1 && !resyncSent) {
    resyncSent = true;
    ws.send(JSON.stringify({ type: 'feature.resync', version: 1, sequence: parsed.sequence }));
  } else if (messages.length === 2) {
    clearTimeout(hardStop);
    settleTimer = setTimeout(() => finish(0), 2000);
  }
};
ws.onerror = () => { clearTimeout(hardStop); finish(2); };
JS

cat > /tmp/er-ws-check.py <<'PY'
import json, sys
try:
    messages = json.load(open('/tmp/er-ws-audit.json')).get('messages', [])
except Exception as e:
    print('no'); print(f'unparseable: {e}'); raise SystemExit
if len(messages) < 2:
    print('no'); print(f'only {len(messages)} message(s)'); raise SystemExit
first, second = messages[0], messages[1]
updates = sum(1 for x in messages if x.get('type') == 'feature.update')
ok = (first.get('type') == 'feature.snapshot' and first.get('version') == 1
      and isinstance(first.get('sequence'), int)
      and second.get('type') == 'feature.snapshot' and second.get('version') == 1
      and first.get('sequence') == second.get('sequence')
      and updates == 0)
print('yes' if ok else 'no')
print(f"snapshot(seq={first.get('sequence')}) resync-snapshot(seq={second.get('sequence')}) updates={updates} total={len(messages)}")
PY

if node /tmp/er-ws-audit.mjs "$WS" > /tmp/er-ws-audit.json 2>/tmp/er-ws-audit.err \
    && python3 /tmp/er-ws-check.py > /tmp/er-ws-check.out 2>&1; then
  WS_OK=$(sed -n '1p' /tmp/er-ws-check.out)
  WS_DETAIL=$(sed -n '2p' /tmp/er-ws-check.out)
  if [ "$WS_OK" = "yes" ]; then
    record "ws-snapshot-resync" PASS "$WS_DETAIL"
  else
    record "ws-snapshot-resync" FAIL "$WS_DETAIL"
  fi
else
  record "ws-snapshot-resync" FAIL "websocket client failed: $(tr -d '\n' < /tmp/er-ws-audit.err | head -c 160)"
fi

# Envelope contract: version drift must be rejected by the client store (frontend unit test), and the
# server never emits an update without a producer.
record "feature-update-producer" NI "no live producer; sequence stays at snapshot value"

# Raw canonical events (trade/quote/index/heartbeat/status/depth/OI) and their ordering/dedup.
for scenario in "trade-tick-normalization" "quote-vs-trade-separation" "index-value-event" \
                "heartbeat-event" "market-status-event" "depth-oi-event" \
                "source-sequence-gap-detection" "late-event-watermark" "cumulative-volume-reset" \
                "correction-revision" "conflicting-source-reconciliation" \
                "impossible-value-validation" "bounded-ingestion-queue"; do
  record "$scenario" NI "no canonical MarketEvent layer / broker streaming client"
done

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-38s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL NOT_IMPLEMENTED=$NI"
[ "$FAIL" -eq 0 ]
