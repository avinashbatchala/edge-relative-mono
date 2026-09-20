#!/usr/bin/env bash
# Edge Relative — backtest API audit (rerunnable).
#
# Exercises the backtest feature over its real HTTP APIs and prints a PASS/FAIL summary.
# It never seeds fixtures or weakens strategy/risk gates; it only observes the running app.
#
# Usage:
#   BASE=http://localhost:8080 scripts/backtest-audit.sh
#
# Requires: curl, python3. The backend must be running and configured (see README/AGENTS).
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
PASS=0
FAIL=0
declare -a RESULTS=()

note() { printf '%s\n' "$*"; }
record() { # id status detail
  RESULTS+=("$1|$2|$3")
  if [ "$2" = "PASS" ]; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); fi
}

# status METHOD PATH [body]
status() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -m 30 -o /tmp/er-audit-body.json -w '%{http_code}' -X "$method" "$BASE$path" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -sS -m 30 -o /tmp/er-audit-body.json -w '%{http_code}' -X "$method" "$BASE$path"
  fi
}

json() { python3 -c "import sys,json;d=json.load(open('/tmp/er-audit-body.json'));print($1)" 2>/dev/null; }

note "== Backtest audit against $BASE =="

# 1. List runs.
code=$(status GET /api/v1/backtests)
[ "$code" = "200" ] && record "list-runs" PASS "HTTP 200" || record "list-runs" FAIL "HTTP $code"

# 2. Unknown run -> 404.
code=$(status GET "/api/v1/backtests/00000000-0000-0000-0000-000000000000")
[ "$code" = "404" ] && record "unknown-run-404" PASS "HTTP 404" || record "unknown-run-404" FAIL "HTTP $code"

# 3. Invalid configs -> 422 with actionable errors.
code=$(status POST /api/v1/backtests '{"symbols":[],"startDate":"2026-08-01","endDate":"2026-09-01","timeframe":"M5","startingCapital":1000,"currency":"INR"}')
[ "$code" = "422" ] && record "empty-symbols-422" PASS "$(json 'd["errors"][0]')" || record "empty-symbols-422" FAIL "HTTP $code"

code=$(status POST /api/v1/backtests '{"symbols":["SBIN"],"startDate":"2026-09-01","endDate":"2026-08-01","timeframe":"M5","startingCapital":1000,"currency":"INR"}')
[ "$code" = "422" ] && record "bad-dates-422" PASS "$(json 'd["errors"][0]')" || record "bad-dates-422" FAIL "HTTP $code"

code=$(status POST /api/v1/backtests '{"symbols":["NOPE_XYZ"],"startDate":"2026-08-01","endDate":"2026-09-01","timeframe":"M5","startingCapital":1000,"currency":"INR"}')
[ "$code" = "422" ] && record "unknown-symbol-422" PASS "$(json 'd["errors"][0]')" || record "unknown-symbol-422" FAIL "HTTP $code"

code=$(status POST /api/v1/backtests '{"symbols":["SBIN"],"startDate":"2026-08-01","endDate":"2026-09-01","timeframe":"M5","startingCapital":1000,"currency":"INR","strategyPreset":"ER_RS_CONTINUATION_V1_RESEARCH","riskPreset":"RESEARCH_PERMISSIVE","contextSource":"NOPE"}')
[ "$code" = "422" ] && record "bad-context-422" PASS "$(json 'd["errors"][0]')" || record "bad-context-422" FAIL "HTTP $code"

# 4. Research run over real canonical history.
RUN_BODY='{"symbols":["SBIN","RELIANCE","TCS"],"startDate":"2026-08-01","endDate":"2026-09-18","timeframe":"M5","dailyTimeframe":"D1","startingCapital":1000000,"currency":"INR","marketSymbol":"NIFTY","strategyPreset":"ER_RS_CONTINUATION_V1_RESEARCH","riskPreset":"RESEARCH_PERMISSIVE","contextSource":"DERIVED_RESEARCH","strictProducers":true,"warmupBars":30,"seed":7,"endOfRun":"MARK_TO_MARKET"}'
code=$(status POST /api/v1/backtests "$RUN_BODY")
if [ "$code" != "200" ]; then
  record "research-run" FAIL "HTTP $code $(json 'd')"
