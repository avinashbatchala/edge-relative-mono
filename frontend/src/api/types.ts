/**
 * Types mirroring the Edge Relative broker-neutral API (see the backend
 * `broker-api` models). Decimal money values arrive as JSON numbers.
 */

export type BrokerExchange = 'NSE' | 'BSE' | 'MCX'
export type BrokerSegment = 'CASH' | 'FNO' | 'COMMODITY'
export type BrokerInstrumentType = 'EQ' | 'IDX' | 'FUT' | 'CE' | 'PE' | 'OTHER'

export type BrokerCandleInterval =
  | 'ONE_MINUTE'
  | 'TWO_MINUTE'
  | 'THREE_MINUTE'
  | 'FIVE_MINUTE'
  | 'TEN_MINUTE'
  | 'FIFTEEN_MINUTE'
  | 'THIRTY_MINUTE'
  | 'ONE_HOUR'
  | 'FOUR_HOUR'
  | 'ONE_DAY'
  | 'ONE_WEEK'
  | 'ONE_MONTH'

export interface BrokerInstrument {
  exchange: BrokerExchange
  exchangeToken: string | null
  tradingSymbol: string
  brokerSymbol: string | null
  name: string | null
  instrumentType: BrokerInstrumentType
  segment: BrokerSegment | null
  series: string | null
  isin: string | null
  underlyingSymbol: string | null
  underlyingExchangeToken: string | null
  lotSize: number
  expiryDate: string | null
  strikePrice: number | null
  tickSize: number | null
  freezeQuantity: number | null
  reserved: boolean
  buyAllowed: boolean
  sellAllowed: boolean
}

export interface BrokerOhlc {
  open: number | null
  high: number | null
  low: number | null
  close: number | null
}

export interface BrokerDepthLevel {
  price: number | null
  quantity: number
}

export interface BrokerQuote {
  lastPrice: number | null
  averagePrice: number | null
  dayChange: number | null
  dayChangePercent: number | null
  upperCircuitLimit: number | null
  lowerCircuitLimit: number | null
  ohlc: BrokerOhlc | null
  bids: BrokerDepthLevel[]
  asks: BrokerDepthLevel[]
  bidPrice: number | null
  bidQuantity: number
  offerPrice: number | null
  offerQuantity: number
  volume: number
  lastTradeQuantity: number
  lastTradeTime: string | null
  openInterest: number | null
  previousOpenInterest: number | null
  openInterestDayChange: number | null
  openInterestDayChangePercentage: number | null
  week52High: number | null
  week52Low: number | null
  impliedVolatility: number | null
  marketCap: number | null
  totalBuyQuantity: number | null
  totalSellQuantity: number | null
}

export interface BrokerCandle {
  openTime: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  openInterest: number | null
}

export interface BrokerCandleSeries {
  exchangeSymbol: string
  interval: BrokerCandleInterval
  requestedStart: string
  requestedEnd: string
  closingPrice: number | null
  candles: BrokerCandle[]
}

export interface BrokerLtp {
  exchangeSymbol: string
  lastPrice: number | null
}

export interface BrokerCapabilities {
  broker: string
  capabilities: string[]
}

export interface BrokerExpiry {
  expiryDate: string
}

export interface BrokerContract {
  brokerSymbol: string
}
