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
import ResearchDataView from './ResearchDataView.vue'
import * as historyApi from '@/api/history'
import * as watchlistApi from '@/api/watchlist'

vi.mock('@/api/history', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/history')>()
  return {
    ...actual,
    getCoverage: vi.fn(),
    getRuns: vi.fn(),
    getCandles: vi.fn(),
    startBackfill: vi.fn(),
    retryRun: vi.fn(),
  }
})

vi.mock('@/components/market-data/PriceChart.vue', () => ({
  default: {
    name: 'PriceChart',
    props: { candles: { type: Array, default: () => [] } },
    template: '<div data-testid="price-chart" />',
  },
}))

vi.mock('@/api/watchlist', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/watchlist')>()
  return {
    ...actual,
    getWatchlist: vi.fn(),
  }
})

const getCoverage = vi.mocked(historyApi.getCoverage)
const getRuns = vi.mocked(historyApi.getRuns)
const getCandles = vi.mocked(historyApi.getCandles)
const startBackfill = vi.mocked(historyApi.startBackfill)
const retryRun = vi.mocked(historyApi.retryRun)

const candle = {
  openTime: '2026-09-18T03:45:00Z',
  open: 100,
  high: 105,
  low: 99,
  close: 104,
  volume: 1000,
  openInterest: null,
  partial: false,
  definitionVersion: 'er-aggregate-v1',
}

const coverage = {
  instrumentId: 7,
  timeframe: 'ONE_MINUTE',
  earliest: '2019-01-01T03:45:00Z',
  latest: '2026-09-19T10:00:00Z',
  candleCount: 12345,
  completedChunks: 4,
  pendingChunks: 1,
  failedChunks: 0,
  lastSyncedAt: '2026-09-19T22:30:00Z',
  status: 'BACKFILLING',
}

const run = {
  runKey: 'run-1',
  instrumentId: 7,
  timeframe: 'ONE_MINUTE',
  requestedFrom: '2019-01-01T00:00:00Z',
  requestedTo: '2026-09-19T00:00:00Z',
  status: 'PARTIAL',
  totalChunks: 5,
  completedChunks: 4,
  failedChunks: 1,
  candlesWritten: 12345,
  lastError: 'timeout',
  createdAt: '2026-09-19T22:00:00Z',
  updatedAt: '2026-09-19T22:30:00Z',
  completedAt: null,
}

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  render(ResearchDataView, {
    global: {
      plugins: [createPinia(), [VueQueryPlugin, { queryClient }]],
    },
  })
}

beforeEach(() => {
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
        instrumentType: 'EQ',
        symbol: 'RELIANCE',
        name: 'Reliance Industries',
        brokerSymbol: 'NSE_RELIANCE',
        tickSize: 0.05,
        lotSize: 1,
        slot: 1,
      },
    ],
  })
  getCoverage.mockReset().mockResolvedValue(coverage)
  getRuns.mockReset().mockResolvedValue([run])
  getCandles.mockReset().mockResolvedValue([candle])
  startBackfill.mockReset().mockResolvedValue(run)
  retryRun.mockReset().mockResolvedValue({ ...run, status: 'RUNNING' })
})

afterEach(cleanup)

test('shows persisted coverage for the default watchlist instrument', async () => {
  setup()

  expect(await screen.findByText('BACKFILLING')).toBeTruthy()
  expect(screen.getAllByText(/candles/i).length).toBeGreaterThan(0)
  expect(getCoverage).toHaveBeenCalledWith(7, 'ONE_MINUTE', expect.anything())
})

test('lists runs and retries a partial run', async () => {
  setup()

  const retry = await screen.findByRole('button', { name: /retry/i })
  expect(screen.getByText('PARTIAL')).toBeTruthy()
  await fireEvent.click(retry)

  await waitFor(() => expect(retryRun).toHaveBeenCalledWith('run-1'))
})

test('renders persisted candles read from the database', async () => {
  setup()

  expect(await screen.findByTestId('price-chart')).toBeTruthy()
  expect(screen.getByText('Persisted history (database)')).toBeTruthy()
  expect(getCandles).toHaveBeenCalledWith(
    7,
    'ONE_DAY',
    expect.any(String),
    expect.any(String),
    5000,
    expect.anything(),
  )
})

test('starting a backfill posts the selected instrument and timeframe', async () => {
  setup()

  await screen.findByText('BACKFILLING')
  await fireEvent.click(
    screen.getByRole('button', { name: /download \/ resume/i }),
  )

  await waitFor(() => expect(startBackfill).toHaveBeenCalledTimes(1))
  const request = startBackfill.mock.calls[0]?.[0]
  expect(request?.instrumentId).toBe(7)
  expect(request?.timeframe).toBe('ONE_MINUTE')
  expect(request?.from).toMatch(/T/)
  expect(request?.to).toMatch(/T/)
})
