#!/usr/bin/env bash
# Edge Relative — broker adapter audit (rerunnable).
#
# Exercises the Groww adapter's read-only capabilities, capability advertisement, passive health,
# and the safe refusal of every state-changing operation. It performs NO order placement and prints
# no account identifiers or credentials.
#
# Usage: BASE=http://localhost:8080 scripts/broker-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
B="$BASE/api/v1/brokers/groww"
PASS=0; FAIL=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); if [ "$2" = "PASS" ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }
body() { python3 -c "import sys,json;d=json.load(open('/tmp/er-broker-body.json'));print($1)" 2>/dev/null; }

# code METHOD URL [json]
code() {
  local m="$1" u="$2" b="${3:-}"
  if [ -n "$b" ]; then
    curl -sS -m 30 -o /tmp/er-broker-body.json -w '%{http_code}' -X "$m" "$u" -H 'Content-Type: application/json' -d "$b"
  else
    curl -sS -m 30 -o /tmp/er-broker-body.json -w '%{http_code}' -X "$m" "$u"
  fi
}

# mutate METHOD URL BODY -> must return 501 BROKER_OPERATION_NOT_ENABLED.
mutate() {
  local label="$1" m="$2" u="$3" b="$4"
  local c codev
  c=$(code "$m" "$u" "$b")
  codev=$(body 'd.get("code")')
  if [ "$c" = "501" ] && [ "$codev" = "BROKER_OPERATION_NOT_ENABLED" ]; then
    record "$label" PASS "HTTP 501 $codev"
  else
    record "$label" FAIL "HTTP $c code=$codev"
  fi
}

echo "== Broker audit against $BASE =="

# Capabilities: execution must be absent.
c=$(code GET "$B/capabilities")
if [ "$c" = "200" ] && ! grep -q "ORDER_EXECUTION" /tmp/er-broker-body.json; then
  record "capabilities-no-execution" PASS "$(body 'len(d["capabilities"])') capabilities"
else
  record "capabilities-no-execution" FAIL "HTTP $c"
fi

# Mutation endpoints are refused with 501 and zero downstream HTTP.
ORDER_BODY='{"exchange":"NSE","segment":"CASH","tradingSymbol":"SBIN","quantity":1,"product":"MIS","orderType":"MARKET","transactionType":"BUY","validity":"DAY","orderId":"x"}'
SMART_BODY='{"smartOrderType":"GTT","referenceId":"er-audit","exchange":"NSE","segment":"CASH","tradingSymbol":"SBIN","quantity":1,"product":"MIS","duration":"DAY","triggerPrice":100,"triggerDirection":"UP","orderType":"MARKET","transactionType":"BUY"}'
mutate "mutation-refused-place" "POST" "$B/orders" "$ORDER_BODY"
mutate "mutation-refused-modify" "PUT" "$B/orders" "$ORDER_BODY"
mutate "mutation-refused-cancel" "POST" "$B/orders/cancel" "$ORDER_BODY"
mutate "mutation-refused-smart-create" "POST" "$B/smart-orders" "$SMART_BODY"

# Passive health.
c=$(code GET "$BASE/actuator/health")
st=$(body 'd.get("status")')
[ "$c" = "200" ] && [ "$st" = "UP" ] && record "actuator-health" PASS "UP" || record "actuator-health" FAIL "HTTP $c $st"

# Read-only market data (canonical mapping + latency).
t0=$(python3 -c 'import time;print(time.time())')
c=$(code GET "$B/market-data/ltp?segment=CASH&exchangeSymbols=NSE_SBIN")
t1=$(python3 -c 'import time;print(time.time())')
ltp=$(body 'd[0]["lastPrice"] if isinstance(d,list) and d else None')
sym=$(body 'd[0]["exchangeSymbol"] if isinstance(d,list) and d else None')
lat=$(python3 -c "print(f'{($t1-$t0)*1000:.0f}')")
[ "$c" = "200" ] && [ -n "$ltp" ] && record "ltp-canonical-mapping" PASS "symbol=$sym lastPrice=$ltp latency=${lat}ms" || record "ltp-canonical-mapping" FAIL "HTTP $c"

# Malformed symbol -> validation error, no broker mutation.
c=$(code GET "$B/market-data/ltp?segment=CASH&exchangeSymbols=NSE-SBIN")
codev=$(body 'd.get("code")')
[ "$c" = "400" ] && record "ltp-malformed-symbol" PASS "HTTP 400 $codev" || record "ltp-malformed-symbol" FAIL "HTTP $c"

# Unknown enum -> 400.
c=$(code GET "$B/market-data/ltp?segment=NOPE&exchangeSymbols=NSE_SBIN")
[ "$c" = "400" ] && record "unknown-segment" PASS "HTTP 400" || record "unknown-segment" FAIL "HTTP $c"

# Missing required parameter -> 400.
c=$(code GET "$B/market-data/quote?exchange=NSE&segment=CASH")
[ "$c" = "400" ] && record "missing-param" PASS "HTTP 400" || record "missing-param" FAIL "HTTP $c"

# Read-only account/order/instrument reads (status only; no identifiers printed).
c=$(code GET "$B/orders?pageSize=1"); [ "$c" = "200" ] && record "order-query" PASS "HTTP 200" || record "order-query" FAIL "HTTP $c"
c=$(code GET "$B/portfolio/positions"); [ "$c" = "200" ] && record "positions" PASS "HTTP 200" || record "positions" FAIL "HTTP $c"
c=$(code GET "$B/margin/user"); [ "$c" = "200" ] && record "margin" PASS "HTTP 200" || record "margin" FAIL "HTTP $c"
c=$(code GET "$B/portfolio/user"); [ "$c" = "200" ] && record "user-profile" PASS "HTTP 200" || record "user-profile" FAIL "HTTP $c"
c=$(code GET "$B/instruments"); n=$(body 'len(d) if isinstance(d,list) else -1')
[ "$c" = "200" ] && record "instrument-master" PASS "HTTP 200 rows=$n" || record "instrument-master" FAIL "HTTP $c"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-40s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
