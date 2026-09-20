#!/usr/bin/env bash
# Edge Relative — RRS math, persistence and benchmark alignment audit (rerunnable).
#
# Recomputes RRS independently from the raw M5 series the feature API itself returns, using a
# hand-written Wilder ATR and the DD-02 §33 formula, and compares it to /api/v1/features/snapshot.
# Also checks benchmark identity/lineage and series determinism. Deterministic directional/edge
# fixtures (five market/stock combinations, zero ATR, stale benchmark, leakage) are covered by
# RrsFeatureTest / FeatureEngineTest / FeatureLeakageTest / FeatureFixtureTest.
#
# Usage: BASE=http://localhost:8080 scripts/rrs-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
F="$BASE/api/v1/features"
H="$BASE/api/v1/history/candles"
INST="${INST:-1}"
MARKET="${MARKET:-3}"   # NIFTY (canonical index)
TF="${TF:-M5}"
ATR_LENGTH="${ATR_LENGTH:-600}"
PASS=0; FAIL=0; NI=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); case "$2" in PASS) PASS=$((PASS+1));; FAIL) FAIL=$((FAIL+1));; NI) NI=$((NI+1));; esac; }

echo "== RRS audit against $BASE (instrument $INST vs market $MARKET, $TF) =="

snapshot=$(curl -sS "$F/snapshot?instrumentId=$INST&timeframe=$TF")
anchor=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('anchorTimestamp') or '')")
# Re-request pinned at that exact anchor so the service window (anchor - 90d) matches the oracle.
if [ -n "$anchor" ]; then
  snapshot=$(curl -sS "$F/snapshot?instrumentId=$INST&timeframe=$TF&anchor=$anchor")
fi
rrs_avail=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('features',{}).get('RRS_RAW',{}).get('availability',''))")

if [ -z "$anchor" ]; then
  record "snapshot-rrs" NI "no snapshot/anchor (insufficient canonical history)"
else
  # Pull the exact window the feature service used: [anchor - 90 days, anchor).
  from=$(python3 -c "import datetime as dt;print((dt.datetime.fromisoformat('$anchor'.replace('Z','+00:00'))-dt.timedelta(days=90)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
  curl -sS "$H?instrumentId=$INST&timeframe=$TF&from=$from&to=$anchor&limit=10000" -o /tmp/rrs_subject.json
  curl -sS "$H?instrumentId=$MARKET&timeframe=$TF&from=$from&to=$anchor&limit=10000" -o /tmp/rrs_market.json

  python3 - "$anchor" "$ATR_LENGTH" <<'PY' > /tmp/rrs_oracle.txt
import json, sys, datetime as dt
anchor = dt.datetime.fromisoformat(sys.argv[1].replace('Z','+00:00'))
k = int(sys.argv[2])
subject = json.load(open('/tmp/rrs_subject.json'))
market = json.load(open('/tmp/rrs_market.json'))

def series(rows):
    rows = [r for r in rows if r['close'] is not None and r['high'] is not None and r['low'] is not None]
    rows.sort(key=lambda r: r['openTime'])
    return rows

def wilder_atr(rows, k):
    if len(rows) < k:
        return [None] * len(rows)
    tr = [rows[0]['high'] - rows[0]['low']]
    for i in range(1, len(rows)):
        pc = rows[i-1]['close']
        tr.append(max(rows[i]['high'] - rows[i]['low'], abs(rows[i]['high'] - pc), abs(rows[i]['low'] - pc)))
    atr = [None] * len(rows)
    atr[k-1] = sum(tr[:k]) / k
    for i in range(k, len(rows)):
        atr[i] = (atr[i-1] * (k - 1) + tr[i]) / k
    return atr

s = series(subject)
m = series(market)
if len(s) < k + 1 or len(m) < k + 1:
    print('BLOCKED insufficient bars subject=%d market=%d need>=%d' % (len(s), len(m), k + 1))
    raise SystemExit
# The engine anchors on the last subject bar (its close time <= the resolved anchor).
last = s[-1]
satr = wilder_atr(s, k)
if satr[-1] is None:
    print('BLOCKED subject ATR not warmed up')
    raise SystemExit
mindex = {r['closeTime']: i for i, r in enumerate(m)}
mi = mindex.get(last['closeTime'])
matr = wilder_atr(m, k)
if mi is None or matr[mi] is None:
    print('BLOCKED benchmark not aligned or ATR not warmed up at %s' % last['closeTime'])
    raise SystemExit
subj_delta = s[-1]['close'] - s[-2]['close']
mkt_delta = m[mi]['close'] - m[mi-1]['close']
expected = subj_delta / satr[-1] - mkt_delta / matr[mi]
print('%s %.10f %.10f %.10f %.10f %.10f' % (last['closeTime'], satr[-1], matr[mi], subj_delta, mkt_delta, expected))
PY

  if head -1 /tmp/rrs_oracle.txt | grep -q '^BLOCKED'; then
    record "independent-rrs-oracle" NI "$(cat /tmp/rrs_oracle.txt)"
  else
    read -r closeTime satr matr sd md expected < /tmp/rrs_oracle.txt
    actual=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);v=d['features']['RRS_RAW'].get('value');print('null' if v is None else format(v,'.10f'))")
    ok=$(python3 -c "print('yes' if abs(float($actual)-float($expected))<1e-6 else 'no')" 2>/dev/null || echo no)
    if [ "$rrs_avail" = "VALID" ] && [ "$ok" = "yes" ]; then
      record "independent-rrs-oracle" PASS "anchor $closeTime expected=$expected actual=$actual (subjATR=$satr mktATR=$matr dS=$sd dM=$md)"
    else
      record "independent-rrs-oracle" FAIL "availability=$rrs_avail expected=$expected actual=$actual"
    fi
  fi
