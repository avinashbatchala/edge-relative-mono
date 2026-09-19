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
import WatchlistView from './WatchlistView.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ApiError } from '@/api/http'
import * as watchlistApi from '@/api/watchlist'
import * as marketDataApi from '@/api/market-data'
import {
  RELIANCE,
  quote,
  watchlistEntry,
  watchlistResponse,
} from '@/test/market-fixtures'

vi.mock('@/api/watchlist', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/watchlist')>()
  return {
    ...actual,
    getWatchlist: vi.fn(),
    addWatchlistItem: vi.fn(),
    removeWatchlistItem: vi.fn(),
    reorderWatchlist: vi.fn(),
  }
})

vi.mock('@/api/market-data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/market-data')>()
  return {
    ...actual,
    listInstruments: vi.fn(),
    getQuote: vi.fn(),
    getLtp: vi.fn().mockResolvedValue([]),
    getHistoricalCandles: vi.fn(),
    listExpiries: vi.fn(),
    listContracts: vi.fn(),
    getOptionChain: vi.fn(),
  }
})

const getWatchlist = vi.mocked(watchlistApi.getWatchlist)
const addWatchlistItem = vi.mocked(watchlistApi.addWatchlistItem)
const removeWatchlistItem = vi.mocked(watchlistApi.removeWatchlistItem)
const listInstruments = vi.mocked(marketDataApi.listInstruments)
const getQuote = vi.mocked(marketDataApi.getQuote)

const TCS = watchlistEntry({
  instrumentId: 2,
  symbol: 'TCS',
  name: 'Tata Consultancy Services',
  brokerSymbol: 'NSE-TCS',
  slot: 2,
})

let router: Router

function setup() {
  const pinia = createPinia()
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const Wrapper = defineComponent({
    render: () => h(TooltipProvider, null, { default: () => h(WatchlistView) }),
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
        path: '/overview',
        name: 'overview',
        component: { template: '<div />' },
      },
      {
        path: '/watchlist',
        name: 'watchlist',
        component: { template: '<div />' },
      },
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
  await router.push('/watchlist')
  await router.isReady()

  getWatchlist.mockReset()
  addWatchlistItem.mockReset()
  removeWatchlistItem.mockReset()
  listInstruments.mockReset().mockResolvedValue([RELIANCE])
  getQuote.mockReset()
})

afterEach(cleanup)

test('lists watched instruments with market data and isolates a failing quote', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([watchlistEntry(), TCS]))
  getQuote.mockImplementation((request) => {
    if (request.tradingSymbol === 'RELIANCE') {
      return Promise.resolve(quote())
    }
    return Promise.reject(
      new ApiError({
        message: 'Market data source unavailable',
        status: 503,
        code: 'BROKER_UNAVAILABLE',
      }),
    )
  })

  setup()

  expect((await screen.findAllByText('₹1,428.35')).length).toBeGreaterThan(0)
  await waitFor(() => expect(screen.getByText('Unavailable')).toBeTruthy())
  expect(screen.getByText('Live')).toBeTruthy()
  expect(screen.getByText('2 / 20 instruments')).toBeTruthy()
})

test('clicking a row opens the instrument detail page', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([watchlistEntry()]))
  getQuote.mockResolvedValue(quote())

  setup()

  await fireEvent.click(await screen.findByLabelText('Open RELIANCE details'))

  await waitFor(() => {
    expect(router.currentRoute.value.name).toBe('market-ticker')
  })
  expect(router.currentRoute.value.params.symbol).toBe('RELIANCE')
})

test('disables adding once the watchlist is full', async () => {
  const entries = Array.from({ length: 20 }, (_, i) =>
    watchlistEntry({
      instrumentId: i + 1,
      symbol: `SYM${i}`,
      name: `Symbol ${i}`,
      brokerSymbol: `NSE-SYM${i}`,
      slot: i + 1,
    }),
  )
  getWatchlist.mockResolvedValue(watchlistResponse(entries))
  getQuote.mockResolvedValue(quote())

  setup()

  await screen.findByText('20 / 20 instruments')
  const addButton = screen.getByRole('button', {
    name: /Add instrument/,
  }) as HTMLButtonElement
  expect(addButton.disabled).toBe(true)
})

test('removes an instrument', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([watchlistEntry()]))
  getQuote.mockResolvedValue(quote())
  removeWatchlistItem.mockResolvedValue(undefined)

  setup()

  await fireEvent.click(await screen.findByLabelText('Remove RELIANCE'))

  await waitFor(() => expect(removeWatchlistItem).toHaveBeenCalledWith(1))
})

test('adds the underlying equity using canonical identity', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([]))
  addWatchlistItem.mockResolvedValue(watchlistEntry())
  listInstruments.mockResolvedValue([RELIANCE])

  setup()

  await screen.findByText('Your watchlist is empty')
  await fireEvent.click(
    screen.getAllByRole('button', { name: /Add instrument/ })[0] as HTMLElement,
  )

  const combo = await screen.findByRole('combobox')
  await fireEvent.click(combo)
  const input = await screen.findByPlaceholderText(/Search by symbol/)
  await fireEvent.update(input, 'RELIANCE')

  await waitFor(() => {
    expect(
      document.querySelectorAll('[data-slot="command-item"]').length,
    ).toBeGreaterThan(0)
  })
  await fireEvent.click(
    document.querySelector<HTMLElement>(
      '[data-slot="command-item"]',
    ) as HTMLElement,
  )

  await waitFor(() => expect(addWatchlistItem).toHaveBeenCalledTimes(1))
  expect(addWatchlistItem.mock.calls[0]?.[0]).toMatchObject({
    exchange: 'NSE',
    segment: 'CASH',
    instrumentType: 'EQ',
    symbol: 'RELIANCE',
    brokerSymbol: 'NSE-RELIANCE',
  })
})

test('surfaces a duplicate add error without breaking the list', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([]))
  addWatchlistItem.mockRejectedValue(
    new ApiError({
      message: 'RELIANCE is already on the active watchlist',
      status: 409,
      code: 'WATCHLIST_DUPLICATE',
    }),
  )
  listInstruments.mockResolvedValue([RELIANCE])

  setup()

  await screen.findByText('Your watchlist is empty')
  await fireEvent.click(
    screen.getAllByRole('button', { name: /Add instrument/ })[0] as HTMLElement,
  )
  const combo = await screen.findByRole('combobox')
  await fireEvent.click(combo)
  const input = await screen.findByPlaceholderText(/Search by symbol/)
  await fireEvent.update(input, 'RELIANCE')
  await waitFor(() => {
    expect(
      document.querySelectorAll('[data-slot="command-item"]').length,
    ).toBeGreaterThan(0)
  })
  await fireEvent.click(
    document.querySelector<HTMLElement>(
      '[data-slot="command-item"]',
    ) as HTMLElement,
  )

  expect(await screen.findByText('Could not add instrument')).toBeTruthy()
  expect(
    screen.getByText('RELIANCE is already on the active watchlist'),
  ).toBeTruthy()
})
