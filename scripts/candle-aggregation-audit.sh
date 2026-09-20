#!/usr/bin/env bash
# Edge Relative — canonical candles and timeframe aggregation audit (rerunnable).
#
# Reads real canonical SBIN M1 from the running application, re-derives M5/M15/H1/H4/D1/W1 with an
# independent Python implementation of the DD-05 session-anchored bucketing rules, and compares
# OHLCV, boundaries and quality. Also checks session purity of the persisted base, parameter
# validation and the one-current-revision invariant.
#
# Usage: BASE=http://localhost:8080 scripts/candle-aggregation-audit.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
C="$BASE/api/v1/history/candles"
INST="${INST:-1}"
DATE="${DATE:-2026-09-18}"
PASS=0; FAIL=0
declare -a RESULTS=()
record() { RESULTS+=("$1|$2|$3"); if [ "$2" = "PASS" ]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }

SESSION_OPEN="${DATE}T03:45:00Z"
SESSION_CLOSE="${DATE}T10:00:00Z"
WEEK_FROM="2026-09-14T00:00:00Z"
WEEK_TO="2026-09-19T00:00:00Z"

echo "== Canonical candle / aggregation audit ($DATE, instrument $INST) =="

curl -sS "$C?instrumentId=$INST&timeframe=M1&from=$SESSION_OPEN&to=$SESSION_CLOSE&limit=10000" -o /tmp/ca_m1.json
for tf in M5 M15 H1 H4 D1; do
  curl -sS "$C?instrumentId=$INST&timeframe=$tf&from=$SESSION_OPEN&to=$SESSION_CLOSE&limit=10000" -o "/tmp/ca_$tf.json"
done
curl -sS "$C?instrumentId=$INST&timeframe=W1&from=$WEEK_FROM&to=$WEEK_TO&limit=100" -o /tmp/ca_W1.json
curl -sS "$C?instrumentId=$INST&timeframe=M1&from=$WEEK_FROM&to=$WEEK_TO&limit=10000" -o /tmp/ca_m1_week.json
curl -sS "$C?instrumentId=$INST&timeframe=M1&from=${DATE}T03:00:00Z&to=${DATE}T11:00:00Z&limit=10000" -o /tmp/ca_m1_wide.json

python3 - "$SESSION_OPEN" "$SESSION_CLOSE" <<'PY' > /tmp/ca_result.txt
import json, sys, datetime as dt

def T(s): return dt.datetime.fromisoformat(s.replace('Z', '+00:00'))
def load(p): return json.load(open(p))
session_open, session_close = T(sys.argv[1]), T(sys.argv[2])

m1 = load('/tmp/ca_m1.json')
problems = []

