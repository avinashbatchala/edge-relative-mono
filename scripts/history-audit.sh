#!/usr/bin/env bash
# Edge Relative — historical ingestion, backfill and coverage audit (rerunnable).
#
# Exercises the public history API: coverage, backfill start/poll/list/detail/retry, invalid inputs,
# gap-aware repeat requests, and agreement between coverage and candle queries. Uses an
# already-covered small range so it does not depend on new broker data; write-path edge cases
# (revisions, partial failure, pagination) are covered by HistoryBackfillIntegrationTest.
#
# Usage: BASE=http://localhost:8080 scripts/history-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
H="$BASE/api/v1/history"
PASS=0; FAIL=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); if [ "$2" = "PASS" ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }
jq() { python3 -c "import sys,json;d=json.load(open('/tmp/er-hist.json'));print($1)" 2>/dev/null; }
post() { curl -sS -m 30 -o /tmp/er-hist.json -w '%{http_code}' -X POST "$H/backfill" -H 'Content-Type: application/json' -d "$1"; }

echo "== History / backfill audit against $BASE =="

SBIN=1
FROM="2026-09-18T03:45:00Z"
TO="2026-09-18T04:00:00Z"

# Coverage read.
c=$(curl -sS -m 20 -o /tmp/er-hist.json -w '%{http_code}' "$H/coverage?instrumentId=$SBIN&timeframe=M1")
st=$(jq 'd["status"]'); cc=$(jq 'd["candleCount"]')
[ "$c" = "200" ] && [ "$cc" -gt 0 ] && record "coverage-read" PASS "status=$st candleCount=$cc" || record "coverage-read" FAIL "HTTP $c"

# Invalid inputs are explicit.
c=$(post "{\"instrumentId\":$SBIN,\"timeframe\":\"M5\",\"from\":\"$FROM\",\"to\":\"$TO\"}")
[ "$c" = "400" ] && record "reject-non-m1" PASS "HTTP 400" || record "reject-non-m1" FAIL "HTTP $c"
c=$(post "{\"instrumentId\":$SBIN,\"timeframe\":\"M1\",\"from\":\"$TO\",\"to\":\"$FROM\"}")
[ "$c" = "400" ] && record "reject-reversed" PASS "HTTP 400" || record "reject-reversed" FAIL "HTTP $c"
c=$(post "{\"instrumentId\":999999,\"timeframe\":\"M1\",\"from\":\"$FROM\",\"to\":\"$TO\"}")
[ "$c" = "409" ] && record "reject-unwatched" PASS "HTTP 409" || record "reject-unwatched" FAIL "HTTP $c"
c=$(post "{\"instrumentId\":$SBIN,\"from\":\"$FROM\",\"to\":\"$TO\"}")
[ "$c" = "400" ] && record "reject-missing-field" PASS "HTTP 400" || record "reject-missing-field" FAIL "HTTP $c"

wait_run() { # runKey
  for _ in $(seq 1 120); do
    curl -sS -m 10 "$H/backfill/$1" -o /tmp/er-hist.json
    st=$(jq 'd["status"]')
    case "$st" in COMPLETED|PARTIAL|FAILED) echo "$st"; return;; esac
    sleep 1
  done
  echo "$st"
}

# Valid (already covered) range: run reaches a terminal state and coverage agrees with candles.
c=$(post "{\"instrumentId\":$SBIN,\"timeframe\":\"M1\",\"from\":\"$FROM\",\"to\":\"$TO\"}")
RK=$(jq 'd["runKey"]')
[ "$c" = "201" ] && record "backfill-start" PASS "HTTP 201 run=$RK" || record "backfill-start" FAIL "HTTP $c"
st=$(wait_run "$RK")
[ "$st" = "COMPLETED" ] && record "backfill-terminal" PASS "status=$st" || record "backfill-terminal" FAIL "status=$st"

nc=$(curl -sS -m 20 "$H/candles?instrumentId=$SBIN&timeframe=M1&from=$FROM&to=$TO&limit=100" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")
curl -sS -m 20 "$H/coverage?instrumentId=$SBIN&timeframe=M1" -o /tmp/er-hist.json; cc=$(jq 'd["candleCount"]')
[ "$nc" = "15" ] && [ "$cc" -ge 15 ] && record "coverage-agrees-with-candles" PASS "range candles=$nc coverageCount=$cc" \
  || record "coverage-agrees-with-candles" FAIL "range candles=$nc coverageCount=$cc"

# Repeat the same request: no duplicates, coverage unchanged.
c=$(post "{\"instrumentId\":$SBIN,\"timeframe\":\"M1\",\"from\":\"$FROM\",\"to\":\"$TO\"}")
RK2=$(jq 'd["runKey"]'); st2=$(wait_run "$RK2")
nc2=$(curl -sS -m 20 "$H/candles?instrumentId=$SBIN&timeframe=M1&from=$FROM&to=$TO&limit=100" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")
[ "$st2" = "COMPLETED" ] && [ "$nc2" = "15" ] && record "repeat-idempotent" PASS "candles=$nc2" || record "repeat-idempotent" FAIL "status=$st2 candles=$nc2"

# Run list and detail.
c=$(curl -sS -m 20 -o /tmp/er-hist.json -w '%{http_code}' "$H/backfill?instrumentId=$SBIN&limit=5"); n=$(jq 'len(d)')
[ "$c" = "200" ] && [ "$n" -ge 1 ] && record "run-list" PASS "runs=$n" || record "run-list" FAIL "HTTP $c n=$n"
c=$(curl -sS -m 20 -o /tmp/er-hist.json -w '%{http_code}' "$H/backfill/$RK"); st3=$(jq 'd["status"]')
[ "$c" = "200" ] && [ "$st3" = "COMPLETED" ] && record "run-detail" PASS "status=$st3" || record "run-detail" FAIL "HTTP $c"

# Retry a completed run is a no-op (no failed chunks).
c=$(curl -sS -m 20 -o /tmp/er-hist.json -w '%{http_code}' -X POST "$H/backfill/$RK/retry")
[ "$c" = "200" ] && record "retry-completed" PASS "HTTP 200" || record "retry-completed" FAIL "HTTP $c"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-30s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
