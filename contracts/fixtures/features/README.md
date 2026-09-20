# Feature fixtures

Canonical, frozen input/output fixtures shared by the Java feature engine and (future) Python
research implementations, per DD-04/DD-05 feature-parity requirements.

Every fixture is a JSON object:

```json
{
  "feature": "ATR",
  "semanticVersion": "ATR_V1",
  "tolerance": 1e-9,
  "parameters": { "...": "..." },
  "bars": [ { "openTime": "...", "closeTime": "...", "open": 0, "high": 0, "low": 0, "close": 0,
              "volume": 0, "complete": true, "quality": "GOOD" } ],
  "benchmarkBars": [ ... optional, for RRS ... ],
  "rvolInterval": [ ... optional, for RVE ... ],
  "expected": [ null, 0.0 ],
  "expectedRaw": [ null, 0.0 ],
  "expectedFast": [ null, 0.0 ]
}
```

Rules:

- `null` means the feature is unavailable (warmup, insufficient history, missing input, stale,
  incomplete or invalid). It does not mean zero.
- Timestamps are UTC ISO-8601; the exchange calendar maps them to `Asia/Kolkata` sessions.
- Floating point is compared within `tolerance`.
- Any implementation (Java production, Python research) must reproduce these outputs from the same
  inputs. Changing an expected value means changing the feature definition/version, not the fixture.
