import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/vue'
import { createPinia } from 'pinia'
import { afterEach, expect, test, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from './App.vue'
import * as watchlistApi from '@/api/watchlist'

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
    listInstruments: vi.fn().mockResolvedValue([]),
    getLtp: vi.fn().mockResolvedValue([]),
    getQuote: vi.fn(),
    getHistoricalCandles: vi.fn(),
    listExpiries: vi.fn(),
    listContracts: vi.fn(),
    getOptionChain: vi.fn(),
  }
})

vi.mocked(watchlistApi.getWatchlist).mockResolvedValue({
  name: 'Active',
  capacity: 20,
  count: 0,
  entries: [],
})

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/overview' },
      {
        path: '/overview',
        name: 'overview',
        component: () => import('@/views/OverviewView.vue'),
      },
      {
        path: '/watchlist',
        name: 'watchlist',
        component: () => import('@/views/WatchlistView.vue'),
      },
      {
        path: '/market',
        name: 'market-search',
        component: () => import('@/views/MarketSearchView.vue'),
      },
      {
        path: '/market/:symbol',
        name: 'market-ticker',
        component: () => import('@/views/TickerView.vue'),
      },
    ],
  })
}

afterEach(cleanup)

test('sidebar exposes tools and highlights the active route', async () => {
  const router = buildRouter()
  await router.push('/overview')
  await router.isReady()

  render(App, {
    global: {
      plugins: [
        createPinia(),
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

  const overviewLink = screen.getByRole('link', { name: 'Overview' })
  expect(overviewLink.getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('link', { name: 'Watchlist' })).toBeTruthy()
  expect(screen.getByRole('link', { name: 'Market Data' })).toBeTruthy()
  expect(
    await screen.findByRole('heading', { name: 'Overview', level: 1 }),
  ).toBeTruthy()
})

test('navigating to Watchlist updates the route and active state', async () => {
  const router = buildRouter()
  await router.push('/overview')
  await router.isReady()

  render(App, {
    global: {
      plugins: [
        createPinia(),
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

  await fireEvent.click(screen.getByRole('link', { name: 'Watchlist' }))

  await waitFor(() => {
    expect(router.currentRoute.value.name).toBe('watchlist')
  })
  expect(
    screen
      .getByRole('link', { name: 'Watchlist' })
      .getAttribute('aria-current'),
  ).toBe('page')
  expect(
    await screen.findByRole('heading', { name: 'Watchlist', level: 1 }),
  ).toBeTruthy()
})
