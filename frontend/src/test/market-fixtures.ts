import type {
  BrokerCandle,
  BrokerCandleSeries,
  BrokerInstrument,
  BrokerOptionChain,
  BrokerQuote,
} from '@/api/types'

export function instrument(
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

export const RELIANCE = instrument()

export const INFY = instrument({
  tradingSymbol: 'INFY',
  brokerSymbol: 'NSE-INFY',
  name: 'Infosys Ltd',
  isin: 'INE009A01021',
})

export function relianceCall(
  overrides: Partial<BrokerInstrument> = {},
): BrokerInstrument {
  return instrument({
    tradingSymbol: 'RELIANCE26NOV3000CE',
    brokerSymbol: 'NSE-RELIANCE-26Nov26-3000-CE',
    name: null,
    instrumentType: 'CE',
    segment: 'FNO',
    series: null,
    isin: null,
    underlyingSymbol: 'RELIANCE',
    strikePrice: 3000,
    expiryDate: '2026-11-26',
    lotSize: 250,
    ...overrides,
  })
}

export function relianceFuture(): BrokerInstrument {
  return instrument({
    tradingSymbol: 'RELIANCE26NOV-FUT',
    brokerSymbol: 'NSE-RELIANCE-26Nov26-FUT',
    name: null,
    instrumentType: 'FUT',
    segment: 'FNO',
    series: null,
    isin: null,
    underlyingSymbol: 'RELIANCE',
    expiryDate: '2026-11-26',
    lotSize: 250,
  })
}

export function quote(overrides: Partial<BrokerQuote> = {}): BrokerQuote {
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

export const CANDLES: BrokerCandle[] = [
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

export function candleSeries(candles: BrokerCandle[]): BrokerCandleSeries {
  return {
    exchangeSymbol: 'NSE-RELIANCE',
    interval: 'FIVE_MINUTE',
    requestedStart: '2026-09-19T03:45:00Z',
    requestedEnd: '2026-09-19T10:00:00Z',
    closingPrice: 1428.35,
    candles,
  }
}

export function optionChain(): BrokerOptionChain {
  return {
    underlying: 'RELIANCE',
    expiryDate: '2026-11-26',
    underlyingLastPrice: 1428.35,
    strikes: [
      {
        strikePrice: 3000,
        call: {
          tradingSymbol: 'RELIANCE26NOV3000CE',
          lastPrice: 12.5,
          openInterest: 4000,
          volume: 1200,
          greeks: null,
        },
        put: {
          tradingSymbol: 'RELIANCE26NOV3000PE',
          lastPrice: 8.2,
          openInterest: 3000,
          volume: 900,
          greeks: null,
        },
      },
    ],
  }
}