fi

# Benchmark identity/lineage: market code and a resolved market instrument, no substitution.
mc=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);print((d.get('benchmark') or {}).get('marketCode',''))")
mid=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);print((d.get('benchmark') or {}).get('marketInstrumentId'))")
if [ "$mc" = "NIFTY50" ] && [ "$mid" != "None" ] && [ -n "$mid" ]; then
  record "benchmark-identity" PASS "marketCode=NIFTY50 marketInstrumentId=$mid"
else
  record "benchmark-identity" FAIL "marketCode=$mc marketInstrumentId=$mid"
fi

# Version lineage: RRS feature version carries a parameter hash, and the same config is stable.
v1=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['features']['RRS_RAW']['featureVersion'])")
v2=$(curl -sS "$F/snapshot?instrumentId=$INST&timeframe=$TF" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['features']['RRS_RAW']['featureVersion'])")
if [ -n "$v1" ] && [ "$v1" = "$v2" ]; then
  record "rrs-version-stable" PASS "$v1"
else
  record "rrs-version-stable" FAIL "$v1 vs $v2"
fi

# Series determinism: the same range returns identical RRS at matching anchors.
if [ -n "$anchor" ]; then
  from=$(python3 -c "import datetime as dt;print((dt.datetime.fromisoformat('$anchor'.replace('Z','+00:00'))-dt.timedelta(days=2)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
  curl -sS "$F/series?instrumentId=$INST&timeframe=$TF&from=$from&to=$anchor&limit=500" -o /tmp/rrs_series_a.json
  curl -sS "$F/series?instrumentId=$INST&timeframe=$TF&from=$from&to=$anchor&limit=500" -o /tmp/rrs_series_b.json
  same=$(python3 -c "
import json
a=json.load(open('/tmp/rrs_series_a.json')); b=json.load(open('/tmp/rrs_series_b.json'))
ka=[(x['anchorTimestamp'], (x['features'].get('RRS_RAW') or {}).get('value')) for x in a]
kb=[(x['anchorTimestamp'], (x['features'].get('RRS_RAW') or {}).get('value')) for x in b]
print('yes' if ka==kb and len(ka)>0 else 'no')
" 2>/dev/null || echo no)
  [ "$same" = "yes" ] && record "series-deterministic" PASS "identical RRS across repeated series reads" \
    || record "series-deterministic" FAIL "series reads differ"
else
  record "series-deterministic" NI "no anchor"
fi

# Diagnostics expose RRS gap tracking.
gaps=$(curl -sS "$F/diagnostics" | python3 -c "import json,sys;d=json.load(sys.stdin);print('yes' if 'RRS_RAW' in d.get('metricGaps',{}) else 'no')")
[ "$gaps" = "yes" ] && record "diagnostics-rrs-gap" PASS "metricGaps contains RRS_RAW" || record "diagnostics-rrs-gap" FAIL "RRS_RAW missing"

# Percentile policy: window semantics are a documented DD-02 §42 vs DD-05 §151 conflict (SPEC_GAP).
record "percentile-prior-only" NI "inclusive of t per DD-05 §151, no min-sample guard; DD-02 §42 says prior only"
record "corporate-action-adjusted-rrs" NI "RRS inputs are raw candles; adjusted-series adoption is pending"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-32s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL NOT_IMPLEMENTED=$NI"
[ "$FAIL" -eq 0 ]
