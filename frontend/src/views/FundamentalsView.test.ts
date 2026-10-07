import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, expect, test, vi } from 'vitest'
import FundamentalsView from './FundamentalsView.vue'
import * as fundamentalsApi from '@/api/fundamentals'
import * as watchlistApi from '@/api/watchlist'

vi.mock('@/api/watchlist', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/watchlist')>()
  return { ...actual, getWatchlist: vi.fn() }
})

vi.mock('@/api/fundamentals', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/fundamentals')>()
  return { ...actual, getFundamentals: vi.fn() }
})

vi.mocked(watchlistApi.getWatchlist).mockResolvedValue({
  name: 'Active',
  capacity: 20,
  count: 1,
  entries: [
    {
      instrumentId: 7,
      instrumentKey: 'key-7',
      exchange: 'NSE',
      segment: 'CASH',
      instrumentType: 'EQUITY',
      symbol: 'RELIANCE',
      name: 'Reliance Industries',
      brokerSymbol: 'NSE-RELIANCE',
      tickSize: 0.05,
      lotSize: 1,
      slot: 1,
    },
  ],
})

vi.mocked(fundamentalsApi.getFundamentals).mockResolvedValue({
  advisory: true,
  instrumentId: 7,
  exchange: 'NSE',
  symbol: 'RELIANCE',
  provider: 'yahoo-nse',
  sourceRevision: 'rev-1',
  asOf: '2026-04-01T00:00:00Z',
  filedAt: '2026-04-01T00:00:00Z',
  fiscalYear: 'FY2025',
  periodType: 'ANNUAL',
  reportingBasis: 'CONSOLIDATED',
  periodEnd: '2025-03-31',
  statements: [],
  metrics: [
    { metricCode: 'trailing_pe', value: '25.0', unit: 'ratio', decimals: null },
  ],
})

afterEach(cleanup)

function renderView() {
  render(FundamentalsView, {
    global: {
      plugins: [
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

test('renders the screener with code-computed values', async () => {
  renderView()

  expect(await screen.findByText('code-computed')).toBeTruthy()
  expect(await screen.findByText('RELIANCE')).toBeTruthy()
  expect(await screen.findByText('25.00')).toBeTruthy()
  expect(screen.getByText('FY2025')).toBeTruthy()
})

test('shows the empty state when the watchlist is empty', async () => {
  vi.mocked(watchlistApi.getWatchlist).mockResolvedValueOnce({
    name: 'Active',
    capacity: 20,
    count: 0,
    entries: [],
  })
  renderView()

  expect(await screen.findByText('Watchlist is empty')).toBeTruthy()
})
