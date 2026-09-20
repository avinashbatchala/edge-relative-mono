#!/usr/bin/env bash
# Edge Relative — corporate actions and adjusted history audit (rerunnable).
#
# Corporate-action handling is NOT_IMPLEMENTED end to end: reference.corporate_action is an unwired
# schema table and there is no adjustment dataset, factor, as-of read or API. This script verifies
# what does exist (raw-price preservation, the physical schema's action semantics, absence of any
# adjusted surface) so the gap is auditable, and reports the rest as NOT_IMPLEMENTED.
#
# Usage: BASE=http://localhost:8080 scripts/corporate-action-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
C="$BASE/api/v1/history/candles"
PG="edge-relative-mono-postgres-1"
PASS=0; FAIL=0; NI=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); case "$2" in PASS) PASS=$((PASS+1));; FAIL) FAIL=$((FAIL+1));; NI) NI=$((NI+1));; esac; }
psql() { docker exec -i "$PG" psql -U edge_relative -d edge_relative -t -A "$@"; }

echo "== Corporate-action / adjusted-history audit against $BASE =="

# 1. No adjusted or corporate-action API surface exists.
curl -sS -m 20 -o /tmp/ca09_openapi.json "$BASE/v3/api-docs" 2>/dev/null
paths=$(python3 - <<'PY'
import json
try:
    d = json.load(open('/tmp/ca09_openapi.json'))
    hits = [p for p in d.get('paths', {}) if any(k in p.lower() for k in ('corporate', 'adjust', 'split', 'bonus', 'dividend'))]
    print(len(hits))
except Exception:
    print('unknown')
PY
)
if [ "$paths" = "0" ]; then
  record "no-adjusted-api-surface" PASS "0 corporate/adjustment paths in OpenAPI"
else
  record "no-adjusted-api-surface" FAIL "paths=${paths}"
fi

# 2. An `adjust` parameter is ignored: the same raw bars are returned, confirming raw preservation.
suffix="instrumentId=1&timeframe=M5&from=2026-09-18T03:45:00Z&to=2026-09-18T10:00:00Z&limit=5"
plain=$(curl -sS "$C?$suffix&adjust=false" | python3 -c "import json,sys;m=json.load(sys.stdin);print(m[0]['openTime'],m[0]['close'],len(m))")
adjusted=$(curl -sS "$C?$suffix&adjust=true" | python3 -c "import json,sys;m=json.load(sys.stdin);print(m[0]['openTime'],m[0]['close'],len(m))")
if [ "$plain" = "$adjusted" ]; then
  record "adjust-param-ignored" PASS "identical raw output with adjust=true ($plain)"
else
  record "adjust-param-ignored" FAIL "plain=$plain adjusted=$adjusted"
fi

# 3. Raw prices are preserved: the API value equals the stored raw candle value.
api_close=$(curl -sS "$C?instrumentId=1&timeframe=M1&from=2026-09-18T03:45:00Z&to=2026-09-18T03:46:00Z&limit=5" \
  | python3 -c "import json,sys;m=json.load(sys.stdin);print(m[-1]['close'])")
db_close=$(psql -c "SELECT close FROM market.candle WHERE instrument_id=1 AND is_current AND open_time='2026-09-18T03:45:00Z';")
if [ -n "$api_close" ] && [ "${api_close%.*}" = "${db_close%.*}" ]; then
  record "raw-prices-preserved" PASS "API close $api_close == stored raw close $db_close"
else
  record "raw-prices-preserved" FAIL "api=$api_close db=$db_close"
fi

# 4. The physical corporate-action model: fixtures are accepted with distinct split/bonus/dividend
#    semantics and malformed rows are rejected. Runs in a rolled-back transaction (non-destructive).
if command -v docker >/dev/null 2>&1; then
  cat > /tmp/ca09_fixtures.sql <<'SQL'
