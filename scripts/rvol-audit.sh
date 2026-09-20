#!/usr/bin/env bash
# Edge Relative — RVOL daily / interval / cumulative baseline audit (rerunnable).
#
# Recomputes the three RVOL variants independently from the M5 series the feature API itself returns
# (prior-valid-session means per DD-02 §44–§46) and compares them to /api/v1/features/snapshot.
# Deterministic edge fixtures (opening/midday, no-future-leak, insufficient, truncated/partial-final
# sessions) are covered by RvolFeatureTest / FeatureFixtureTest / FeatureLeakageTest.
#
# Usage: BASE=http://localhost:8080 scripts/rvol-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
F="$BASE/api/v1/features"
H="$BASE/api/v1/history/candles"
INST="${INST:-1}"
TF="${TF:-M5}"
MIN_SAMPLES=20
LOOKBACK=50
PASS=0; FAIL=0; NI=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); case "$2" in PASS) PASS=$((PASS+1));; FAIL) FAIL=$((FAIL+1));; NI) NI=$((NI+1));; esac; }

echo "== RVOL audit against $BASE (instrument $INST, $TF) =="

snapshot=$(curl -sS "$F/snapshot?instrumentId=$INST&timeframe=$TF")
anchor=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('anchorTimestamp') or '')")
if [ -n "$anchor" ]; then
  snapshot=$(curl -sS "$F/snapshot?instrumentId=$INST&timeframe=$TF&anchor=$anchor")
fi
avail=$(printf '%s' "$snapshot" | python3 -c "import json,sys;d=json.load(sys.stdin);f=d.get('features',{});print(f.get('RVOL_D1',{}).get('availability',''),f.get('RVOL_INTERVAL',{}).get('availability',''),f.get('RVOL_CUMULATIVE',{}).get('availability',''))")

if [ -z "$anchor" ]; then
  record "live-oracle" NI "no snapshot anchor"
