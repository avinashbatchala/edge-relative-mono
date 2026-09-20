import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/vue'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { FeatureDashboardRow } from '@/api/features'
import * as featureApi from '@/api/features'
import { useFeatureStreamStore } from '@/stores/feature-stream'
import FeatureDashboardView from './FeatureDashboardView.vue'

vi.mock('@/api/features', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/features')>()
  return {
    ...actual,
    getFeatureDashboard: vi.fn(),
    getFeatureDiagnostics: vi.fn(),
  }
})

vi.mock('@/composables/useFeatureStream', () => ({
  useFeatureStream: () => ({
    connect: vi.fn(),
    disconnect: vi.fn(),
    requestResync: vi.fn(),
  }),
}))

vi.mock('@/components/feature/FeatureHistoryPanel.vue', () => ({
  default: {
    name: 'FeatureHistoryPanel',
    props: { instrumentId: { type: Number }, symbol: { type: String } },
    template: '<div data-testid="history-panel">{{ symbol }}</div>',
  },
}))

const getFeatureDashboard = vi.mocked(featureApi.getFeatureDashboard)
const getFeatureDiagnostics = vi.mocked(featureApi.getFeatureDiagnostics)

function row(
  instrumentId: number,
  symbol: string,
  values: Partial<FeatureDashboardRow> = {},
): FeatureDashboardRow {
  return {
    instrumentId,
    instrumentKey: `key-${instrumentId}`,
    symbol,
    displayName: symbol,
    exchange: 'NSE',
    segment: 'CASH',
    instrumentType: 'EQUITY',
    timeframe: 'M5',
    observationTime: '2026-09-20T10:00:00Z',
    generatedAt: '2026-09-20T10:00:01Z',
    lastPrice: 100,
    previousClose: 99,
    priceChange: 1,
    priceChangePercent: 1.0,
    rrsRaw: 1,
    rrsFast: 1,
    rrsSlow: 1,
    rrsPersistence: 1,
    rrsTrendState: 'POSITIVE_RISING',
    dailyRrsState: 'POSITIVE',
    rvolInterval: 1.2,
    rvolCumulative: 1.1,
    rve: 0.1,
    atr: 2,
    atrPercent: 2,
    vwapDistanceAtr: null,
    marketState: 'BULL_STRUCTURE',
    sectorState: 'BULL_STRUCTURE',
    sectorRrsRaw: 0.5,
    quality: 'GOOD',
    availability: 'VALID',
    qualityReason: null,
    staleSeconds: 5,
    featureSchemaVersion: 'er-feature-schema-v1',
    featureVersions: { RRS_RAW: 'RRS_V1@abc' },
    unavailableReasons: {
      VWAP_DISTANCE_ATR: 'VWAP feature is not implemented',
    },
    ...values,
  }
}

function diagnostics(instrumentIds: number[]) {
  return {
    generatedAt: '2026-09-20T10:00:01Z',
    engineStatus: 'UP',
    timeframe: 'M5',
    watchlistCount: instrumentIds.length,
    healthyCount: instrumentIds.length,
    stateCounts: { HEALTHY: instrumentIds.length },
    metricGaps: { RRS_RAW: 0 },
    latestObservationSeconds: 5,
    oldestObservationSeconds: 5,
    persistenceQueueDepth: 0,
    persistenceDropped: 0,
    counters: {
      snapshots: 10,
      warmupFailures: 0,
      missingDependencies: 0,
      alignmentFailures: 0,
      qualityDowngrades: 0,
    },
    versions: {
      featureSchemaVersion: 'er-feature-schema-v1',
      calculationVersion: 'er-feature-calc-v1',
    },
    instruments: instrumentIds.map((instrumentId) => ({
      instrumentId,
      symbol: `SYM${instrumentId}`,
      state: 'HEALTHY',
      reasonCode: null,
      staleSeconds: 5,
      quality: 'GOOD',
    })),
    notes: ['No live market-data event producer is wired.'],
  }
}

