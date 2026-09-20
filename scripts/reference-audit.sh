#!/usr/bin/env bash
# Edge Relative — instrument master and temporal reference mapping audit (rerunnable).
#
# Drives the public watchlist application boundary to import instruments, reuse a broker token on a
# second instrument, and reject invalid attributes; verifies temporal resolution, half-open validity
# and DB overlap protection directly against reference tables. Uses clearly-labelled fixture symbols
# (ERREF*, ERREF2) and clears its own fixture rows first.
#
# Usage: BASE=http://localhost:8080 PG=edge-relative-mono-postgres-1 scripts/reference-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
PG="${PG:-edge-relative-mono-postgres-1}"
W="$BASE/api/v1/watchlist"
PASS=0; FAIL=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); if [ "$2" = "PASS" ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }
body() { python3 -c "import sys,json;d=json.load(open('/tmp/er-ref-body.json'));print($1)" 2>/dev/null; }
sql() { docker exec -i "$PG" psql -tA -U edge_relative -d edge_relative -c "$1" 2>&1; }
code() {
  local m="$1" u="$2" b="${3:-}"
  if [ -n "$b" ]; then
    curl -sS -m 30 -o /tmp/er-ref-body.json -w '%{http_code}' -X "$m" "$u" -H 'Content-Type: application/json' -d "$b"
  else
    curl -sS -m 30 -o /tmp/er-ref-body.json -w '%{http_code}' -X "$m" "$u"
  fi
}

echo "== Reference mapping audit against $BASE =="

# Reset fixture rows (watchlist items, mappings, identifiers, then instruments) so reruns are clean.
sql "DELETE FROM operational.watchlist_item WHERE instrument_id IN (SELECT instrument_id FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%');" >/dev/null
sql "DELETE FROM reference.broker_instrument_mapping WHERE instrument_id IN (SELECT instrument_id FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%');" >/dev/null
sql "DELETE FROM reference.instrument_identifier WHERE instrument_id IN (SELECT instrument_id FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%');" >/dev/null
sql "DELETE FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%';" >/dev/null

# S1: import a fixture instrument (creates canonical instrument + broker mapping).
c=$(code POST "$W/items" '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERREF","name":"ER Reference Fixture","brokerSymbol":"NSE_ERREF_A","tickSize":0.05,"lotSize":1}')
[ "$c" = "200" ] || [ "$c" = "201" ] && record "import-create" PASS "HTTP $c" || record "import-create" FAIL "HTTP $c $(body 'd')"
IID=$(sql "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol='ERREF'")
[ -n "$IID" ] && record "canonical-id-resolved" PASS "instrument_id=$IID" || record "canonical-id-resolved" FAIL "not found"

# S2: duplicate add is rejected and must not mutate the broker mapping.
c=$(code POST "$W/items" '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERREF","name":"ER Reference Fixture","brokerSymbol":"NSE_ERREF_Z","tickSize":0.05,"lotSize":1}')
codev=$(body 'd.get("code")')
open_n=$(sql "SELECT count(*) FROM reference.broker_instrument_mapping WHERE instrument_id=$IID AND valid_to IS NULL AND broker_token='NSE_ERREF_A'")
[ "$c" = "409" ] && [ "$codev" = "WATCHLIST_DUPLICATE" ] && [ "$open_n" = "1" ] \
  && record "duplicate-add-no-mutation" PASS "HTTP 409; token A intact" || record "duplicate-add-no-mutation" FAIL "HTTP $c code=$codev openA=$open_n"

# S3: reuse broker token A on a second instrument -> A is closed for ERREF, opened for ERREF2, adjacent.
c=$(code POST "$W/items" '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERREF2","name":"ER Reference Fixture 2","brokerSymbol":"NSE_ERREF_A","tickSize":0.05,"lotSize":1}')
IID2=$(sql "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol='ERREF2'")
[ "$c" = "200" ] || [ "$c" = "201" ] && record "token-reuse-create" PASS "HTTP $c instrument_id=$IID2" || record "token-reuse-create" FAIL "HTTP $c"
closedA=$(sql "SELECT count(*) FROM reference.broker_instrument_mapping WHERE instrument_id=$IID AND broker_token='NSE_ERREF_A' AND valid_to IS NOT NULL")
openA2=$(sql "SELECT count(*) FROM reference.broker_instrument_mapping WHERE instrument_id=$IID2 AND broker_token='NSE_ERREF_A' AND valid_to IS NULL")
adjacent=$(sql "SELECT (a.valid_to = b.valid_from) FROM reference.broker_instrument_mapping a, reference.broker_instrument_mapping b WHERE a.instrument_id=$IID AND a.broker_token='NSE_ERREF_A' AND b.instrument_id=$IID2 AND b.broker_token='NSE_ERREF_A'")
[ "$closedA" = "1" ] && [ "$openA2" = "1" ] && [ "$adjacent" = "t" ] \
  && record "token-reuse-half-open" PASS "closedA=$closedA openA2=$openA2 adjacent=$adjacent" \
  || record "token-reuse-half-open" FAIL "closedA=$closedA openA2=$openA2 adjacent=$adjacent"

