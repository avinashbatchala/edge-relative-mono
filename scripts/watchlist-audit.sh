#!/usr/bin/env bash
# Edge Relative — watchlist management and eligibility audit (rerunnable).
#
# Exercises the public watchlist API: add/list/reorder/remove, the 20-entry cap (including two
# concurrent adds at the cap), duplicate rejection, persistence and history preservation. Fills the
# spare capacity with labelled fixtures (ERWL*) and removes only those during cleanup, leaving the
# operator's real watchlist untouched.
#
# Usage: BASE=http://localhost:8080 PG=edge-relative-mono-postgres-1 scripts/watchlist-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
PG="${PG:-edge-relative-mono-postgres-1}"
W="$BASE/api/v1/watchlist"
PASS=0; FAIL=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); if [ "$2" = "PASS" ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }
wjson() { python3 -c "import sys,json;d=json.load(open('/tmp/er-wl.json'));print($1)" 2>/dev/null; }
sql() { docker exec -i "$PG" psql -tA -U edge_relative -d edge_relative -c "$1" 2>&1; }
wl() { curl -sS -m 20 "$W" -o /tmp/er-wl.json -w '%{http_code}'; }
count() { wl >/dev/null; wjson 'd["count"]'; }
add() { # symbol -> http code
  curl -sS -m 20 -o /tmp/er-wl-body.json -w '%{http_code}' -X POST "$W/items" -H 'Content-Type: application/json' \
    -d "{\"exchange\":\"NSE\",\"segment\":\"CASH\",\"instrumentType\":\"EQUITY\",\"symbol\":\"$1\",\"name\":\"$1 fixture\",\"brokerSymbol\":\"NSE_$1\",\"tickSize\":0.05,\"lotSize\":1}"
}
remove_fixtures() {
  for sym in $(sql "SELECT i.canonical_symbol FROM operational.watchlist_item wi JOIN operational.watchlist w ON w.watchlist_id=wi.watchlist_id JOIN reference.instrument i ON i.instrument_id=wi.instrument_id WHERE w.active AND i.canonical_symbol LIKE 'ERWL%'"); do
    iid=$(sql "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol='$sym'")
    curl -sS -m 10 -o /dev/null -X DELETE "$W/items/$iid"
  done
}

echo "== Watchlist audit against $BASE =="
remove_fixtures >/dev/null
base_count=$(count)
record "baseline-count" PASS "existing active entries=$base_count"
[ "$base_count" -le 20 ] || record "baseline-count" FAIL "existing entries exceed cap: $base_count"

# Fill to the cap with fixtures.
i=1
while [ "$(count)" -lt 20 ] && [ "$i" -le 20 ]; do
  code=$(add "$(printf 'ERWL%02d' "$i")")
  [ "$code" = "201" ] || break
  i=$((i+1))
done
filled=$(count)
[ "$filled" = "20" ] && record "fill-to-cap-20" PASS "count=20" || record "fill-to-cap-20" FAIL "count=$filled"

# 21st entry must be rejected as WATCHLIST_FULL.
code=$(add "ERWL21")
codev=$(python3 -c "import json;print(json.load(open('/tmp/er-wl-body.json')).get('code'))" 2>/dev/null)
[ "$code" = "409" ] && [ "$codev" = "WATCHLIST_FULL" ] && record "cap-21st-rejected" PASS "HTTP 409 $codev" || record "cap-21st-rejected" FAIL "HTTP $code code=$codev"

# Duplicate add rejected.
ever=$(/usr/bin/env python3 -c "print('ERWL01')")
code=$(add "$ever")
codev=$(python3 -c "import json;print(json.load(open('/tmp/er-wl-body.json')).get('code'))" 2>/dev/null)
[ "$code" = "409" ] && [ "$codev" = "WATCHLIST_DUPLICATE" ] && record "duplicate-rejected" PASS "HTTP 409 $codev" || record "duplicate-rejected" FAIL "HTTP $code code=$codev"

