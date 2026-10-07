import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import type { RiskPostureResponse } from '@/api/risk'
import * as riskApi from '@/api/risk'
import RiskCenterView from './RiskCenterView.vue'

vi.mock('@/api/risk', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/risk')>()
  return { ...actual, getRiskPosture: vi.fn() }
})

vi.mock('@/api/opportunities', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/opportunities')>()
  return { ...actual, getOpportunities: vi.fn().mockResolvedValue([]) }
})

const getRiskPosture = vi.mocked(riskApi.getRiskPosture)

function posture(
  overrides: Partial<RiskPostureResponse> = {},
): RiskPostureResponse {
  return {
    asOf: '2026-09-18T10:00:00Z',
    portfolioAvailable: false,
    portfolio: null,
    accountRiskAvailable: false,
    account: null,
    controls: {
      present: false,
      stopNewTrades: false,
      cancelPendingEntries: false,
      flattenOnly: false,
      automationEnabled: false,
      executionEnabled: false,
      reason: null,
      updatedBy: null,
      updatedAt: null,
    },
    counts: { openTrades: 0, activeReservations: 0, pendingOrders: 0 },
    unavailable: [
      'No portfolio snapshot has been persisted (portfolio producer not wired).',
    ],
    ...overrides,
  }
}

function renderView() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, retryDelay: 0 } },
  })
  render(defineComponent({ render: () => h(RiskCenterView) }), {
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
}

beforeEach(() => getRiskPosture.mockReset())
afterEach(cleanup)

test('states portfolio risk context is unavailable with reasons', async () => {
  getRiskPosture.mockResolvedValue(posture())
  renderView()
  const card = await screen.findByTestId('risk-unavailable')
  await waitFor(() =>
    expect(card.textContent).toContain(
      'No portfolio snapshot has been persisted',
    ),
  )
  expect(screen.queryByTestId('risk-portfolio')).toBeNull()
  expect(document.body.textContent).toContain('No control row')
})

test('renders portfolio metrics when a snapshot is available', async () => {
  getRiskPosture.mockResolvedValue(
    posture({
      portfolioAvailable: true,
      unavailable: [],
      portfolio: {
        snapshotAt: '2026-09-18T10:00:00Z',
        tradingDate: '2026-09-18',
        netLiquidationValue: 1000000,
        availableCash: 250000,
        buyingPower: 500000,
        marginUsed: 100000,
        grossExposure: 400000,
        netExposure: 400000,
        openRisk: 5000,
        stressOpenRisk: 8000,
        realizedSessionPnl: 0,
        unrealizedPnl: 1500,
        openPositions: 2,
      },
    }),
  )
  renderView()
  const card = await screen.findByTestId('risk-portfolio')
  await waitFor(() =>
    expect(card.textContent).toContain('Net liquidation value'),
  )
  expect(card.textContent).toContain('Open positions')
  expect(screen.queryByTestId('risk-unavailable')).toBeNull()
})
