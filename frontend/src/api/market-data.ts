import { apiGet } from './http'
import type {
  BrokerCandleInterval,
  BrokerCandleSeries,
  BrokerCapabilities,
  BrokerExchange,
  BrokerInstrument,
  BrokerLtp,
  BrokerOhlc,
  BrokerQuote,
  BrokerSegment,
} from './types'

const BASE = '/api/v1/brokers/groww'

export interface QuoteRequest {
  exchange: BrokerExchange
  segment: BrokerSegment
  tradingSymbol: string
}

export interface HistoricalCandlesRequest {
  exchange: BrokerExchange
  segment: BrokerSegment
  brokerSymbol: string
  start: string
  end: string
  interval: BrokerCandleInterval
}

/** Groww's batched live endpoints key instruments as `EXCHANGE_SYMBOL`. */
export function toExchangeSymbol(
  exchange: BrokerExchange,
  tradingSymbol: string,
): string {
  return `${exchange}_${tradingSymbol}`
}

/**
 * Broker instrument master.
 *
 * The master has ~140k rows; the UI always passes a query so the backend returns a bounded slice.
 * Omitting the query returns the full master (large) and is intended for tooling only.
 */
export function listInstruments(
  query?: string,
  limit?: number,
  signal?: AbortSignal,
): Promise<BrokerInstrument[]> {
  return apiGet<BrokerInstrument[]>(`${BASE}/instruments`, {
    signal,
    params: { query, limit },
  })
}

export function getCapabilities(
  signal?: AbortSignal,
): Promise<BrokerCapabilities> {
  return apiGet<BrokerCapabilities>(`${BASE}/capabilities`, { signal })
}

export function getQuote(
  request: QuoteRequest,
  signal?: AbortSignal,
): Promise<BrokerQuote> {
  return apiGet<BrokerQuote>(`${BASE}/market-data/quote`, {
    signal,
    params: {
      exchange: request.exchange,
      segment: request.segment,
      tradingSymbol: request.tradingSymbol,
    },
  })
}

export function getOhlc(
  segment: BrokerSegment,
  exchangeSymbols: string[],
  signal?: AbortSignal,
): Promise<Record<string, BrokerOhlc>> {
  return apiGet<Record<string, BrokerOhlc>>(`${BASE}/market-data/ohlc`, {
    signal,
    params: { segment, exchangeSymbols: exchangeSymbols.join(',') },
  })
}

export function getLtp(
  segment: BrokerSegment,
  exchangeSymbols: string[],
  signal?: AbortSignal,
): Promise<BrokerLtp[]> {
  return apiGet<BrokerLtp[]>(`${BASE}/market-data/ltp`, {
    signal,
    params: { segment, exchangeSymbols: exchangeSymbols.join(',') },
  })
}

export function getHistoricalCandles(
  request: HistoricalCandlesRequest,
  signal?: AbortSignal,
): Promise<BrokerCandleSeries> {
  return apiGet<BrokerCandleSeries>(`${BASE}/historical/candles`, {
    signal,
    params: {
      exchange: request.exchange,
      segment: request.segment,
      growwSymbol: request.brokerSymbol,
      start: request.start,
      end: request.end,
      interval: request.interval,
    },
  })
}

/** Query-key factory: one source of truth for cache identity and invalidation. */
export const marketDataKeys = {
  all: ['market-data'] as const,
  instruments: () => [...marketDataKeys.all, 'instruments'] as const,
  capabilities: () => [...marketDataKeys.all, 'capabilities'] as const,
  quote: (request: QuoteRequest) =>
    [
      ...marketDataKeys.all,
      'quote',
      request.exchange,
      request.segment,
      request.tradingSymbol,
    ] as const,
  ohlc: (segment: BrokerSegment, exchangeSymbol: string) =>
    [...marketDataKeys.all, 'ohlc', segment, exchangeSymbol] as const,
  history: (request: HistoricalCandlesRequest) =>
    [
      ...marketDataKeys.all,
      'history',
      request.exchange,
      request.segment,
      request.brokerSymbol,
      request.interval,
      request.start,
      request.end,
    ] as const,
}
