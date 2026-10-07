import { describe, expect, it } from 'vitest'
import type { BacktestTimelinePoint, BacktestTradeRow } from '@/api/backtests'
import {
  timelineCandles,
  timelineMarkers,
  timelineOverlays,
} from '@/lib/backtest-timeline'

function point(
  overrides: Partial<BacktestTimelinePoint>,
): BacktestTimelinePoint {
  return {
    at: '2026-09-18T04:30:00Z',
    open: 100,
    high: 101,
    low: 99,
    close: 100.5,
    volume: 1000,
    rrsRaw: 0.2,
    rrsFast: 0.1,
    rrsSlow: 0.05,
    rrsPersistence: 0.8,
    rrsSlope: 0.01,
    rrsAcceleration: 0.02,
    rrsPercentile: 0.7,
    rvolDaily: 1.2,
    rvolInterval: 1.5,
    rvolCumulative: 1.1,
    rve: 0.3,
    atr: 1.5,
    marketStructure: 'BULL_STRUCTURE',
    marketEfficiency: 0.6,
    sectorRrs: 0.4,
    sectorStructure: 'BULL_STRUCTURE',
    longState: 'NONE',
    shortState: 'NONE',
    longReasons: [],
    shortReasons: [],
    ...overrides,
  }
}

function trade(overrides: Partial<BacktestTradeRow>): BacktestTradeRow {
  return {
    tradeKey: 't1',
    instrumentId: 1,
    symbol: 'TCS',
    direction: 'LONG',
    entryPattern: 'M5_3_8_CONFIRMATION',
    entryAt: '2026-09-18T05:00:00Z',
    entryPrice: 100,
    exitAt: '2026-09-18T06:00:00Z',
    exitPrice: 101,
    quantity: 10,
    grossPnl: 10,
    explicitCosts: 1,
    netPnl: 9,
    realizedR: 0.9,
    holdingSeconds: 3600,
    exitReason: 'TARGET',
    ambiguousBars: 0,
    costBreakdown: {},
    planKey: 'p',
    decisionKey: 'd',
    ...overrides,
  }
}

describe('backtest timeline transforms', () => {
  it('maps points to candles and overlays with baselines', () => {
    const points = [
      point({ at: '2026-09-18T04:30:00Z' }),
      point({ at: '2026-09-18T04:35:00Z', close: 101 }),
    ]
    const candles = timelineCandles(points)
    expect(candles).toHaveLength(2)
    expect(candles[0]).toMatchObject({
      openTime: '2026-09-18T04:30:00Z',
      close: 100.5,
      volume: 1000,
    })

    const overlays = timelineOverlays(points)
    expect(overlays.map((o) => o.key)).toEqual(['rrs', 'rvol', 'rve'])
    expect(overlays[0]?.baseline).toBe(0)
    expect(overlays[1]?.baseline).toBe(1)
    expect(overlays[1]?.points[0]?.value).toBe(1.5)
  })

  it('emits setup transitions once and entry/exit markers sorted by time', () => {
    const points = [
      point({ at: '2026-09-18T04:30:00Z', longState: 'WATCH' }),
      point({ at: '2026-09-18T04:35:00Z', longState: 'WATCH' }),
      point({ at: '2026-09-18T04:40:00Z', longState: 'FORMING' }),
      point({ at: '2026-09-18T04:45:00Z', longState: 'VALID' }),
    ]
    const markers = timelineMarkers(points, [trade({})], 'long')

    const setupTexts = markers
      .filter((m) => m.position === 'inBar')
      .map((m) => m.text)
    expect(setupTexts).toEqual(['WATCH', 'FORMING', 'VALID'])

    const entry = markers.find((m) => m.text === 'Entry LONG')
    const exit = markers.find((m) => m.text === 'Exit TARGET')
    expect(entry).toBeDefined()
    expect(exit).toBeDefined()
    const times = markers.map((m) => Date.parse(m.time))
    expect(times).toEqual([...times].sort((a, b) => a - b))
  })

  it('does not mark short states when evaluating the long direction', () => {
    const points = [
      point({
        at: '2026-09-18T04:30:00Z',
        longState: 'NONE',
        shortState: 'WATCH',
      }),
    ]
    expect(timelineMarkers(points, [], 'long')).toHaveLength(0)
  })
})
