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
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import TickerView from './TickerView.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ApiError } from '@/api/http'
import * as marketDataApi from '@/api/market-data'
import {
  CANDLES,
  RELIANCE,
  candleSeries,
  optionChain,
  quote,
  relianceCall,
  relianceFuture,
} from '@/test/market-fixtures'

vi.mock('@/api/market-data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/market-data')>()
  return {
    ...actual,
    listInstruments: vi.fn(),
    getQuote: vi.fn(),
    getHistoricalCandles: vi.fn(),
    listExpiries: vi.fn(),
    listContracts: vi.fn(),
    getOptionChain: vi.fn(),
  }
})

vi.mock('@/components/market-data/PriceChart.vue', () => ({
  default: {
    name: 'PriceChart',
    props: { candles: { type: Array, default: () => [] } },
    template: '<div data-testid="price-chart" />',
  },
}))

const listInstruments = vi.mocked(marketDataApi.listInstruments)
const getQuote = vi.mocked(marketDataApi.getQuote)
const getHistoricalCandles = vi.mocked(marketDataApi.getHistoricalCandles)
const listExpiries = vi.mocked(marketDataApi.listExpiries)
const listContracts = vi.mocked(marketDataApi.listContracts)
const getOptionChain = vi.mocked(marketDataApi.getOptionChain)

let router: Router

function setup(symbol = 'RELIANCE') {
  const pinia = createPinia()
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const Wrapper = defineComponent({
    render: () =>
      h(TooltipProvider, null, {
        default: () => h(TickerView, { symbol }),
      }),
  })
  render(Wrapper, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] },
  })
}

beforeEach(async () => {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/market',
        name: 'market-search',
        component: { template: '<div />' },
      },
      {
        path: '/market/:symbol',
        name: 'market-ticker',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push('/market/RELIANCE')
  await router.isReady()

  // Derivatives are returned first to prove the underlying resolution picks the equity.
  listInstruments
    .mockReset()
    .mockResolvedValue([relianceCall(), relianceFuture(), RELIANCE])
  getQuote.mockReset().mockResolvedValue(quote())
  getHistoricalCandles.mockReset().mockResolvedValue(candleSeries(CANDLES))
  listExpiries.mockReset().mockResolvedValue([{ expiryDate: '2026-11-26' }])
  listContracts
    .mockReset()
    .mockResolvedValue([
      { brokerSymbol: 'NSE-RELIANCE-26Nov26-FUT' },
      { brokerSymbol: 'NSE-RELIANCE-26Nov26-3000-CE' },
    ])
  getOptionChain.mockReset().mockResolvedValue(optionChain())
})

afterEach(cleanup)

test('resolves the underlying equity and renders quote, session, chart and derivative tabs', async () => {
  setup()

  expect((await screen.findAllByText('₹1,428.35')).length).toBeGreaterThan(0)
  expect(
    (await screen.findAllByText('Reliance Industries Ltd')).length,
  ).toBeGreaterThan(0)
  expect(screen.getByText('Session')).toBeTruthy()
  expect(await screen.findByTestId('price-chart')).toBeTruthy()
  expect(screen.getByRole('tab', { name: 'Futures' })).toBeTruthy()
  expect(screen.getByRole('tab', { name: 'Options' })).toBeTruthy()
})

test('keeps the chart when the quote (and therefore depth) fails', async () => {
  getQuote.mockRejectedValue(
    new ApiError({
      message: 'Market data source unavailable',
      status: 503,
      code: 'BROKER_UNAVAILABLE',
    }),
  )
  setup()

  expect(
    await screen.findByText('Quote unavailable', undefined, { timeout: 3000 }),
  ).toBeTruthy()
  expect(await screen.findByTestId('price-chart')).toBeTruthy()
})

test('changing the interval issues the matching historical request', async () => {
  setup()
  await screen.findByTestId('price-chart')

  await fireEvent.click(screen.getByText('15m'))

  await waitFor(() => {
    const last = getHistoricalCandles.mock.calls.at(-1)?.[0]
    expect(last?.interval).toBe('FIFTEEN_MINUTE')
  })
})

test('handles missing depth without rendering NaN', async () => {
  getQuote.mockResolvedValue(quote({ bids: [], asks: [], openInterest: null }))
  setup()

  await screen.findAllByText('₹1,428.35')
  expect(screen.queryByText(/NaN/)).toBeNull()

  await fireEvent.click(screen.getByRole('tab', { name: 'Depth' }))
  expect(await screen.findByText('No order book depth available.')).toBeTruthy()
})

test('futures tab lists contracts for the underlying', async () => {
  setup()
  await screen.findAllByText('₹1,428.35')

  // reka-ui tabs activate on mousedown, not click.
  await fireEvent.mouseDown(screen.getByRole('tab', { name: 'Futures' }))

  expect(await screen.findByText('NSE-RELIANCE-26Nov26-FUT')).toBeTruthy()
  expect(screen.queryByText('NSE-RELIANCE-26Nov26-3000-CE')).toBeNull()
})

test('options tab renders the option chain for the underlying', async () => {
  setup()
  await screen.findAllByText('₹1,428.35')

  await fireEvent.mouseDown(screen.getByRole('tab', { name: 'Options' }))

  expect(await screen.findByText('3,000.00')).toBeTruthy()
  expect(screen.getByText('12.50')).toBeTruthy()
  expect(screen.getByText('8.20')).toBeTruthy()
})