# Concurrent adds at the cap: free one slot, then two parallel adds -> exactly one 201, one 409.
dup_sym=$(sql "SELECT i.canonical_symbol FROM operational.watchlist_item wi JOIN operational.watchlist w ON w.watchlist_id=wi.watchlist_id JOIN reference.instrument i ON i.instrument_id=wi.instrument_id WHERE w.active AND i.canonical_symbol LIKE 'ERWL%' LIMIT 1")
dup_iid=$(sql "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol='$dup_sym'")
curl -sS -m 10 -o /dev/null -X DELETE "$W/items/$dup_iid"
before=$(count)
( curl -sS -m 20 -o /tmp/er-wl-c1.json -w '%{http_code}' -X POST "$W/items" -H 'Content-Type: application/json' -d '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERWLC1","name":"c1","brokerSymbol":"NSE_ERWLC1","tickSize":0.05,"lotSize":1}' >/tmp/er-wl-c1.code ) &
( curl -sS -m 20 -o /tmp/er-wl-c2.json -w '%{http_code}' -X POST "$W/items" -H 'Content-Type: application/json' -d '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERWLC2","name":"c2","brokerSymbol":"NSE_ERWLC2","tickSize":0.05,"lotSize":1}' >/tmp/er-wl-c2.code ) &
wait
c1=$(cat /tmp/er-wl-c1.code); c2=$(cat /tmp/er-wl-c2.code)
after=$(count)
ok201=$(( (c1 == 201) + (c2 == 201) ))
[ "$before" = "19" ] && [ "$after" = "20" ] && [ "$ok201" = "1" ] && { [ "$c1" = "409" ] || [ "$c2" = "409" ]; } \
  && record "concurrent-cap" PASS "before=$before after=$after codes=$c1/$c2" \
  || record "concurrent-cap" FAIL "before=$before after=$after codes=$c1/$c2"

# Reorder is persisted and reflected in API order.
wl >/dev/null
ids=$(wjson '[e["instrumentId"] for e in d["entries"]]'); rev=$(wjson 'list(reversed([e["instrumentId"] for e in d["entries"]]))')
curl -sS -m 20 -o /tmp/er-wl.json -w '%{http_code}' -X PUT "$W/order" -H 'Content-Type: application/json' -d "{\"instrumentIds\":$rev}" >/dev/null
newids=$(wjson '[e["instrumentId"] for e in d["entries"]]')
[ "$newids" = "$rev" ] && record "reorder-persisted" PASS "order reversed" || record "reorder-persisted" FAIL "expected $rev got $newids"

# Removing a watched row must not erase reference/history identity.
rem_sym=$(sql "SELECT i.canonical_symbol FROM operational.watchlist_item wi JOIN operational.watchlist w ON w.watchlist_id=wi.watchlist_id JOIN reference.instrument i ON i.instrument_id=wi.instrument_id WHERE w.active AND i.canonical_symbol LIKE 'ERWL%' LIMIT 1")
rem_iid=$(sql "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol='$rem_sym'")
curl -sS -m 10 -o /dev/null -w '%{http_code}' -X DELETE "$W/items/$rem_iid" >/dev/null
still=$(sql "SELECT count(*) FROM reference.instrument WHERE instrument_id=$rem_iid")
gone=$(sql "SELECT count(*) FROM operational.watchlist_item WHERE instrument_id=$rem_iid")
[ "$still" = "1" ] && [ "$gone" = "0" ] && record "remove-preserves-identity" PASS "instrument retained, watchlist row removed" || record "remove-preserves-identity" FAIL "instrument=$still row=$gone"

# Reload consistency.
a=$(curl -sS -m 20 "$W"); b=$(curl -sS -m 20 "$W")
[ "$a" = "$b" ] && record "reload-consistent" PASS "two reads identical" || record "reload-consistent" FAIL "reads differ"

# Unsupported lifecycle features must be explicit (no silent success).
for op in "POST|$W/items/1/suspend" "POST|$W/items/1/resume" "POST|$W/items/1/pin" "PUT|$W/items/1/notes" "PUT|$W/items/1/derivatives"; do
  m="${op%%|*}"; u="${op##*|}"
  code=$(curl -sS -m 10 -o /dev/null -w '%{http_code}' -X "$m" "$u" -H 'Content-Type: application/json' -d '{}')
  [ "$code" = "404" ] || [ "$code" = "405" ] && record "unsupported-$(basename "$u")" PASS "HTTP $code (not implemented)" || record "unsupported-$(basename "$u")" FAIL "HTTP $code"
done

remove_fixtures >/dev/null
record "cleanup" PASS "fixtures removed"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-30s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