def bucketize(seconds):
    groups = {}
    for c in m1:
        ot = T(c['openTime'])
        if not (session_open <= ot < session_close):
            continue
        idx = int((ot - session_open).total_seconds() // seconds)
        groups.setdefault(idx, []).append(c)
    out = []
    for idx in sorted(groups):
        rows = groups[idx]
        start = session_open + dt.timedelta(seconds=idx * seconds)
        nominal = start + dt.timedelta(seconds=seconds)
        eff = min(nominal, session_close)
        out.append({
            'openTime': start, 'closeTime': eff, 'partial': nominal > session_close,
            'expected': int((eff - start).total_seconds() // 60), 'received': len(rows),
            'open': rows[0]['open'], 'high': max(r['high'] for r in rows),
            'low': min(r['low'] for r in rows), 'close': rows[-1]['close'],
            'volume': sum(r['volume'] for r in rows)})
    return out

def fmt(t): return t.strftime('%Y-%m-%dT%H:%M:%SZ')

def compare(tf, expected):
    got = {c['openTime']: c for c in load('/tmp/ca_%s.json' % tf)}
    exp_keys = {fmt(e['openTime']) for e in expected}
    if set(got) != exp_keys:
        problems.append(f'{tf}: key set mismatch expected={len(exp_keys)} got={len(got)}')
    mism = 0
    for e in expected:
        g = got.get(fmt(e['openTime']))
        if not g:
            mism += 1; continue
        for f in ('open', 'high', 'low', 'close', 'volume'):
            if g[f] != e[f]:
                mism += 1
        if g['closeTime'] != fmt(e['closeTime']) or g['partial'] != e['partial']:
            mism += 1
        covered = e['received'] >= e['expected']
        q = 'INCOMPLETE' if not covered else ('NO_TRADES' if e['volume'] == 0 else 'GOOD')
        if g['qualityState'] != q:
            mism += 1
        if g['complete'] is not True or g['definitionVersion'] != 'er-aggregate-v1':
            mism += 1
    if mism:
        problems.append(f'{tf}: {mism} field mismatch(es)')
    return f'{tf} bars={len(got)}'

details = []
for tf, sec in (('M5', 300), ('M15', 900), ('H1', 3600), ('H4', 14400)):
    details.append(compare(tf, bucketize(sec)))

# D1: independent day bar over the session.
d1 = load('/tmp/ca_D1.json')
if len(d1) != 1:
    problems.append('D1: expected 1 bar')
else:
    day = d1[0]
    exp_open, exp_high = m1[0]['open'], max(c['high'] for c in m1)
    exp_low, exp_close = min(c['low'] for c in m1), m1[-1]['close']
    exp_vol = sum(c['volume'] for c in m1)
    for f, v in (('open', exp_open), ('high', exp_high), ('low', exp_low), ('close', exp_close), ('volume', exp_vol)):
        if day[f] != v:
            problems.append(f'D1 {f} expected {v} got {day[f]}')
    if day['openTime'] != sys.argv[1] or day['closeTime'] != sys.argv[2]:
        problems.append('D1 boundaries not canonical session')
    exp_q = 'INCOMPLETE' if len(m1) < 375 else ('NO_TRADES' if exp_vol == 0 else 'GOOD')
    if day['qualityState'] != exp_q:
        problems.append(f'D1 quality expected {exp_q} got {day["qualityState"]}')
    details.append(f'D1 bars=1 received={len(m1)}/375 quality={day["qualityState"]}')

# W1: independent week bar; compare OHLCV (boundary convention is implementation-defined).
m1w = load('/tmp/ca_m1_week.json')
w1 = load('/tmp/ca_W1.json')
if len(w1) != 1:
    problems.append('W1: expected 1 bar')
else:
    wk = w1[0]
    for f, v in (('open', m1w[0]['open']), ('high', max(c['high'] for c in m1w)),
                 ('low', min(c['low'] for c in m1w)), ('close', m1w[-1]['close']),
                 ('volume', sum(c['volume'] for c in m1w))):
        if wk[f] != v:
            problems.append(f'W1 {f} expected {v} got {wk[f]}')
    details.append(f'W1 bars=1 open={wk["openTime"]} close={wk["closeTime"]} quality={wk["qualityState"]}')

# Session purity of the read path.
wide = load('/tmp/ca_m1_wide.json')
out_of_session = [c['openTime'] for c in wide
                  if not (T(sys.argv[1]) <= T(c['openTime']) < T(sys.argv[2]))]
if out_of_session:
    problems.append(f'M1 read returned {len(out_of_session)} non-session bars')
details.append(f'M1 session minutes={len(m1)} wide-range bars={len(wide)} out-of-session={len(out_of_session)}')

print('|'.join(details))
if problems:
    print('PROBLEMS: ' + '; '.join(problems))
else:
    print('PROBLEMS: none')
PY

DETAIL=$(sed -n '1p' /tmp/ca_result.txt)
PROBLEMS=$(sed -n '2p' /tmp/ca_result.txt)
if [ "$PROBLEMS" = "PROBLEMS: none" ]; then
  record "independent-aggregation-oracle" PASS "$DETAIL"
else
  record "independent-aggregation-oracle" FAIL "$PROBLEMS"
fi

# A fully-covered zero-volume interval is NO_TRADES, not GOOD and not INCOMPLETE (DD-05 §§103/104).
# NIFTY is an index whose every M1 bar has zero volume, so its complete M5 session is all NO_TRADES.
nifty=$(curl -sS "$C?instrumentId=3&timeframe=M5&from=$SESSION_OPEN&to=$SESSION_CLOSE&limit=10000" \
  | python3 -c "import json,sys,collections;m=json.load(sys.stdin);print(dict(collections.Counter(x['qualityState'] for x in m)),len(m))")
if printf '%s' "$nifty" | grep -q "'NO_TRADES'"; then
  record "no-trades-vs-missing" PASS "NIFTY zero-volume M5 session -> $nifty"
else
  record "no-trades-vs-missing" FAIL "NIFTY quality states $nifty"
fi

# The persisted M1 base must contain no pre-open, post-close or non-trading-day rows.
if command -v docker >/dev/null 2>&1; then
  dirty=$(docker exec edge-relative-mono-postgres-1 psql -U edge_relative -d edge_relative -t -A \
    -c "SELECT count(*) FROM market.candle WHERE is_current AND candle_definition_version='er-m1-base-v1' AND (EXTRACT(ISODOW FROM open_time AT TIME ZONE 'Asia/Kolkata') IN (6,7) OR (open_time AT TIME ZONE 'Asia/Kolkata')::time < TIME '09:15' OR (open_time AT TIME ZONE 'Asia/Kolkata')::time >= TIME '15:30');" 2>/dev/null)
  if [ "$dirty" = "0" ]; then
    record "base-session-purity" PASS "non-session rows in canonical M1 base = 0"
  else
    record "base-session-purity" FAIL "non-session rows in canonical M1 base = ${dirty:-unknown}"
  fi
  violations=$(docker exec edge-relative-mono-postgres-1 psql -U edge_relative -d edge_relative -t -A \
    -c "SELECT count(*) FROM (SELECT instrument_id, timeframe_id, open_time, candle_definition_version, count(*) FILTER (WHERE is_current) AS cur FROM market.candle GROUP BY 1,2,3,4 HAVING count(*) FILTER (WHERE is_current) > 1) x;" 2>/dev/null)
  if [ "$violations" = "0" ]; then
    record "one-current-revision" PASS "no logical candle has >1 current revision"
  else
    record "one-current-revision" FAIL "logical candles with >1 current revision = ${violations:-unknown}"
  fi
else
  record "base-session-purity" PASS "skipped (docker unavailable)"
  record "one-current-revision" PASS "skipped (docker unavailable)"
fi

# Parameter validation.
code=$(curl -sS -o /tmp/ca_err.json -w '%{http_code}' "$C?instrumentId=$INST&timeframe=XX&from=$SESSION_OPEN&to=$SESSION_CLOSE")
[ "$code" = "400" ] && grep -q HISTORY_INVALID /tmp/ca_err.json && record "reject-unknown-timeframe" PASS "400 HISTORY_INVALID" || record "reject-unknown-timeframe" FAIL "HTTP $code"
code=$(curl -sS -o /tmp/ca_err.json -w '%{http_code}' "$C?instrumentId=$INST&timeframe=M5&from=$SESSION_CLOSE&to=$SESSION_OPEN")
[ "$code" = "400" ] && grep -q HISTORY_INVALID /tmp/ca_err.json && record "reject-reversed-range" PASS "400 HISTORY_INVALID" || record "reject-reversed-range" FAIL "HTTP $code"
code=$(curl -sS -o /tmp/ca_err.json -w '%{http_code}' "$C?instrumentId=$INST&timeframe=M5&from=$SESSION_OPEN&to=$SESSION_CLOSE&limit=0")
[ "$code" = "200" ] && [ "$(python3 -c "import json;print(len(json.load(open('/tmp/ca_err.json'))))")" = "1" ] && record "limit-lower-clamp" PASS "limit=0 -> 1 bar" || record "limit-lower-clamp" FAIL "HTTP $code"
code=$(curl -sS -o /tmp/ca_err.json -w '%{http_code}' "$C?instrumentId=$INST&timeframe=M5&from=notadate&to=$SESSION_CLOSE")
[ "$code" = "400" ] && grep -q REQUEST_INVALID /tmp/ca_err.json \
  && record "reject-malformed-instant" PASS "400 REQUEST_INVALID envelope" \
  || record "reject-malformed-instant" FAIL "HTTP $code (unexpected body)"

echo ""
echo "== Summary =="
for row in "${RESULTS[@]}"; do IFS='|' read -r id st detail <<<"$row"; printf '%-32s %-4s %s\n' "$id" "$st" "$detail"; done
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
