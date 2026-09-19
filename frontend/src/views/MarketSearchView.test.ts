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
import MarketSearchView from './MarketSearchView.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import * as marketDataApi from '@/api/market-data'
import { RELIANCE, relianceCall, relianceFuture } from '@/test/market-fixtures'

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

const listInstruments = vi.mocked(marketDataApi.listInstruments)

let router: Router

function setup() {
  const pinia = createPinia()
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const Wrapper = defineComponent({
    render: () =>
      h(TooltipProvider, null, { default: () => h(MarketSearchView) }),
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
  await router.push('/market')
  await router.isReady()

  // The backend guarantees equity-first ranking; the mock mirrors that ordering.
  listInstruments
    .mockReset()
    .mockResolvedValue([RELIANCE, relianceFuture(), relianceCall()])
})

afterEach(cleanup)

test('searching a ticker shows the equity before its derivatives and opens the ticker page', async () => {
  setup()

  await fireEvent.click(screen.getByRole('combobox'))
  const input = await screen.findByPlaceholderText(/Search by symbol/)
  await fireEvent.update(input, 'RELIANCE')

  // The command list is teleported to <body>; query the rendered items directly.
  await waitFor(() => {
    expect(
      document.querySelectorAll('[data-slot="command-item"]').length,
    ).toBeGreaterThanOrEqual(3)
  })
  const options = Array.from(
    document.querySelectorAll<HTMLElement>('[data-slot="command-item"]'),
  )
  expect(options[0]?.textContent).toContain('Reliance Industries Ltd')
  expect(options[1]?.textContent).toMatch(/FUT|CE/)

  await fireEvent.click(options[0] as HTMLElement)

  await waitFor(() => {
    expect(router.currentRoute.value.name).toBe('market-ticker')
  })
  expect(router.currentRoute.value.params.symbol).toBe('RELIANCE')
})