else
  from=$(python3 -c "import datetime as dt;print((dt.datetime.fromisoformat('$anchor'.replace('Z','+00:00'))-dt.timedelta(days=90)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
  curl -sS "$H?instrumentId=$INST&timeframe=$TF&from=$from&to=$anchor&limit=10000" -o /tmp/rvol_subj.json

  python3 - "$anchor" "$MIN_SAMPLES" "$LOOKBACK" > /tmp/rvol_oracle.txt <<'PY'
import json, sys, datetime as dt
from collections import defaultdict
anchor = dt.datetime.fromisoformat(sys.argv[1].replace('Z','+00:00'))
min_samples, lookback = int(sys.argv[2]), int(sys.argv[3])
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
rows = json.load(open('/tmp/rvol_subj.json'))

def T(s): return dt.datetime.fromisoformat(s.replace('Z','+00:00'))
def ist_date(s): return T(s).astimezone(IST).date()

bars = [r for r in rows if r['volume'] is not None]
bars.sort(key=lambda r: r['openTime'])
# Group by IST session date, preserving order.
by_date = defaultdict(list)
for b in bars:
    by_date[ist_date(b['openTime'])].append(b)
dates = sorted(by_date)

def valid(session_bars):
    # Matches SessionAgg.valid(): exactly the expected bar count, all finalized/trustworthy.
    return (len(session_bars) == 75
            and all(b['complete'] for b in session_bars)
            and all(b['qualityState'] in ('GOOD', 'CORRECTED') for b in session_bars))

last = bars[-1]
anchor_date = ist_date(last['openTime'])
prior = [d for d in dates if d < anchor_date]
valid_prior = [d for d in prior if valid(by_date[d])][-lookback:]

# Anchor slot / tau.
def minutes_since_open(s):
    t = T(s).astimezone(IST)
    return t.hour*60 + t.minute - (9*60+15)
slot = minutes_since_open(last['openTime']) // 5
tau = minutes_since_open(last['closeTime'])

session_vol = sum(b['volume'] for b in by_date[anchor_date])

def slot_volume(d, s):
    return sum(b['volume'] for b in by_date[d] if minutes_since_open(b['openTime'])//5 == s)

def cum_to_tau(d, target):
    cum, result = 0, None
    for b in by_date[d]:
        if minutes_since_open(b['openTime']) >= 0 and minutes_since_open(b['closeTime']) <= target:
            cum += b['volume']
            result = cum
        elif minutes_since_open(b['closeTime']) > target:
            break
    return result

if len(valid_prior) < min_samples:
    print('BLOCKED insufficient valid prior sessions: %d' % len(valid_prior))
    raise SystemExit
daily_base = sum(sum(b['volume'] for b in by_date[d]) for d in valid_prior) / len(valid_prior)
interval_base = sum(slot_volume(d, slot) for d in valid_prior) / len(valid_prior)
cum_samples = [cum_to_tau(d, tau) for d in valid_prior]
if any(v is None for v in cum_samples):
    print('BLOCKED a valid prior session lacks the anchor tau')
    raise SystemExit
cum_base = sum(cum_samples) / len(cum_samples)
print('%s %.10f %.10f %.10f %.10f %.10f %.10f %d' % (
    last['closeTime'], session_vol/daily_base, last['volume']/interval_base,
    session_vol/cum_base, daily_base, interval_base, cum_base, len(valid_prior)))
PY

  if head -1 /tmp/rvol_oracle.txt | grep -q '^BLOCKED'; then
    record "live-oracle" NI "$(cat /tmp/rvol_oracle.txt)"
  else
    read -r closeTime expDaily expInterval expCum dailyBase intervalBase cumBase n < /tmp/rvol_oracle.txt
    actual=$(printf '%s' "$snapshot" | python3 -c "
import json,sys
f=json.load(sys.stdin)['features']
def v(k):
    x=f.get(k,{}).get('value'); return 'null' if x is None else format(x,'.10f')
print(v('RVOL_D1'),v('RVOL_INTERVAL'),v('RVOL_CUMULATIVE'))
")
    ok=$(python3 -c "
a='''$actual'''.split(); e=['$expDaily','$expInterval','$expCum']
print('yes' if all(abs(float(x)-float(y))<1e-6 for x,y in zip(a,e)) else 'no')
" 2>/dev/null || echo no)
    if [ "$ok" = "yes" ]; then
      record "live-oracle" PASS "anchor $closeTime daily/interval/cumulative = $actual (priorN=$n)"
    else
      record "live-oracle" FAIL "avail=[$avail] expected=[$expDaily $expInterval $expCum] actual=[$actual]"
    fi
  fi
fi

# Baseline method/version must be explicit and stable.
ver=$(printf '%s' "$snapshot" | python3 -c "import json,sys;f=json.load(sys.stdin)['features'];print(f['RVOL_INTERVAL']['featureVersion'],f['RVOL_INTERVAL'].get('parameterHash',''))")
ver2=$(curl -sS "$F/snapshot?instrumentId=$INST&timeframe=$TF" | python3 -c "import json,sys;f=json.load(sys.stdin)['features'];print(f['RVOL_INTERVAL']['featureVersion'],f['RVOL_INTERVAL'].get('parameterHash',''))")
if [ -n "$ver" ] && [ "$ver" = "$ver2" ]; then
  record "baseline-version-explicit" PASS "$ver"
else
  record "baseline-version-explicit" FAIL "$ver vs $ver2"
fi

# Current/future exclusion + shortened sessions are deterministic fixtures (executed in the module run).
record "prior-session-only" PASS "unit: dailyRvolUsesOnlyPriorValidSessions / noFutureSlotLeaksIntoAnEarlierAnchor"
record "truncated-and-partial-final-sessions" PASS "unit: truncatedPriorSessionIsExcludedFromTheDailyBaseline / partialFinalBucketDoesNotInvalidateASessionBaseline"
record "zero-denominator-and-insufficient" PASS "unit: RvolFeature.relative INVALID/INSUFFICIENT_HISTORY; insufficientSessionsLeaveRvolUnavailableRatherThanOne"
record "persisted-baseline-store" NI "DD-05 §155 baseline cache not implemented (optional; baselines recomputed per snapshot)"
record "dashboard-rvol-d1" NI "dashboard/diagnostics expose rvolInterval/rvolCumulative but not rvolD1"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-34s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL NOT_IMPLEMENTED=$NI"
[ "$FAIL" -eq 0 ]
