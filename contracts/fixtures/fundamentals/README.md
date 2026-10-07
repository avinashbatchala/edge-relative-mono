# Fundamental fixtures

Canonical, frozen fixtures shared by the Java fundamental adapter and the (future) Python research
implementation, per DD-06 and the DD-04 cross-language parity requirement.

Every fixture is a JSON object:

```json
{
  "fixture": "yahoo-reliance-annual-v1",
  "provider": "yahoo-nse",
  "exchange": "NSE",
  "symbol": "RELIANCE",
  "asOf": "2026-04-01T00:00:00Z",
  "tolerance": 1e-6,
  "providerResponse": { "...": "raw provider payload" },
  "expected": {
    "fiscalYear": "FY2025",
    "periodType": "ANNUAL",
    "reportingBasis": "CONSOLIDATED",
    "periodEnd": "2025-03-31",
    "filedAt": "2026-04-01T00:00:00Z",
    "statements": [ { "lineCode": "total_revenue", "value": "9000000000000", "unit": "INR", "scale": "unit" } ],
    "metrics": [ { "metricCode": "trailing_pe", "value": "25.0", "unit": "ratio" } ]
  }
}
```

Rules:

- `providerResponse` is the raw upstream payload a provider adapter parses. It is stored verbatim so
  both Java and Python parse the same input.
- `expected` is the normalised, provider-neutral result. Monetary values are decimal strings, never
  binary floating point (DD-06 §5).
- An observation-time source such as Yahoo has no filing timestamp; `filedAt` equals `asOf`, so the
  system never claims it knew a value earlier than it observed it. A filing-authoritative source
  (NSE/BSE XBRL) will refine `filedAt` behind the same port.
- Any implementation (Java production, Python research) must reproduce `expected` from
  `providerResponse`. Changing an expected value means changing the adapter/parser or the fixture
  version, not silently editing a result.