function setup(rows: FeatureDashboardRow[]) {
  getFeatureDashboard.mockResolvedValue(rows)
  getFeatureDiagnostics.mockResolvedValue(
    diagnostics(rows.map((r) => r.instrumentId)),
  )
  const pinia = createPinia()
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
  router.push('/features')
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const Wrapper = defineComponent({
    render: () => h(FeatureDashboardView),
  })
  render(Wrapper, {
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient }]],
    },
  })
  return { pinia, router }
}

beforeEach(() => {
  getFeatureDashboard.mockReset()
  getFeatureDiagnostics.mockReset()
})

afterEach(cleanup)

test('renders watchlist rows and explains missing metrics', async () => {
  setup([
    row(1, 'SYM1', { rrsRaw: 0.5 }),
    row(2, 'SYM2', {
      rrsRaw: null,
      unavailableReasons: { RRS_RAW: 'baseline samples=3' },
    }),
  ])

  const table = await screen.findByTestId('feature-table')
  expect(within(table).getByText('SYM1')).toBeTruthy()
  expect(within(table).getByText('SYM2')).toBeTruthy()
  // Missing metric is visible as an explicit dash with a reason, not zero.
  expect(within(table).getAllByText('—').length).toBeGreaterThan(0)
  expect(
    within(table).getAllByTitle('baseline samples=3').length,
  ).toBeGreaterThan(0)
  expect(screen.getByText(/Showing 2 of 2 watchlist rows/)).toBeTruthy()
})

test('sorts by RRS and keeps missing values last', async () => {
  setup([row(1, 'SYM1', { rrsRaw: 0.5 }), row(2, 'SYM2', { rrsRaw: -3 })])
  const table = await screen.findByTestId('feature-table')

  // Default sort is absolute RRS desc: |−3| > |0.5|.
  let symbols = within(table)
    .getAllByText(/^SYM\d$/)
    .map((node) => node.textContent)
  expect(symbols).toEqual(['SYM2', 'SYM1'])

  await fireEvent.click(screen.getByRole('button', { name: 'Sort by RRS' }))
  await waitFor(() => {
    symbols = within(table)
      .getAllByText(/^SYM\d$/)
      .map((node) => node.textContent)
    expect(symbols).toEqual(['SYM1', 'SYM2'])
  })
})

test('filters by symbol search and clears', async () => {
  setup([row(1, 'SYM1'), row(2, 'SYM2')])
  const table = await screen.findByTestId('feature-table')

  await fireEvent.update(
    screen.getByLabelText('Search watchlist symbols'),
    'SYM2',
  )
  await waitFor(() => {
    expect(screen.queryByText('SYM1')).toBeNull()
    expect(within(table).getByText('SYM2')).toBeTruthy()
    expect(screen.getByText(/Showing 1 of 2 watchlist rows/)).toBeTruthy()
  })

  await fireEvent.click(screen.getByRole('button', { name: /Clear/ }))
  await waitFor(() => expect(within(table).getByText('SYM1')).toBeTruthy())
})

test('stock names are links that open each instrument in its own tab', async () => {
  setup([row(1, 'SYM1')])
  const table = await screen.findByTestId('feature-table')

  const link = within(table).getByRole('link', { name: /SYM1/ })
  expect(link.getAttribute('href')).toBe('/features/SYM1')
  expect(link.getAttribute('target')).toBe('_blank')
  expect(link.getAttribute('rel')).toContain('noopener')

  // Row actions still open the in-app feature history drawer.
  await fireEvent.click(
    screen.getByRole('button', { name: 'Actions for SYM1' }),
  )
  await fireEvent.click(await screen.findByText('Open feature history'))
  expect(await screen.findByTestId('history-panel')).toBeTruthy()
  expect(screen.getByTestId('history-panel').textContent).toContain('SYM1')
})

test('row actions open the feature view in a new tab', async () => {
  const open = vi.spyOn(window, 'open').mockReturnValue(null)
  setup([row(1, 'SYM1')])
  await screen.findByTestId('feature-table')

  await fireEvent.click(
    screen.getByRole('button', { name: 'Actions for SYM1' }),
  )
  await fireEvent.click(await screen.findByText('Open feature view'))
  expect(open).toHaveBeenCalledWith(
    '/features/SYM1',
    '_blank',
    'noopener,noreferrer',
  )
  open.mockRestore()
})

