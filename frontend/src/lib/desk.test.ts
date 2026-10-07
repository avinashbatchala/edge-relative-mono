import { expect, test } from 'vitest'
import type { FeatureDashboardRow } from '@/api/features'
import { marketSummary, recentRows, sectorSummary } from './desk'

function row(
  overrides: Partial<FeatureDashboardRow> = {},
): FeatureDashboardRow {
  return {
    instrumentId: 1,
    instrumentKey: null,
    symbol: 'X',
    displayName: null,
    exchange: 'NSE',
    segment: 'CASH',
    instrumentType: 'EQUITY',
    timeframe: 'M5',
    observationTime: null,
    generatedAt: null,
    lastPrice: null,
    previousClose: null,
    priceChange: null,
    priceChangePercent: null,
    rrsRaw: null,
    rrsFast: null,
    rrsSlow: null,
    rrsPersistence: null,
    rrsTrendState: null,
    dailyRrsState: null,
    rvolInterval: null,
    rvolCumulative: null,
    rve: null,
    atr: null,
    atrPercent: null,
    vwapDistanceAtr: null,
    marketState: null,
    sectorCode: null,
    sectorName: null,
    sectorState: null,
    sectorRrsRaw: null,
    quality: 'GOOD',
    availability: 'VALID',
    qualityReason: null,
    staleSeconds: null,
    featureSchemaVersion: 'v1',
    featureVersions: {},
    unavailableReasons: {},
    unavailableStates: {},
    ...overrides,
  }
}

test('summarises breadth, volatility and dominant market state', () => {
  const summary = marketSummary([
    row({ rrsRaw: 1, atrPercent: 2, marketState: 'BULLISH' }),
    row({ rrsRaw: 0.5, atrPercent: 4, marketState: 'BULLISH' }),
    row({ rrsRaw: -1, atrPercent: 3, marketState: 'BEARISH' }),
    row({ rrsRaw: null }),
  ])

  expect(summary.total).toBe(4)
  expect(summary.positives).toBe(2)
  expect(summary.negatives).toBe(1)
  expect(summary.breadth).toBeCloseTo(2 / 3)
  expect(summary.avgAtrPercent).toBeCloseTo(3)
  expect(summary.marketState).toBe('BULLISH')
})

test('handles empty input without inventing values', () => {
  const summary = marketSummary([])
  expect(summary.breadth).toBeNull()
  expect(summary.avgAtrPercent).toBeNull()
  expect(summary.marketState).toBeNull()
})

test('recentRows orders by observation time and drops unknown times', () => {
  const rows = recentRows([
    row({ symbol: 'A', observationTime: '2026-01-01T00:00:00Z' }),
    row({ symbol: 'B', observationTime: '2026-02-01T00:00:00Z' }),
    row({ symbol: 'C', observationTime: null }),
  ])
  expect(rows.map((r) => r.symbol)).toEqual(['B', 'A'])
})

test('sectorSummary groups by sector and surfaces unmapped instruments', () => {
  const groups = sectorSummary([
    row({
      rrsRaw: 1,
      sectorCode: 'IT',
      sectorRrsRaw: 0.5,
      sectorState: 'BULLISH',
    }),
    row({
      rrsRaw: 0.2,
      sectorCode: 'IT',
      sectorRrsRaw: 0.5,
      sectorState: 'BULLISH',
    }),
    row({
      rrsRaw: -0.3,
      sectorCode: 'PSU_BANK',
      sectorRrsRaw: -0.8,
      sectorState: 'BEARISH',
    }),
    row({ rrsRaw: null, sectorCode: null }),
  ])

  expect(groups.map((g) => g.code)).toEqual(['IT', 'PSU_BANK', null])
  const it = groups[0]!
  expect(it.count).toBe(2)
  expect(it.positives).toBe(2)
  expect(it.avgRrs).toBeCloseTo(0.6)
  expect(it.sectorRrs).toBeCloseTo(0.5)
  expect(it.state).toBe('BULLISH')
  expect(groups[2]!.count).toBe(1)
  expect(groups[2]!.avgRrs).toBeNull()
})

test('sectorSummary is empty for no rows', () => {
  expect(sectorSummary([])).toEqual([])
})
