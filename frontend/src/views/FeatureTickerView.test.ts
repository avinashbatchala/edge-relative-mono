import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import FeatureTickerView from './FeatureTickerView.vue'
import * as watchlistApi from '@/api/watchlist'
import { watchlistEntry, watchlistResponse } from '@/test/market-fixtures'

vi.mock('@/api/watchlist', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/watchlist')>()
  return { ...actual, getWatchlist: vi.fn() }
})

vi.mock('@/components/feature/FeatureHistoryPanel.vue', () => ({
  default: {
    name: 'FeatureHistoryPanel',
    props: { instrumentId: { type: Number }, symbol: { type: String } },
    template:
      '<div data-testid="history-panel">{{ symbol }}#{{ instrumentId }}</div>',
  },
}))

const getWatchlist = vi.mocked(watchlistApi.getWatchlist)

function setup(symbol: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/features',
        name: 'feature-dashboard',
        component: { template: '<div />' },
      },
      {
        path: '/features/:symbol',
        name: 'feature-ticker',
        component: { template: '<div />' },
      },
      {
        path: '/market/:symbol',
        name: 'market-ticker',
        component: { template: '<div />' },
      },
    ],
  })
  router.push(`/features/${symbol}`)
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const Wrapper = defineComponent({
    render: () => h(FeatureTickerView, { symbol }),
  })
  render(Wrapper, {
    global: {
      plugins: [createPinia(), router, [VueQueryPlugin, { queryClient }]],
    },
  })
  return { router }
}

beforeEach(() => {
  getWatchlist.mockReset()
})

afterEach(cleanup)

test('resolves the watchlist instrument and shows its feature history', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([watchlistEntry()]))
  setup('RELIANCE')

  expect(await screen.findByTestId('history-panel')).toBeTruthy()
  expect(screen.getByTestId('history-panel').textContent).toContain(
    'RELIANCE#1',
  )
  expect(screen.getByRole('link', { name: /Feature Dashboard/ })).toBeTruthy()
})

test('explains when the symbol is not on the active watchlist', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([]))
  setup('NOPE')

  expect(await screen.findByText(/is not on the active watchlist/)).toBeTruthy()
  expect(screen.queryByTestId('history-panel')).toBeNull()
})

test('opens the market data tab on demand', async () => {
  getWatchlist.mockResolvedValue(watchlistResponse([watchlistEntry()]))
  const open = vi.spyOn(window, 'open').mockReturnValue(null)
  setup('RELIANCE')

  await fireEvent.click(
    await screen.findByRole('button', { name: /Open market data/ }),
  )
  expect(open).toHaveBeenCalledWith(
    '/market/RELIANCE',
    '_blank',
    'noopener,noreferrer',
  )
  open.mockRestore()
})
