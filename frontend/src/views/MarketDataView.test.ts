import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import type {
  BrokerCandle,
  BrokerCandleSeries,
  BrokerInstrument,
  BrokerQuote,
} from '@/api/types'
import { ApiError } from '@/api/http'
import * as marketDataApi from '@/api/market-data'
import MarketDataView from './MarketDataView.vue'
import { useMarketDataStore } from '@/stores/market-data'

vi.mock('@/api/market-data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/market-data')>()
  return {
    ...actual,
    listInstruments: vi.fn(),
    getQuote: vi.fn(),
    getHistoricalCandles: vi.fn(),
  }
})

vi.mock('@/components/market-data/PriceChart.vue', () => ({
  default: {
    name: 'PriceChart',
    props: { candles: { type: Array, default: () => [] } },
    template: '<div data-testid="price-chart" />',
  },
}))

const listInstruments = vi.mocked(marketDataApi.listInstruments)
const getQuote = vi.mocked(marketDataApi.getQuote)
const getHistoricalCandles = vi.mocked(marketDataApi.getHistoricalCandles)

function instrument(
  overrides: Partial<BrokerInstrument> = {},
): BrokerInstrument {
  return {
    exchange: 'NSE',
    exchangeToken: '2885',
    tradingSymbol: 'RELIANCE',
    brokerSymbol: 'NSE-RELIANCE',
    name: 'Reliance Industries Ltd',
    instrumentType: 'EQ',
    segment: 'CASH',
    series: 'EQ',
    isin: 'INE002A01018',
    underlyingSymbol: null,
    underlyingExchangeToken: null,
    lotSize: 1,
    expiryDate: null,
    strikePrice: null,
    tickSize: 0.05,
    freezeQuantity: null,
    reserved: false,
    buyAllowed: true,
    sellAllowed: true,
    ...overrides,
  }
}

const RELIANCE = instrument()
const INFY = instrument({
  tradingSymbol: 'INFY',
  brokerSymbol: 'NSE-INFY',
  name: 'Infosys Ltd',
  isin: 'INE009A01021',
})

function quote(overrides: Partial<BrokerQuote> = {}): BrokerQuote {
  return {
    lastPrice: 1428.35,
    averagePrice: 1415.5,
    dayChange: 20.05,
    dayChangePercent: 1.42,
    upperCircuitLimit: 1600,
    lowerCircuitLimit: 1300,
    ohlc: { open: 1410.5, high: 1435.2, low: 1405.1, close: 1428.35 },
    bids: [
      { price: 1428.2, quantity: 12420 },
      { price: 1428.15, quantity: 9750 },
    ],
    asks: [
      { price: 1428.35, quantity: 8210 },
      { price: 1428.4, quantity: 7455 },
    ],
    bidPrice: 1428.2,
    bidQuantity: 12420,
    offerPrice: 1428.35,
    offerQuantity: 8210,
    volume: 1_234_567,
    lastTradeQuantity: 50,
    lastTradeTime: '2026-09-19T09:02:08Z',
    openInterest: 912_345,
    previousOpenInterest: 900_000,
    openInterestDayChange: 12345,
    openInterestDayChangePercentage: 1.37,
    week52High: 1600,
    week52Low: 1200,
    impliedVolatility: null,
    marketCap: null,
    totalBuyQuantity: 100000,
    totalSellQuantity: 90000,
    ...overrides,
  }
}

function candleSeries(candles: BrokerCandle[]): BrokerCandleSeries {
  return {
    exchangeSymbol: 'NSE-RELIANCE',
    interval: 'FIVE_MINUTE',
    requestedStart: '2026-09-19T03:45:00Z',
    requestedEnd: '2026-09-19T10:00:00Z',
    closingPrice: 1428.35,
    candles,
  }
}

const CANDLES: BrokerCandle[] = [
  {
    openTime: '2026-09-19T03:45:00Z',
    open: 1410.5,
    high: 1415,
    low: 1408,
    close: 1412,
    volume: 1000,
    openInterest: null,
  },
  {
    openTime: '2026-09-19T03:50:00Z',
    open: 1412,
    high: 1430,
    low: 1411,
    close: 1428.35,
    volume: 1500,
    openInterest: null,
  },
]

function setup() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 0, refetchInterval: false },
    },
  })
  const Wrapper = defineComponent({
    render: () =>
      h(TooltipProvider, null, { default: () => h(MarketDataView) }),
  })
  const utils = render(Wrapper, {
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient }]] },
  })
  return { ...utils, store: useMarketDataStore(), queryClient }
}

