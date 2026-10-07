import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, test } from 'vitest'
import type { FeatureDashboardRow } from '@/api/features'
import {
  useFeatureStreamStore,
  type FeatureStreamEnvelope,
} from './feature-stream'

function row(
  instrumentId: number,
  values: Partial<FeatureDashboardRow> = {},
): FeatureDashboardRow {
  return {
    instrumentId,
    instrumentKey: `key-${instrumentId}`,
    symbol: `SYM${instrumentId}`,
    displayName: null,
    exchange: 'NSE',
    segment: 'CASH',
    instrumentType: 'EQUITY',
    timeframe: 'M5',
    observationTime: '2026-09-20T10:00:00Z',
    generatedAt: '2026-09-20T10:00:01Z',
    lastPrice: 100,
    previousClose: 99,
    priceChange: 1,
    priceChangePercent: 1.0,
    rrsRaw: 1,
    rrsFast: 1,
    rrsSlow: 1,
    rrsPersistence: 1,
    rrsTrendState: 'POSITIVE_RISING',
    dailyRrsState: 'POSITIVE',
    rvolInterval: 1.2,
    rvolCumulative: 1.1,
    rve: 0.1,
    atr: 2,
    atrPercent: 2,
    vwapDistanceAtr: null,
    marketState: 'BULL_STRUCTURE',
    sectorCode: null,
    sectorName: null,
    sectorState: 'BULL_STRUCTURE',
    sectorRrsRaw: 0.5,
    quality: 'GOOD',
    availability: 'VALID',
    qualityReason: null,
    staleSeconds: 5,
    featureSchemaVersion: 'er-feature-schema-v1',
    featureVersions: { RRS_RAW: 'RRS_V1@abc' },
    unavailableReasons: {},
    unavailableStates: {},
    ...values,
  }
}

function envelope(
  overrides: Partial<FeatureStreamEnvelope> = {},
): FeatureStreamEnvelope {
  return {
    type: 'feature.update',
    version: 1,
    sequence: 1,
    payload: [row(1)],
    ...overrides,
  }
}

describe('feature stream store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  test('authoritative snapshot sets order and sequence', () => {
    const store = useFeatureStreamStore()
    store.applyEnvelope({
      type: 'feature.snapshot',
      version: 1,
      sequence: 7,
      payload: [row(1), row(2)],
    })
    expect(store.rowList.map((r) => r.instrumentId)).toEqual([1, 2])
    expect(store.lastSequence).toBe(7)
    expect(store.gapDetected).toBe(false)
  })

  test('applies incremental updates by canonical key', () => {
    const store = useFeatureStreamStore()
    store.applyEnvelope({
      type: 'feature.snapshot',
      version: 1,
      sequence: 0,
      payload: [row(1)],
    })
    const outcome = store.applyEnvelope(
      envelope({ sequence: 1, payload: [row(1, { rrsRaw: 2.5 })] }),
    )
    expect(outcome).toBe('applied')
    expect(store.rowList[0]?.rrsRaw).toBe(2.5)
    expect(store.lastSequence).toBe(1)
  })

  test('ignores duplicate and out-of-order messages', () => {
    const store = useFeatureStreamStore()
    store.applyEnvelope({
      type: 'feature.snapshot',
      version: 1,
      sequence: 3,
      payload: [row(1, { rrsRaw: 1 })],
    })
    expect(store.applyEnvelope(envelope({ sequence: 3 }))).toBe('duplicate')
    expect(
      store.applyEnvelope(
        envelope({ sequence: 2, payload: [row(1, { rrsRaw: 9 })] }),
      ),
    ).toBe('out-of-order')
    expect(store.rowList[0]?.rrsRaw).toBe(1)
  })

  test('detects a sequence gap without applying the event', () => {
    const store = useFeatureStreamStore()
    store.applyEnvelope({
      type: 'feature.snapshot',
      version: 1,
      sequence: 4,
      payload: [row(1, { rrsRaw: 1 })],
    })
    const outcome = store.applyEnvelope(
      envelope({ sequence: 6, payload: [row(1, { rrsRaw: 99 })] }),
    )
    expect(outcome).toBe('gap')
    expect(store.gapDetected).toBe(true)
    expect(store.rowList[0]?.rrsRaw).toBe(1)
    expect(store.lastSequence).toBe(4)
  })

  test('a resync snapshot recovers from a gap', () => {
    const store = useFeatureStreamStore()
    store.setAuthoritative([row(1)], 4)
    store.applyEnvelope(envelope({ sequence: 6, payload: [row(1)] }))
    expect(store.gapDetected).toBe(true)
    store.applyEnvelope({
      type: 'feature.snapshot',
      version: 1,
      sequence: 6,
      payload: [row(1, { rrsRaw: 4 })],
    })
    expect(store.gapDetected).toBe(false)
    expect(store.rowList[0]?.rrsRaw).toBe(4)
  })

  test('ignores unknown envelope versions and types', () => {
    const store = useFeatureStreamStore()
    expect(store.applyEnvelope(envelope({ version: 2 }))).toBe('ignored')
    expect(store.applyEnvelope(envelope({ type: 'other' }))).toBe('ignored')
  })

  test('merging keeps existing order and appends new instruments', () => {
    const store = useFeatureStreamStore()
    store.setAuthoritative([row(1), row(2)])
    store.mergeRows([row(1), row(3)])
    expect(store.rowList.map((r) => r.instrumentId)).toEqual([1, 2, 3])
  })
})
