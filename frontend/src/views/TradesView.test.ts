import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, render, screen } from '@testing-library/vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import TradesView from './TradesView.vue'
import * as portfolioApi from '@/api/portfolio'

vi.mock('@/api/portfolio', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/portfolio')>()
  return {
    ...actual,
    getUserProfile: vi.fn(),
    getPositions: vi.fn(),
    getHoldings: vi.fn(),
    getOrders: vi.fn(),
    getUserMargin: vi.fn(),
  }
})

vi.mocked(portfolioApi.getUserProfile).mockResolvedValue({
  userId: 'u1',
  uniqueClientCode: 'ABC123',
  nseEnabled: true,
  bseEnabled: false,
  ddpiEnabled: false,
  activeSegments: ['CASH'],
})

vi.mocked(portfolioApi.getPositions).mockResolvedValue([
  {
    tradingSymbol: 'RELIANCE',
    isin: null,
    exchange: 'NSE',
    segment: 'CASH',
    product: 'CNC',
    quantity: 10,
    netPrice: 100,
    creditQuantity: 0,
    creditPrice: null,
    debitQuantity: 0,
    debitPrice: null,
    carryForwardCreditQuantity: 0,
    carryForwardCreditPrice: null,
    carryForwardDebitQuantity: 0,
    carryForwardDebitPrice: null,
    netCarryForwardQuantity: 0,
    netCarryForwardPrice: null,
    realisedPnl: 50,
  },
])

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(cleanup)

function renderView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/chart/:symbol',
        name: 'market-ticker',
        component: { template: '<div />' },
      },
    ],
  })
  render(TradesView, {
    global: {
      plugins: [
        router,
        [
          VueQueryPlugin,
          {
            queryClient: new QueryClient({
              defaultOptions: { queries: { retry: false } },
            }),
          },
        ],
      ],
    },
  })
}

test('renders the read-only notice, profile and positions', async () => {
  renderView()

  expect(await screen.findByText('Read-only account context')).toBeTruthy()
  expect(await screen.findByText('RELIANCE')).toBeTruthy()
  expect(screen.getByText(/ABC123/)).toBeTruthy()
})

test('shows an error state when positions are unavailable', async () => {
  vi.mocked(portfolioApi.getPositions).mockRejectedValue(new Error('down'))
  renderView()

  expect(
    await screen.findByText('Positions unavailable', undefined, {
      timeout: 3000,
    }),
  ).toBeTruthy()
})