BEGIN;
INSERT INTO reference.corporate_action (instrument_id, action_type, ex_date, record_date, effective_date, ratio_numerator, ratio_denominator, source_reference)
VALUES (1, 'SPLIT', '2026-10-01', '2026-09-30', '2026-10-05', 1, 2, 'audit-fixture');
INSERT INTO reference.corporate_action (instrument_id, action_type, ex_date, ratio_numerator, ratio_denominator, source_reference)
VALUES (1, 'BONUS', '2026-10-01', 1, 1, 'audit-fixture');
INSERT INTO reference.corporate_action (instrument_id, action_type, ex_date, cash_amount, currency_code, source_reference)
VALUES (1, 'DIVIDEND', '2026-10-01', 5.00, 'INR', 'audit-fixture');
SELECT action_type || ':' || coalesce(ratio_numerator::text, '') || '/' || coalesce(ratio_denominator::text, '') || '/' || coalesce(cash_amount::text, '') FROM reference.corporate_action ORDER BY corporate_action_id;
ROLLBACK;
SQL
  out=$(docker exec -i "$PG" psql -U edge_relative -d edge_relative < /tmp/ca09_fixtures.sql 2>&1)
  if printf '%s' "$out" | grep -q "SPLIT:1.00000000/2.00000000/" \
      && printf '%s' "$out" | grep -q "BONUS:1.00000000/1.00000000/" \
      && printf '%s' "$out" | grep -q "DIVIDEND://5.00000000"; then
    record "corporate-action-schema-semantics" PASS "SPLIT/BONUS ratio and DIVIDEND cash are distinct"
  else
    record "corporate-action-schema-semantics" FAIL "$out"
  fi
  badtype=$(docker exec -i "$PG" psql -U edge_relative -d edge_relative <<'SQL' 2>&1
INSERT INTO reference.corporate_action (instrument_id, action_type, ex_date) VALUES (1, 'COUPON', '2026-10-01');
SQL
)
  badratio=$(docker exec -i "$PG" psql -U edge_relative -d edge_relative <<'SQL' 2>&1
INSERT INTO reference.corporate_action (instrument_id, action_type, ex_date, ratio_numerator) VALUES (1, 'SPLIT', '2026-10-01', 2);
SQL
)
  if printf '%s' "$badtype" | grep -q "ck_corporate_action_type" && printf '%s' "$badratio" | grep -q "ck_corporate_action_ratio"; then
    record "corporate-action-schema-rejects" PASS "unknown type and unpaired ratio rejected"
  else
    record "corporate-action-schema-rejects" FAIL "constraints not enforced"
  fi
  leaked=$(psql -c "SELECT count(*) FROM reference.corporate_action;")
  [ "$leaked" = "0" ] && record "fixture-rollback-clean" PASS "no fixture rows left" || record "fixture-rollback-clean" FAIL "rows=$leaked"
else
  record "corporate-action-schema-semantics" NI "docker unavailable"
  record "corporate-action-schema-rejects" NI "docker unavailable"
  record "fixture-rollback-clean" NI "docker unavailable"
fi

# 5. The table is unwired: nothing is ingested or read.
if command -v docker >/dev/null 2>&1; then
  rows=$(psql -c "SELECT count(*) FROM reference.corporate_action;")
  if [ "$rows" = "0" ]; then
    record "corporate-action-unwired" NI "reference.corporate_action has 0 rows; no ingestion/read path in code"
  else
    record "corporate-action-unwired" PASS "table has ${rows} rows (external population)"
  fi
  aliases=$(psql -c "SELECT count(*) FROM reference.instrument_identifier;")
  record "instrument-symbol-history" NI "instrument_identifier has ${aliases} rows; identity is symbol-keyed, no rename/alias tracking"
else
  record "corporate-action-unwired" NI "docker unavailable"
  record "instrument-symbol-history" NI "docker unavailable"
fi

# 6. Adjustment / as-of / feature-continuity scenarios have no implementation to drive.
record "split-bonus-adjustment" NI "no adjustment factor or adjusted series"
record "dividend-adjustment-distinct" NI "no dividend handling; ratio vs cash semantics only in schema"
record "announcement-vs-effective" NI "corporate_action has no announcement/availability timestamp"
record "factor-revisions" NI "no factor dataset or revision model"
record "adjusted-vs-raw-feature-continuity" NI "features compute on raw prices; no adjusted analytical series"
record "position-quantity-adjustment" NI "no execution/positions subsystem"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-38s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL NOT_IMPLEMENTED=$NI"
[ "$FAIL" -eq 0 ]
