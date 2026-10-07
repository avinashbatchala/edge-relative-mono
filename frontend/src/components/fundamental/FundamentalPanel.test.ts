import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import FundamentalPanel from './FundamentalPanel.vue'
import * as fundamentalsApi from '@/api/fundamentals'
import * as llmApi from '@/api/llm'
import { ApiError } from '@/api/http'

vi.mock('@/api/fundamentals', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/fundamentals')>()
  return { ...actual, getFundamentals: vi.fn() }
})

vi.mock('@/api/llm', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/llm')>()
  return { ...actual, narrateFundamentals: vi.fn() }
})

const getFundamentals = vi.mocked(fundamentalsApi.getFundamentals)
const narrateFundamentals = vi.mocked(llmApi.narrateFundamentals)

const FUNDAMENTALS: fundamentalsApi.FundamentalResponse = {
  advisory: true,
  instrumentId: 7,
  exchange: 'NSE',
  symbol: 'RELIANCE',
  provider: 'yahoo-nse',
  sourceRevision: 'rev-1',
  asOf: '2026-04-01T00:00:00Z',
  filedAt: '2026-04-01T00:00:00Z',
  fiscalYear: 'FY2025',
  periodType: 'ANNUAL',
  reportingBasis: 'CONSOLIDATED',
  periodEnd: '2025-03-31',
  statements: [
    {
      lineCode: 'total_revenue',
      label: null,
      value: '9000000000000',
      unit: 'INR',
      scale: 'unit',
    },
  ],
  metrics: [
    { metricCode: 'trailing_pe', value: '25.0', unit: 'ratio', decimals: null },
  ],
}

function setup(instrumentId: number | null) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  render(FundamentalPanel, {
    props: { instrumentId },
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
}

beforeEach(() => {
  getFundamentals.mockReset()
  narrateFundamentals.mockReset()
})

afterEach(cleanup)

test('explains when the instrument is not resolvable', () => {
  setup(null)
  expect(screen.getByText('Fundamentals unavailable')).toBeTruthy()
  expect(getFundamentals).not.toHaveBeenCalled()
})

test('renders code-computed fundamentals', async () => {
  getFundamentals.mockResolvedValue(FUNDAMENTALS)
  setup(7)

  expect(await screen.findByText('code-computed')).toBeTruthy()
  expect(await screen.findByText('P/E (trailing)')).toBeTruthy()
  expect(await screen.findByText('25.00')).toBeTruthy()
  expect(await screen.findByText('Total revenue')).toBeTruthy()
})

test('shows the fetch action when no fundamentals are recorded', async () => {
  getFundamentals.mockRejectedValue(
    new ApiError({
      message: 'none',
      status: 404,
      code: 'FUNDAMENTAL_NOT_FOUND',
    }),
  )
  setup(7)

  expect(await screen.findByText('No fundamentals recorded')).toBeTruthy()
  expect(screen.getByText('Fetch fundamentals')).toBeTruthy()
})

test('requests advisory narration and renders the text', async () => {
  getFundamentals.mockResolvedValue(FUNDAMENTALS)
  narrateFundamentals.mockResolvedValue({
    advisory: true,
    provider: 'deepseek',
    model: 'deepseek-chat',
    text: 'Revenue grew strongly.',
  })
  setup(7)

  await screen.findByText('P/E (trailing)')
  await fireEvent.click(screen.getByText('Explain with AI'))

  expect(await screen.findByText('Revenue grew strongly.')).toBeTruthy()
  expect(narrateFundamentals).toHaveBeenCalledOnce()
  const request = narrateFundamentals.mock.calls[0]?.[0]
  expect(request?.facts).toContain('trailing_pe: 25.0 ratio')
})
