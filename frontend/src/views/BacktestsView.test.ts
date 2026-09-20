import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/vue'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as backtestsApi from '@/api/backtests'
import type { BacktestRun } from '@/api/backtests'
import BacktestsView from './BacktestsView.vue'

vi.mock('@/api/watchlist', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/watchlist')>()
  return {
    ...actual,
    getWatchlist: vi.fn().mockResolvedValue({
      name: 'Watchlist',
      capacity: 20,
      count: 2,
      entries: [{ symbol: 'SBIN' }, { symbol: 'NIFTY' }],
    }),
  }
})

vi.mock('@/api/catalog', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/catalog')>()
  return {
    ...actual,
    getStrategies: vi.fn().mockResolvedValue([]),
    getRiskPolicies: vi.fn().mockResolvedValue([]),
  }
})

vi.mock('@/api/backtests', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/backtests')>()
  return {
    ...actual,
    getBacktestRuns: vi.fn(),
    getBacktestRun: vi.fn(),
    getBacktestTrades: vi.fn(),
    getBacktestEquity: vi.fn(),
    startBacktest: vi.fn(),
    cancelBacktest: vi.fn(),
  }
})

const getBacktestRuns = vi.mocked(backtestsApi.getBacktestRuns)
const getBacktestRun = vi.mocked(backtestsApi.getBacktestRun)
const getBacktestTrades = vi.mocked(backtestsApi.getBacktestTrades)
const getBacktestEquity = vi.mocked(backtestsApi.getBacktestEquity)

function run(overrides: Partial<BacktestRun> = {}): BacktestRun {
  return {
    runKey: 'run-1',
    experimentRunId: 1,
    backtestRunId: 2,
    status: 'SUCCEEDED',
    strategyId: 'ER_RS_CONTINUATION_V1',
    strategyVersion: 'v1',
    datasetCode: 'CANONICAL_M5',
    datasetChecksum: 'abc123',
    startDate: '2026-08-01',
    endDate: '2026-09-18',
    universeSize: 3,
    startingCapital: 1000000,
    currency: 'INR',
    progressEvents: 100,
    progressTotal: 100,
    progressThrough: '2026-09-18T10:00:00Z',
    createdAt: '2026-09-18T10:00:00Z',
    startedAt: '2026-09-18T10:00:01Z',
    completedAt: '2026-09-18T10:01:00Z',
    metrics: {
      netReturnPct: 1.5,
      netPnl: 15000,
      explicitCosts: 500,
      maxDrawdownPct: 0.8,
      completedTrades: 12,
      openPositions: 0,
      winRatePct: 58.33,
      profitFactor: 1.42,
      notes: {},
    },
    failure: {},
    parameters: { symbols: ['SBIN'], warmup: 30, seed: 1, strict: true },
    ...overrides,
  }
}

function setup(runs: BacktestRun[]) {
  getBacktestRuns.mockResolvedValue(runs)
  getBacktestRun.mockResolvedValue(runs[0] ?? run())
  getBacktestTrades.mockResolvedValue([])
  getBacktestEquity.mockResolvedValue([])
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/backtests',
        name: 'backtests',
        component: { template: '<div/>' },
      },
      {
        path: '/backtests/:runKey',
        name: 'backtest-run',
        component: { template: '<div/>' },
      },
    ],
  })
  router.push('/backtests')
  render(defineComponent({ render: () => h(BacktestsView) }), {
    global: { plugins: [[VueQueryPlugin, { queryClient }], router] },
  })
}

beforeEach(() => {
  getBacktestRuns.mockReset()
  getBacktestRun.mockReset()
  getBacktestTrades.mockReset()
  getBacktestEquity.mockReset()
})

afterEach(cleanup)

test('selects symbols from the finite watchlist universe', async () => {
  setup([run()])
  const sbin = (await screen.findByLabelText(
    'Include SBIN',
  )) as HTMLInputElement
  expect(sbin.checked).toBe(true)
  await fireEvent.click(sbin)
  expect(sbin.checked).toBe(false)
  await fireEvent.click(sbin)
  expect(sbin.checked).toBe(true)
})

test('lists runs with status and completed metrics', async () => {
  setup([run()])
  await screen.findByTestId('run-status-run-1')
  expect(screen.getByTestId('run-status-run-1').textContent).toContain(
    'SUCCEEDED',
  )
  expect(screen.getByText('1.5%')).toBeTruthy()
  expect(screen.getByText('12')).toBeTruthy()
})

test('opens a run and shows explicit no-trade handling', async () => {
  setup([run()])
  await screen.findByTestId('run-status-run-1')
  await fireEvent.click(await screen.findByRole('button', { name: 'Open' }))
  const detail = await screen.findByTestId('backtest-detail')
  await waitFor(() => expect(detail.textContent).toContain('Net return'))
  expect(detail.textContent).toContain('No-trade or zero-risk outcomes')
  await fireEvent.click(screen.getByRole('tab', { name: 'trades' }))
  await waitFor(() =>
    expect(detail.textContent).toContain('No simulated trades for this run'),
  )
})

test('failed runs are labelled and inspectable', async () => {
  setup([run({ status: 'FAILED', metrics: {} })])
  await screen.findByTestId('run-status-run-1')
  expect(screen.getByTestId('run-status-run-1').textContent).toContain('FAILED')
})