beforeEach(() => {
  listInstruments.mockReset().mockResolvedValue([RELIANCE, INFY])
  getQuote.mockReset().mockResolvedValue(quote())
  getHistoricalCandles.mockReset().mockResolvedValue(candleSeries(CANDLES))
})

afterEach(() => {
  cleanup()
})

describe('MarketDataView', () => {
  test('shows a purposeful empty state before any instrument is selected', async () => {
    const { store } = setup()
    expect(await screen.findByText('No instrument selected')).toBeTruthy()
    expect(store.selectedInstrument).toBeNull()
  })

  test('searching selects an instrument and renders quote, session stats and depth', async () => {
    setup()

    await fireEvent.click(screen.getByRole('combobox'))

    // Opening the selector loads a first page even before any query is typed.
    await waitFor(() => {
      expect(listInstruments.mock.calls.length).toBeGreaterThan(0)
      expect(listInstruments.mock.calls[0]?.[1]).toBe(50)
    })

    const input = await screen.findByPlaceholderText(/Search by symbol/)
    await fireEvent.update(input, 'RELIANCE')

    // Search is debounced and performed server-side with a bounded limit.
    await waitFor(() => {
      const last = listInstruments.mock.calls.at(-1)
      expect(last?.[0]).toBe('RELIANCE')
      expect(last?.[1]).toBe(50)
    })

    await fireEvent.click(await screen.findByText('Reliance Industries Ltd'))

    expect((await screen.findAllByText('₹1,428.35')).length).toBeGreaterThan(0)
    expect(screen.getByText('+20.05')).toBeTruthy()
    expect(screen.getByText('+1.42%')).toBeTruthy()
    expect(screen.getByText('Session')).toBeTruthy()
    expect(await screen.findByTestId('price-chart')).toBeTruthy()

    await fireEvent.click(screen.getByText('Depth'))
    expect(await screen.findByText('12,420')).toBeTruthy()
    expect(screen.getByText('1,428.35')).toBeTruthy()
  })

  test('keeps the chart when the quote (and therefore depth) fails', async () => {
    getQuote.mockRejectedValue(
      new ApiError({
        message: 'Market data source unavailable',
        status: 503,
        code: 'BROKER_UNAVAILABLE',
      }),
    )
    const { store } = setup()
    store.selectInstrument(RELIANCE)

    expect(
      await screen.findByText('Quote unavailable', undefined, {
        timeout: 3000,
      }),
    ).toBeTruthy()
    expect(await screen.findByTestId('price-chart')).toBeTruthy()
  })

  test('a late response for a previous instrument never overwrites the current one', async () => {
    const resolvers = new Map<string, (value: BrokerQuote) => void>()
    getQuote.mockImplementation(
      (request) =>
        new Promise<BrokerQuote>((resolve) => {
          resolvers.set(request.tradingSymbol, resolve)
        }),
    )

    const { store } = setup()
    store.selectInstrument(RELIANCE)
    store.selectInstrument(INFY)

    await waitFor(() => expect(resolvers.has('INFY')).toBe(true))
    resolvers.get('INFY')?.(
      quote({ lastPrice: 2000, dayChange: 10, dayChangePercent: 0.5 }),
    )
    expect(await screen.findByText('₹2,000.00')).toBeTruthy()

    // RELIANCE resolves last; it must not replace INFY on screen.
    resolvers.get('RELIANCE')?.(quote({ lastPrice: 100 }))
    await waitFor(() => expect(screen.getByText('₹2,000.00')).toBeTruthy())
    expect(screen.queryByText('₹100.00')).toBeNull()
  })

  test('changing the interval issues the matching historical request', async () => {
    const { store } = setup()
    store.selectInstrument(RELIANCE)
    await screen.findByTestId('price-chart')

    await fireEvent.click(screen.getByText('15m'))

    await waitFor(() => {
      const calls = getHistoricalCandles.mock.calls
      const last = calls.at(-1)?.[0]
      expect(last?.interval).toBe('FIFTEEN_MINUTE')
    })
  })

  test('handles missing depth and open interest without rendering NaN', async () => {
    getQuote.mockResolvedValue(
      quote({ bids: [], asks: [], openInterest: null, lastPrice: 1428.35 }),
    )
    const { store } = setup()
    store.selectInstrument(RELIANCE)

    expect((await screen.findAllByText('₹1,428.35')).length).toBeGreaterThan(0)
    expect(screen.queryByText(/NaN/)).toBeNull()

    await fireEvent.click(screen.getByText('Depth'))
    expect(
      await screen.findByText('No order book depth available.'),
    ).toBeTruthy()
  })
})