test('row actions open market data in a new tab', async () => {
  const open = vi.spyOn(window, 'open').mockReturnValue(null)
  setup([row(1, 'SYM1')])
  await screen.findByTestId('feature-table')

  await fireEvent.click(
    screen.getByRole('button', { name: 'Actions for SYM1' }),
  )
  await fireEvent.click(await screen.findByText('Open market data'))
  expect(open).toHaveBeenCalledWith(
    '/market/SYM1',
    '_blank',
    'noopener,noreferrer',
  )
  open.mockRestore()
})

test('surfaces the reasoning at the status cell, never raw keys', async () => {
  setup([
    row(1, 'SYM1', {
      quality: 'DEGRADED',
      availability: 'VALID',
      qualityReason: 'needs 20 prior sessions, found 3',
      unavailableReasons: {
        RVOL_INTERVAL: 'needs 20 prior sessions, found 3',
        VWAP_DISTANCE_ATR: 'VWAP feature is not implemented',
      },
    }),
  ])
  await screen.findByTestId('feature-table')

  // The status badge exposes the reason to hover and assistive technology.
  expect(screen.getByLabelText(/needs 20 prior sessions, found 3/)).toBeTruthy()
  expect(screen.queryByText('RVOL_INTERVAL')).toBeNull()
})

test('distinguishes a genuine zero from a missing value', async () => {
  setup([
    row(1, 'SYM1', {
      rrsRaw: null,
      unavailableReasons: { RRS_RAW: 'baseline samples=3' },
    }),
    row(2, 'SYM2', { rrsRaw: 0, rvolInterval: 0 }),
  ])
  await screen.findByTestId('feature-table')

  // Missing RRS is an explicit dash with a reason, never coerced to zero.
  expect(screen.getAllByTitle('baseline samples=3').length).toBeGreaterThan(0)
  // A legitimate zero renders as a numeric value.
  expect(screen.getAllByText('0.00').length).toBeGreaterThan(0)
})

test('activates a row from the keyboard to open it in a new tab', async () => {
  const open = vi.spyOn(window, 'open').mockReturnValue(null)
  setup([row(1, 'SYM1')])
  await screen.findByTestId('feature-table')

  const rowElement = screen.getByLabelText('Open SYM1 in a new tab')
  await fireEvent.keyDown(rowElement, { key: 'Enter' })

  expect(open).toHaveBeenCalledWith(
    '/features/SYM1',
    '_blank',
    'noopener,noreferrer',
  )
  open.mockRestore()
})

test('exposes the sort direction to assistive technology', async () => {
  setup([row(1, 'SYM1')])
  await screen.findByTestId('feature-table')

  const header = screen.getByText('RRS').closest('th') as HTMLElement
  expect(header.getAttribute('aria-sort')).toBe('none')

  await fireEvent.click(screen.getByRole('button', { name: 'Sort by RRS' }))
  await waitFor(() =>
    expect(header.getAttribute('aria-sort')).toBe('descending'),
  )
})

test('preserves filters and selection across a live update', async () => {
  const { pinia } = setup([row(1, 'SYM1'), row(2, 'SYM2')])
  await screen.findByTestId('feature-table')

  await fireEvent.update(
    screen.getByLabelText('Search watchlist symbols'),
    'SYM1',
  )
  await waitFor(() => expect(screen.queryByText('SYM2')).toBeNull())
  await fireEvent.click(
    screen.getByRole('button', { name: 'Actions for SYM1' }),
  )
  await fireEvent.click(await screen.findByText('Open feature history'))
  await screen.findByTestId('history-panel')

  const store = useFeatureStreamStore(pinia)
  store.mergeRows([row(1, 'SYM1', { rrsRaw: 9 }), row(3, 'SYM3')])

  await waitFor(() => expect(screen.getByText(/Search: SYM1/)).toBeTruthy())
  // The active filter keeps excluding the other instruments.
  expect(screen.queryByText('SYM2')).toBeNull()
  expect(screen.queryByText('SYM3')).toBeNull()
  // The open inspection panel is not dismissed by the update.
  expect(screen.getByTestId('history-panel')).toBeTruthy()
})
