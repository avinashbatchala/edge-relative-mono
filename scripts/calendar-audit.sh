#!/usr/bin/env bash
# Edge Relative — trading calendar and session lifecycle audit (rerunnable).
#
# Cross-checks the NSE session calendar through the canonical candle API (session-anchored slots),
# verifies holidays/weekends, reports real M1 coverage gaps without fabricating bars, and proves
# host-timezone independence by running the calendar unit test under two host zones.
#
# Usage: BASE=http://localhost:8080 PG=edge-relative-mono-postgres-1 scripts/calendar-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
PG="${PG:-edge-relative-mono-postgres-1}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0; FAIL=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); if [ "$2" = "PASS" ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }
sql() { docker exec -i "$PG" psql -tA -U edge_relative -d edge_relative -c "$1" 2>&1; }

bars() { # date -> "count firstOpen lastClose"
  curl -sS -m 30 "$BASE/api/v1/history/candles?instrumentId=$IID&timeframe=M5&from=${1}T00:00:00Z&to=${1}T23:59:59Z&limit=500" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d), d[0]['openTime'] if d else '-', d[-1]['closeTime'] if d else '-')" 2>/dev/null
}

echo "== Calendar / session audit against $BASE =="

IID=$(curl -sS -m 20 "$BASE/api/v1/watchlist" | python3 -c "import sys,json;d=json.load(sys.stdin);print(next((e['instrumentId'] for e in d['entries'] if e['symbol']=='SBIN'), ''))" 2>/dev/null)
[ -n "$IID" ] && record "instrument-resolved" PASS "SBIN instrumentId=$IID" || record "instrument-resolved" FAIL "SBIN not watched"

# Normal session: 2026-09-04 (Friday, full 375 M1 minutes) -> 75 M5 bars anchored at 03:45Z.
read -r n first last <<<"$(bars 2026-09-04)"
[ "$n" = "75" ] && [ "$first" = "2026-09-04T03:45:00Z" ] && [ "$last" = "2026-09-04T10:00:00Z" ] \
  && record "normal-session-slots" PASS "75 bars 03:45Z..10:00Z" \
  || record "normal-session-slots" FAIL "count=$n first=$first last=$last"

# Configured holiday (Ganesh Chaturthi 2026-09-14, Monday) -> no session bars.
read -r n first last <<<"$(bars 2026-09-14)"
[ "$n" = "0" ] && record "holiday-no-session" PASS "0 bars" || record "holiday-no-session" FAIL "count=$n"

# Weekend (2026-09-19) -> no session bars.
read -r n first last <<<"$(bars 2026-09-19)"
[ "$n" = "0" ] && record "weekend-no-session" PASS "0 bars" || record "weekend-no-session" FAIL "count=$n"

# Real data gap (informational, never fabricated): 2026-09-18 M1 coverage vs 375 expected.
m1=$(sql "SELECT count(*) FROM market.candle c JOIN reference.timeframe t ON t.timeframe_id=c.timeframe_id WHERE c.instrument_id=$IID AND t.code='M1' AND c.is_current AND c.open_time >= '2026-09-18T00:00:00Z' AND c.open_time < '2026-09-19T00:00:00Z';")
read -r n first last <<<"$(bars 2026-09-18)"
record "session-coverage-observed" PASS "2026-09-18 M1=$m1/375 -> M5=$n/75 (gap, not fabricated)"

# Host-timezone independence: the calendar unit test asserts absolute instants.
for tz in "America/New_York" "Asia/Kolkata"; do
  if (cd "$REPO/backend" && TZ="$tz" JAVA_HOME=$(/usr/libexec/java_home -v 26) ./mvnw -q -Denforcer.skip=true \
      -pl application -am test -Dtest=NseTradingCalendarTest -Dsurefire.failIfNoSpecifiedTests=false >/tmp/er-cal.log 2>&1); then
    record "host-tz-$tz" PASS "calendar test passed under TZ=$tz"
  else
    record "host-tz-$tz" FAIL "calendar test failed under TZ=$tz (see /tmp/er-cal.log)"
  fi
done

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-28s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