# S4: canonical read resolves the current token for the new instrument.
c=$(code GET "$W")
tok=$(body '[e["brokerSymbol"] for e in d["entries"] if e["symbol"]=="ERREF2"]')
echo "$tok" | grep -q "NSE_ERREF_A" && record "current-token-resolved" PASS "brokerSymbol=$tok" || record "current-token-resolved" FAIL "brokerSymbol=$tok"

# S5: DB overlap protection (EXCLUDE) on an open broker mapping.
out=$(sql "INSERT INTO reference.broker_instrument_mapping (broker_id,instrument_id,broker_token,valid_from,valid_to) SELECT broker_id,$IID2,'NSE_ERREF_OVERLAP', (SELECT valid_from FROM reference.broker_instrument_mapping WHERE instrument_id=$IID2 AND broker_token='NSE_ERREF_A'), NULL FROM reference.broker WHERE code='GROWW';")
echo "$out" | grep -qi "exclusion constraint\|conflicting key" && record "mapping-overlap-rejected" PASS "EXCLUDE enforced" || record "mapping-overlap-rejected" FAIL "$out"

# S6: half-open [valid_from, valid_to): included at start, excluded at end.
sql "INSERT INTO reference.instrument_identifier (instrument_id, identifier_type, identifier_value, valid_from, valid_to) VALUES ($IID,'ISIN','INFIXTURE0001','2026-01-01','2026-07-01');" >/dev/null
at_start=$(sql "SELECT count(*) FROM reference.instrument_identifier WHERE instrument_id=$IID AND identifier_value='INFIXTURE0001' AND validity @> DATE '2026-01-01'")
at_end=$(sql "SELECT count(*) FROM reference.instrument_identifier WHERE instrument_id=$IID AND identifier_value='INFIXTURE0001' AND validity @> DATE '2026-07-01'")
[ "$at_start" = "1" ] && [ "$at_end" = "0" ] && record "half-open-boundary" PASS "start=in end=out" || record "half-open-boundary" FAIL "start=$at_start end=$at_end"

# S7: unknown identifier stays explicit (empty result, not the whole master).
c=$(code GET "$BASE/api/v1/brokers/groww/instruments?query=ZZZ_NO_SUCH_REFERENCE_XYZ")
n=$(body 'len(d) if isinstance(d,list) else -1')
[ "$c" = "200" ] && [ "$n" = "0" ] && record "unknown-identifier-empty" PASS "0 results" || record "unknown-identifier-empty" FAIL "HTTP $c n=$n"

# S8: invalid attributes must be rejected at the boundary (400), not 500 or silently coerced.
c=$(code POST "$W/items" '{"exchange":"NSE","instrumentType":"EQUITY","symbol":"ERREF_NOSEG","name":"x","tickSize":0.05,"lotSize":1}')
[ "$c" = "400" ] && record "missing-segment-400" PASS "HTTP 400" || record "missing-segment-400" FAIL "HTTP $c"
c=$(code POST "$W/items" '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERREF_ZEROTICK","name":"x","tickSize":0,"lotSize":1}')
[ "$c" = "400" ] && record "zero-tick-400" PASS "HTTP 400" || record "zero-tick-400" FAIL "HTTP $c"
c=$(code POST "$W/items" '{"exchange":"NSE","segment":"CASH","instrumentType":"EQUITY","symbol":"ERREF_ZEROLOT","name":"x","tickSize":0.05,"lotSize":0}')
[ "$c" = "400" ] && record "zero-lot-400" PASS "HTTP 400" || record "zero-lot-400" FAIL "HTTP $c"

# Cleanup fixture rows.
sql "DELETE FROM operational.watchlist_item WHERE instrument_id IN (SELECT instrument_id FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%');" >/dev/null
sql "DELETE FROM reference.broker_instrument_mapping WHERE instrument_id IN (SELECT instrument_id FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%');" >/dev/null
sql "DELETE FROM reference.instrument_identifier WHERE instrument_id IN (SELECT instrument_id FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%');" >/dev/null
sql "DELETE FROM reference.instrument WHERE canonical_symbol LIKE 'ERREF%';" >/dev/null

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-32s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