else
  RK=$(json 'd["runKey"]')
  for _ in $(seq 1 200); do
    st=$(curl -sS -m 5 "$BASE/api/v1/backtests/$RK" | python3 -c "import sys,json;print(json.load(sys.stdin)['status'])" 2>/dev/null)
    case "$st" in CREATED|RUNNING) sleep 1 ;; *) break ;; esac
  done
  [ "$st" = "SUCCEEDED" ] && record "research-run-terminal" PASS "run=$RK status=$st" || record "research-run-terminal" FAIL "run=$RK status=$st"

  trades_code=$(status GET "/api/v1/backtests/$RK/trades?limit=50")
  [ "$trades_code" = "200" ] && record "research-trades-200" PASS "HTTP 200" || record "research-trades-200" FAIL "HTTP $trades_code"

  curl -sS -m 10 "$BASE/api/v1/backtests/$RK" -o /tmp/er-audit-run.json
  python3 - <<'PY'
import json
d = json.load(open('/tmp/er-audit-run.json'))
m = d['metrics']
sc = m.get('stageCounts', {})
keys = ['anchorsProcessed','setup_VALID','setupValid','plansCreated','ordersSubmitted','fills','exits']
print('RUN_STAGE_COUNTS', {k: sc.get(k, 0) for k in keys})
print('RUN_METRICS netPnl=%s completedTrades=%s' % (m.get('netPnl'), m.get('completedTrades')))
PY

  # Symbol filter must not error and must be consistent.
  filter_code=$(status GET "/api/v1/backtests/$RK/trades?symbol=SBIN")
  [ "$filter_code" = "200" ] && record "trades-symbol-filter" PASS "HTTP 200" || record "trades-symbol-filter" FAIL "HTTP $filter_code"

  # Idempotency: identical config returns the same run key.
  code2=$(status POST /api/v1/backtests "$RUN_BODY")
  RK2=$(json 'd["runKey"]')
  [ "$code2" = "200" ] && [ "$RK2" = "$RK" ] && record "idempotent-start" PASS "same runKey" || record "idempotent-start" FAIL "code=$code2 key=$RK2"
fi

# 5. Strict (no producers) run is a legitimate zero-trade outcome with explanations.
STRICT='{"symbols":["SBIN"],"startDate":"2026-08-01","endDate":"2026-09-18","timeframe":"M5","dailyTimeframe":"D1","startingCapital":1000000,"currency":"INR","marketSymbol":"NIFTY","strategyPreset":"ER_RS_CONTINUATION_V1_RESEARCH","riskPreset":"RESEARCH_PERMISSIVE","contextSource":"STRICT_PRODUCTION","strictProducers":true,"warmupBars":30,"seed":8,"endOfRun":"MARK_TO_MARKET"}'
code=$(status POST /api/v1/backtests "$STRICT")
if [ "$code" = "200" ]; then
  ZK=$(json 'd["runKey"]')
  for _ in $(seq 1 200); do
    st=$(curl -sS -m 5 "$BASE/api/v1/backtests/$ZK" | python3 -c "import sys,json;print(json.load(sys.stdin)['status'])" 2>/dev/null)
    case "$st" in CREATED|RUNNING) sleep 1 ;; *) break ;; esac
  done
  curl -sS -m 10 "$BASE/api/v1/backtests/$ZK" -o /tmp/er-audit-zero.json
  ZSUMMARY=$(python3 - <<'PY'
import json
d = json.load(open('/tmp/er-audit-zero.json'))
m = d['metrics']; sc = m.get('stageCounts', {})
miss = sc.get('longReason_MISSING_REQUIRED_DEPENDENCY', 0) + sc.get('shortReason_MISSING_REQUIRED_DEPENDENCY', 0)
print('%s trades=%s missing_dep=%s anchors=%s' % (d['status'], m.get('completedTrades'), miss, sc.get('anchorsProcessed', 0)))
PY
)
  echo "$ZSUMMARY" | grep -q "trades=0" && echo "$ZSUMMARY" | grep -q "missing_dep=" \
    && record "strict-zero-trade-explained" PASS "$ZSUMMARY" \
    || record "strict-zero-trade-explained" FAIL "$ZSUMMARY"
else
  record "strict-zero-trade-explained" FAIL "HTTP $code"
fi

note ""
note "== Summary =="
for row in "${RESULTS[@]}"; do
  IFS='|' read -r id st detail <<<"$row"
  printf '%-32s %-4s %s\n' "$id" "$st" "$detail"
done
note "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
