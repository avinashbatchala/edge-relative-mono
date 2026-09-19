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
  }
})

afterEach(cleanup)

test('renders the market-data workstation shell with navigation', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/market-data' },
      {
        path: '/market-data',
        name: 'market-data',
        component: () => import('@/views/MarketDataView.vue'),
      },
    ],
  })
  await router.push('/market-data')
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
  expect(await screen.findByText('No instrument selected')).toBeTruthy()
  expect(screen.queryByRole('button', { name: /BUY|SELL/i })).toBeNull()
})
