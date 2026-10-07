import { describe, expect, it } from 'vitest'
import type { FeatureDashboardRow } from '@/api/features'
import type { OpportunityRow } from '@/api/opportunities'
import { rankOpportunities } from '@/lib/opportunity-rank'

function opp(overrides: Partial<OpportunityRow>): OpportunityRow {
  return {
    instrumentId: 1,
    symbol: 'AAA',
    displayName: null,
    exchange: 'NSE',
    segment: 'CASH',
    timeframe: 'M5',
    setupObservationId: 1,
    setupStatus: 'WATCH',
    direction: 'LONG',
    setupFamily: 'M5_3_8_CONFIRMATION',
    setupObservedAt: null,
    setupInstanceId: null,
    riskState: 'AWAITING_RISK_EVALUATION',
    riskDecisionKey: null,
    riskDecision: null,
    rejectionReason: null,
    planKey: null,
    planEligibilityStatus: null,
    planPermitsEntry: false,
    planExpiresAt: null,
    ...overrides,
  }
}

function feature(overrides: Partial<FeatureDashboardRow>): FeatureDashboardRow {
  return {
    instrumentId: 1,
    instrumentKey: null,
    symbol: 'AAA',
    displayName: null,
    exchange: 'NSE',
    segment: 'CASH',
    instrumentType: 'EQ',
    timeframe: 'M5',
    observationTime: null,
    generatedAt: null,
    lastPrice: 100,
    previousClose: 99,
    priceChange: 1,
    priceChangePercent: 1,
    rrsRaw: 0.5,
    rrsFast: 0.6,
    rrsSlow: 0.4,
    rrsPersistence: 0.7,
    rrsTrendState: 'POSITIVE_RISING',
    dailyRrsState: 'LONG_ALIGNED',
    rvolInterval: 1.2,
    rvolCumulative: 1.1,
    rve: 0.2,
    atr: 1,
    atrPercent: 1,
    vwapDistanceAtr: 0,
    marketState: 'BULL_STRUCTURE',
    sectorState: 'STRONG',
    sectorRrsRaw: 0.4,
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

describe('rankOpportunities', () => {
  it('orders by setup maturity first', () => {
    const ranked = rankOpportunities(
      [
        opp({ symbol: 'WATCHING', setupStatus: 'WATCH' }),
        opp({ symbol: 'READY', setupStatus: 'VALID' }),
      ],
      [],
    )
    expect(ranked.map((r) => r.row.symbol)).toEqual(['READY', 'WATCHING'])
    expect(ranked[0]?.rank).toBe(1)
  })

  it('uses risk permission to break maturity ties', () => {
    const ranked = rankOpportunities(
      [
        opp({
          symbol: 'PENDING',
          setupStatus: 'VALID',
          riskState: 'AWAITING_RISK_EVALUATION',
        }),
        opp({ symbol: 'APPROVED', setupStatus: 'VALID', riskState: 'APPROVE' }),
      ],
      [],
    )
    expect(ranked.map((r) => r.row.symbol)).toEqual(['APPROVED', 'PENDING'])
  })

  it('signs RRS momentum for the trade direction', () => {
    const features = [
      feature({
        instrumentId: 1,
        symbol: 'SHORTY',
        rrsFast: 0.2,
        rrsSlow: 0.5,
      }),
    ]
    const [ranked] = rankOpportunities(
      [opp({ symbol: 'SHORTY', direction: 'SHORT', setupStatus: 'FORMING' })],
      features,
    )
    // For a short, fast below slow (0.2 - 0.5 = -0.3) is favourable -> positive directional momentum.
    expect(ranked?.momentum).toBeCloseTo(0.3, 6)
  })

  it('marks alignment opposed when structure contradicts the direction', () => {
    const features = [
      feature({
        instrumentId: 1,
        symbol: 'CONFLICT',
        rrsRaw: 0.5,
        marketState: 'BEAR_STRUCTURE',
      }),
    ]
    const [ranked] = rankOpportunities(
      [opp({ symbol: 'CONFLICT', direction: 'LONG' })],
      features,
    )
    expect(ranked?.alignment).toBe('OPPOSED')
  })
})
