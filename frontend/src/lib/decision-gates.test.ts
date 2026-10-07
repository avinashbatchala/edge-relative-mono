import { describe, expect, it } from 'vitest'
import type { FeatureDashboardRow } from '@/api/features'
import type { OpportunityRow } from '@/api/opportunities'
import type { SetupObservation } from '@/api/setups'
import { decisionGates } from '@/lib/decision-gates'

function feature(
  overrides: Partial<FeatureDashboardRow> = {},
): FeatureDashboardRow {
  return {
    instrumentId: 1,
    instrumentKey: null,
    symbol: 'SBIN',
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
    rrsPersistence: 0.8,
    rrsTrendState: 'POSITIVE_RISING',
    dailyRrsState: 'LONG_ALIGNED',
    rvolInterval: 1.2,
    rvolCumulative: 1.1,
    rve: 0.2,
    atr: 1,
    atrPercent: 1,
    vwapDistanceAtr: 0,
    marketState: 'BULL_STRUCTURE',
    sectorCode: null,
    sectorName: null,
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

function opp(overrides: Partial<OpportunityRow> = {}): OpportunityRow {
  return {
    instrumentId: 1,
    symbol: 'SBIN',
    displayName: null,
    exchange: 'NSE',
    segment: 'CASH',
    timeframe: 'M5',
    setupObservationId: 1,
    setupStatus: 'VALID',
    direction: 'LONG',
    setupFamily: 'M5_3_8_CONFIRMATION',
    setupObservedAt: null,
    setupInstanceId: null,
    riskState: 'APPROVE',
    riskDecisionKey: null,
    riskDecision: 'APPROVE',
    rejectionReason: null,
    planKey: null,
    planEligibilityStatus: 'ELIGIBLE',
    planPermitsEntry: true,
    planExpiresAt: null,
    ...overrides,
  }
}

const setup: SetupObservation = {
  observedAt: '2026-01-01T04:30:00Z',
  direction: 'LONG',
  setupStatus: 'VALID',
  entryPattern: 'M5_3_8_CONFIRMATION',
  structuralInvalidation: 98,
  structuralRr: 2,
  strategyVersion: 'v1',
  explanation: null,
}

describe('decisionGates', () => {
  it('passes an aligned long with a valid trigger and risk approval', () => {
    const gates = decisionGates({
      feature: feature(),
      setup,
      opportunity: opp(),
    })
    const status = Object.fromEntries(gates.map((g) => [g.code, g.status]))
    expect(status.MARKET).toBe('PASS')
    expect(status.SECTOR).toBe('PASS')
    expect(status.RRS).toBe('PASS')
    expect(status.TRIGGER).toBe('PASS')
    expect(status.RISK).toBe('PASS')
    // Structure is not exposed to the browser.
    expect(status.STRUCTURE).toBe('NOT_EVALUATED')
  })

  it('fails the market gate for a long into a bear structure', () => {
    const gates = decisionGates({
      feature: feature({ marketState: 'BEAR_STRUCTURE' }),
      setup,
      opportunity: opp(),
    })
    expect(gates.find((g) => g.code === 'MARKET')?.status).toBe('FAIL')
  })

  it('marks risk rejected', () => {
    const gates = decisionGates({
      feature: feature(),
      setup,
      opportunity: opp({
        riskState: 'REJECT',
        rejectionReason: 'sector concentration',
      }),
    })
    const risk = gates.find((g) => g.code === 'RISK')
    expect(risk?.status).toBe('FAIL')
    expect(risk?.detail).toContain('sector concentration')
  })

  it('reports missing measurements as NOT_EVALUATED rather than passing', () => {
    const gates = decisionGates({
      feature: null,
      setup: null,
      opportunity: null,
    })
    expect(gates.find((g) => g.code === 'RRS')?.status).toBe('NOT_EVALUATED')
    expect(gates.find((g) => g.code === 'RISK')?.status).toBe('NOT_EVALUATED')
  })
})
