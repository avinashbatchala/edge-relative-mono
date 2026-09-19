import { apiGet, apiPost } from './http'
import type { BrokerCandleInterval } from './types'

const BASE = '/api/v1/history'

export interface CoverageResponse {
  instrumentId: number
  timeframe: string
  earliest: string | null
  latest: string | null
  candleCount: number
  completedChunks: number
  pendingChunks: number
  failedChunks: number
  lastSyncedAt: string | null
  status: string
}

export interface BackfillRunResponse {
  runKey: string
  instrumentId: number
  timeframe: string
  requestedFrom: string
  requestedTo: string
  status: string
  totalChunks: number
  completedChunks: number
  failedChunks: number
  candlesWritten: number
  lastError: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export interface StartBackfillRequest {
  instrumentId: number
  timeframe: BrokerCandleInterval
  from: string
  to: string
}

/**
 * A canonical candle. M1 is read from the database; higher timeframes are derived from it.
 * `partial` marks a bar truncated by the session boundary; `definitionVersion` records how
 * the bar was constructed.
 */
export interface HistoryCandle {
  openTime: string
  open: number | null
  high: number
  low: number
  close: number
  volume: number
  openInterest: number | null
  partial: boolean
  definitionVersion: string
}

export function getCoverage(
  instrumentId: number,
  timeframe: BrokerCandleInterval,
  signal?: AbortSignal,
): Promise<CoverageResponse> {
  return apiGet<CoverageResponse>(`${BASE}/coverage`, {
    signal,
    params: { instrumentId, timeframe },
  })
}

export function startBackfill(
  request: StartBackfillRequest,
): Promise<BackfillRunResponse> {
  return apiPost<BackfillRunResponse>(`${BASE}/backfill`, request)
}

export function getRuns(
  instrumentId: number,
  limit = 20,
  signal?: AbortSignal,
): Promise<BackfillRunResponse[]> {
  return apiGet<BackfillRunResponse[]>(`${BASE}/backfill`, {
    signal,
    params: { instrumentId, limit },
  })
}

export function retryRun(runKey: string): Promise<BackfillRunResponse> {
  return apiPost<BackfillRunResponse>(`${BASE}/backfill/${runKey}/retry`)
}

export function getCandles(
  instrumentId: number,
  timeframe: BrokerCandleInterval,
  from: string,
  to: string,
  limit = 5000,
  signal?: AbortSignal,
): Promise<HistoryCandle[]> {
  return apiGet<HistoryCandle[]>(`${BASE}/candles`, {
    signal,
    params: { instrumentId, timeframe, from, to, limit },
  })
}

export const historyKeys = {
  all: ['history'] as const,
  coverage: (instrumentId: number, timeframe: string) =>
    [...historyKeys.all, 'coverage', instrumentId, timeframe] as const,
  runs: (instrumentId: number) =>
    [...historyKeys.all, 'runs', instrumentId] as const,
  candles: (
    instrumentId: number,
    timeframe: string,
    from: string,
    to: string,
  ) =>
    [...historyKeys.all, 'candles', instrumentId, timeframe, from, to] as const,
}
