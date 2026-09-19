import { apiDelete, apiGet, apiPost, apiPut } from './http'

const BASE = '/api/v1/watchlist'

/** Canonical watched instrument. `brokerSymbol` is a resolved mapping detail, not identity. */
export interface WatchlistEntry {
  instrumentId: number
  instrumentKey: string
  exchange: string
  segment: string | null
  instrumentType: string
  symbol: string
  name: string | null
  brokerSymbol: string | null
  tickSize: number | null
  lotSize: number | null
  slot: number
}

export interface WatchlistResponse {
  name: string
  capacity: number
  count: number
  entries: WatchlistEntry[]
}

export interface AddWatchlistItemRequest {
  exchange: string
  segment: string
  instrumentType: string
  symbol: string
  name: string | null
  brokerSymbol: string | null
  tickSize: number | null
  lotSize: number | null
}

export function getWatchlist(signal?: AbortSignal): Promise<WatchlistResponse> {
  return apiGet<WatchlistResponse>(BASE, { signal })
}

export function addWatchlistItem(
  request: AddWatchlistItemRequest,
  signal?: AbortSignal,
): Promise<WatchlistEntry> {
  return apiPost<WatchlistEntry>(`${BASE}/items`, request, { signal })
}

export function removeWatchlistItem(
  instrumentId: number,
  signal?: AbortSignal,
): Promise<void> {
  return apiDelete<void>(`${BASE}/items/${instrumentId}`, { signal })
}

export function reorderWatchlist(
  instrumentIds: number[],
  signal?: AbortSignal,
): Promise<WatchlistResponse> {
  return apiPut<WatchlistResponse>(
    `${BASE}/order`,
    { instrumentIds },
    { signal },
  )
}

export const watchlistKeys = {
  all: ['watchlist'] as const,
}
