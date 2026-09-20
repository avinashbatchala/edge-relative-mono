import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/vue'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { OpportunityRow } from '@/api/opportunities'
import * as opportunitiesApi from '@/api/opportunities'
import * as tradePlanApi from '@/api/trade-plans'
import OpportunitiesView from './OpportunitiesView.vue'

vi.mock('@/api/opportunities', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/opportunities')>()
  return { ...actual, getOpportunities: vi.fn() }
})

vi.mock('@/api/trade-plans', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/trade-plans')>()
  return { ...actual, getTradePlan: vi.fn() }
})

vi.mock('@/views/FeatureDashboardView.vue', () => ({
  default: {
    name: 'FeatureDashboardView',
    template: '<div data-testid="features-view" />',
  },
}))

const getOpportunities = vi.mocked(opportunitiesApi.getOpportunities)
const getTradePlan = vi.mocked(tradePlanApi.getTradePlan)

function row(
  instrumentId: number,
  symbol: string,
  overrides: Partial<OpportunityRow> = {},
): OpportunityRow {
  return {
    instrumentId,
    symbol,
    displayName: symbol,
    exchange: 'NSE',
    segment: 'CASH',
    timeframe: 'M5',
    setupObservationId: 10,
    setupStatus: 'VALID',
    direction: 'LONG',
    setupFamily: 'M5_3_8_CONFIRMATION',
    setupObservedAt: '2026-09-18T04:30:00Z',
    setupInstanceId: null,
    riskState: 'Awaiting',
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

function setup(rows: OpportunityRow[]) {
  getOpportunities.mockResolvedValue(rows)
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/opportunities',
        name: 'opportunities',
        component: { template: '<div/>' },
      },
      {
        path: '/features/:symbol',
        name: 'feature-ticker',
        component: { template: '<div/>' },
      },
    ],
  })
  router.push('/opportunities')
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  render(defineComponent({ render: () => h(OpportunitiesView) }), {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
}

beforeEach(() => {
  getOpportunities.mockReset()
  getTradePlan.mockReset()
})

afterEach(cleanup)

test('defaults to Setups and distinguishes setup, risk, and plan states', async () => {
  setup([
    row(1, 'SYM1', {
      setupStatus: 'VALID',
      riskState: 'AWAITING_RISK_EVALUATION',
    }),
    row(2, 'SYM2', {
      setupStatus: 'FORMING',
      riskState: 'AWAITING_RISK_EVALUATION',
    }),
    row(3, 'SYM3', {
      setupStatus: 'VALID',
      riskState: 'REJECT',
      rejectionReason: 'RVOL_FAILED',
    }),
    row(4, 'SYM4', { setupStatus: null, riskState: 'RISK_UNAVAILABLE' }),
  ])
  await screen.findByTestId('opportunities-setups')

  expect(screen.getByTestId('setup-state-SYM1').textContent).toContain(
    'Valid (strategy-qualified)',
  )
  expect(screen.getByTestId('risk-state-SYM1').textContent).toContain(
    'Awaiting risk evaluation',
  )
  expect(screen.getByTestId('setup-state-SYM2').textContent).toContain(
    'Forming',
  )
  expect(screen.getByTestId('risk-state-SYM3').textContent).toContain(
    'Risk rejected',
  )
  expect(screen.getByText('RVOL_FAILED')).toBeTruthy()
  expect(screen.getByTestId('risk-state-SYM4').textContent).toContain(
    'Risk evaluation unavailable',
  )
  // A valid setup is not presented as an approved plan.
  expect(screen.queryByTestId('view-plan-SYM1')).toBeNull()
})

test('an expired approved plan stays available for inspection', async () => {
  setup([
    row(1, 'SYM1', {
      riskState: 'APPROVE',
      riskDecisionKey: 'd-1',
      planKey: 'p-1',
      planEligibilityStatus: 'EXPIRED',
      planPermitsEntry: false,
      planExpiresAt: '2026-09-18T05:00:00Z',
    }),
  ])
  await screen.findByTestId('opportunities-setups')

  expect(screen.getByTestId('plan-state-SYM1').textContent).toContain('Expired')
  expect(screen.getByTestId('view-plan-SYM1')).toBeTruthy()
})

test('View plan opens frozen plan detail with exact expiration', async () => {
  getTradePlan.mockResolvedValue({
    planKey: 'p-1',
    decisionKey: 'd-1',
    riskDecision: 'APPROVE',
    decisionAt: '2026-09-18T04:30:00Z',
    setupObservationId: 10,
    setupInstanceId: null,
    brokerAccountId: 1,
    instrumentId: 1,
    symbol: 'SYM1',
    displayName: 'SYM1',
    direction: 'LONG',
    strategyId: 'ER_RS_CONTINUATION_V1',
    strategyVersion: 'v1',
    entryPattern: 'M5_3_8_CONFIRMATION',
    entryMethod: 'REFERENCE_PRICE',
    entryLow: 100,
    entryHigh: 100,
    structuralInvalidation: 98,
    invalidationReason: 'M5_SWING_LOW',
    protectiveStop: 97.95,
    stopBufferMethod: 'TICK_BUFFER',
    targetMethod: 'STRUCTURAL_UNRESOLVED',
    targetReference: null,
    targetRationale: null,
    expectedRewardRisk: null,
    entryTriggerPrice: 99.9,
    noChasePrice: 100,
    noChaseBasis: 'Trigger +/- ticks',
    plannedQuantity: 2000,
    approvedQuantityCeiling: 2000,
    quantityIncrement: 1,
    tickSize: 0.05,
    plannedRisk: 4400,
    approvedRiskCeiling: 4400,
    plannedNotional: 200000,
    approvedNotionalCeiling: 200000,
    maximumPlannedLoss: 4400,
    expectedCost: null,
    expectedSlippage: null,
    createdAt: '2026-09-18T04:30:00Z',
    validFrom: '2026-09-18T04:30:00Z',
    expiresAt: '2026-09-18T05:00:00Z',
    entryCutoffAt: null,
    marketRegime: null,
    sectorCode: 'IT',
    correlationId: null,
    featureSchemaVersion: null,
    policyReference: 'trade-plan-policy/v1',
    reasonCodes: ['OTHER'],
    explanation: 'APPROVE',
    eligibility: {
      status: 'ELIGIBLE',
      permitsEntry: true,
      evaluatedAt: '2026-09-18T04:35:00Z',
      expiresAt: '2026-09-18T05:00:00Z',
      reasons: [],
    },
  })
  setup([
    row(1, 'SYM1', {
      riskState: 'APPROVE',
      planKey: 'p-1',
      planEligibilityStatus: 'ELIGIBLE',
      planPermitsEntry: true,
    }),
  ])
  await screen.findByTestId('opportunities-setups')

  await fireEvent.click(screen.getByTestId('view-plan-SYM1'))
  const detail = await screen.findByTestId('plan-detail')
  expect(detail.textContent).toContain('100.00')
  expect(detail.textContent).toContain('97.95')
  expect(detail.textContent).toContain('STRUCTURAL_UNRESOLVED (no fixed price)')
  // Exact expiration timestamp, not just a countdown.
  expect(detail.textContent).toContain('18/09/2026')
})

test('switches to the linked Features view', async () => {
  setup([])
  await screen.findByTestId('opportunities-setups')
  await fireEvent.click(screen.getByRole('tab', { name: 'Features' }))
  await waitFor(() => expect(screen.getByTestId('features-view')).toBeTruthy())
})
