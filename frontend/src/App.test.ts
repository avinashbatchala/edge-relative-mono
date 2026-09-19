import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, render, screen } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { afterEach, expect, test, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from './App.vue'

vi.mock('@/api/market-data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/market-data')>()
  return {
    ...actual,
    listInstruments: vi.fn().mockResolvedValue([]),
    getQuote: vi.fn(),
    getHistoricalCandles: vi.fn(),
    listExpiries: vi.fn(),
    listContracts: vi.fn(),
    getOptionChain: vi.fn(),
  }
})

afterEach(cleanup)

test('renders the market-data shell with navigation and the search landing page', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/market' },
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
  await router.push('/market')
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

  expect(
    screen.getAllByRole('link', { name: /Market Data/ }).length,
  ).toBeGreaterThan(0)
  expect(await screen.findByRole('heading', { level: 1 })).toHaveProperty(
    'textContent',
    'Market Data',
  )
  expect(await screen.findByText('Start with an underlying')).toBeTruthy()
  expect(screen.queryByRole('button', { name: /BUY|SELL/i })).toBeNull()
})
