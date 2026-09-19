import { apiGet, apiPost } from './http'

const BASE = '/api/v1/history'

/** Registered timeframes (DD-05 §96). M1 is stored; the rest are derived on read. */
export const HISTORY_TIMEFRAMES = [
  { value: 'M1', label: '1m' },
  { value: 'M3', label: '3m' },
  { value: 'M5', label: '5m' },
  { value: 'M15', label: '15m' },
  { value: 'M30', label: '30m' },
  { value: 'H1', label: '1h' },
  { value: 'H2', label: '2h' },
  { value: 'H4', label: '4h' },
  { value: 'D1', label: '1D' },
  { value: 'W1', label: '1W' },
] as const

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
  timeframe: string
  from: string
  to: string
}

/**
 * A canonical candle. M1 is read from the database; higher timeframes are derived from it.
 * `partial` marks a bar truncated by the session boundary; `complete` marks a finalized bar;
 * `qualityState` is `INCOMPLETE` when a required minute is missing; `definitionVersion` records
 * how the bar was constructed.
 */
export interface HistoryCandle {
  openTime: string
  closeTime: string
  open: number | null
  high: number
  low: number
  close: number
  volume: number
  openInterest: number | null
  tradeCount: number | null
  vwap: number | null
  partial: boolean
  complete: boolean
  qualityState: string
  definitionVersion: string
}

export function getCoverage(
  instrumentId: number,
  timeframe: string,
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

export function getCandles(
  instrumentId: number,
  timeframe: string,
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
  candles: (
    instrumentId: number,
    timeframe: string,
    from: string,
    to: string,
  ) =>
    [...historyKeys.all, 'candles', instrumentId, timeframe, from, to] as const,
}
