import { apiGet } from './http'
import type {
  BrokerCandleInterval,
  BrokerCandleSeries,
  BrokerCapabilities,
  BrokerContract,
  BrokerExchange,
  BrokerExpiry,
  BrokerInstrument,
  BrokerLtp,
  BrokerOhlc,
  BrokerOptionChain,
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

export function listExpiries(
  exchange: BrokerExchange,
  underlying: string,
  signal?: AbortSignal,
): Promise<BrokerExpiry[]> {
  return apiGet<BrokerExpiry[]>(`${BASE}/historical/expiries`, {
    signal,
    params: { exchange, underlying },
  })
}

export function listContracts(
  exchange: BrokerExchange,
  underlying: string,
  expiryDate: string,
  signal?: AbortSignal,
): Promise<BrokerContract[]> {
  return apiGet<BrokerContract[]>(`${BASE}/historical/contracts`, {
    signal,
    params: { exchange, underlying, expiryDate },
  })
}

export function getOptionChain(
  exchange: BrokerExchange,
  underlying: string,
  expiry: string,
  signal?: AbortSignal,
): Promise<BrokerOptionChain> {
  return apiGet<BrokerOptionChain>(`${BASE}/market-data/option-chain`, {
    signal,
    params: { exchange, underlying, expiry },
  })
}

/** Query-key factory: one source of truth for cache identity and invalidation. */
export const marketDataKeys = {
  all: ['market-data'] as const,
  instruments: () => [...marketDataKeys.all, 'instruments'] as const,
  capabilities: () => [...marketDataKeys.all, 'capabilities'] as const,
  expiries: (exchange: BrokerExchange, underlying: string) =>
    [...marketDataKeys.all, 'expiries', exchange, underlying] as const,
  contracts: (
    exchange: BrokerExchange,
    underlying: string,
    expiryDate: string,
  ) =>
    [
      ...marketDataKeys.all,
      'contracts',
      exchange,
      underlying,
      expiryDate,
    ] as const,
  optionChain: (exchange: BrokerExchange, underlying: string, expiry: string) =>
    [
      ...marketDataKeys.all,
      'option-chain',
      exchange,
      underlying,
      expiry,
    ] as const,
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
