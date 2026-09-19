import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ApiError } from '@/api/http'
import { getQuote, marketDataKeys, type QuoteRequest } from '@/api/market-data'
import type { BrokerExchange, BrokerSegment } from '@/api/types'

export interface QuoteSubject {
  exchange: string
  segment: string | null
  symbol: string
}

/**
 * Per-instrument quote query. Isolated by query key so one failing instrument does not affect
 * others, and shared across views (Overview/Watchlist) via the cache.
 */
export function useQuote(subject: Ref<QuoteSubject>) {
  const request = computed<QuoteRequest | null>(() => {
    const value = subject.value
    if (!value.segment) {
      return null
    }
    return {
      exchange: value.exchange as BrokerExchange,
      segment: value.segment as BrokerSegment,
      tradingSymbol: value.symbol,
    }
  })

  return useQuery(() => ({
    queryKey: request.value
      ? marketDataKeys.quote(request.value)
      : ([...marketDataKeys.all, 'quote', 'none'] as const),
    queryFn: ({ signal }) => getQuote(request.value as QuoteRequest, signal),
    enabled: request.value !== null,
    refetchInterval: 15_000,
    staleTime: 5_000,
    retry: (failureCount: number, error: unknown) => {
      if (
        error instanceof ApiError &&
        (error.isRateLimited || error.isAuthFailure)
      ) {
        return false
      }
      return failureCount < 1
    },
  }))
}
