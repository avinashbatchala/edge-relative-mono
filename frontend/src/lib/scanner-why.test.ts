import { expect, test } from 'vitest'
import type { FeatureDashboardRow } from '@/api/features'
import { featureWhySections } from './scanner-why'

function row(
  overrides: Partial<FeatureDashboardRow> = {},
): FeatureDashboardRow {
  return {
    instrumentId: 1,
    instrumentKey: null,
    symbol: 'RELIANCE',
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

test('maps a positive row to deterministic why sections', () => {
  const sections = featureWhySections(
    row({
      rrsRaw: 1.5,
      rve: 0.3,
      quality: 'GOOD',
      marketState: 'BULLISH',
      sectorState: 'STRONG',
      staleSeconds: 5,
    }),
  )

  const relativeStrength = sections.find((s) => s.title === 'Relative strength')
  expect(relativeStrength?.items[0]?.label).toBe('RRS 1.50')
  expect(relativeStrength?.items[0]?.tone).toBe('positive')

  const participation = sections.find((s) => s.title === 'Participation')
  expect(participation?.items[2]?.tone).toBe('positive')

  const quality = sections.find((s) => s.title === 'Data quality')
  expect(quality?.items[0]?.tone).toBe('positive')
  expect(quality?.items[2]?.label).toBe('Freshness 5s')
})

test('treats missing measurements as muted, not zero', () => {
  const sections = featureWhySections(row())
  const relativeStrength = sections.find((s) => s.title === 'Relative strength')
  expect(relativeStrength?.items[0]?.label).toBe('RRS —')
  expect(relativeStrength?.items[0]?.tone).toBe('muted')
})
