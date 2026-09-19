import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { refDebounced } from '@vueuse/core'
import { listInstruments, marketDataKeys } from '@/api/market-data'

/**
 * Debounced, bounded instrument search backed by the server (the master has ~140k rows).
 * Queries once the operator has typed enough to narrow it, or immediately when the caller asks
 * (e.g. a selector just opened).
 */
export function useInstrumentSearch(
  search: Ref<string>,
  enabled: Ref<boolean>,
) {
  const debounced = refDebounced(search, 250)
  const term = computed(() => debounced.value.trim())

  const query = useQuery(() => {
    const value = term.value
    const searching = value.length >= 2
    return {
      queryKey: [...marketDataKeys.instruments(), value] as const,
      queryFn: ({ signal }) =>
        listInstruments(searching ? value : undefined, 50, signal),
      enabled: enabled.value || searching,
      staleTime: 5 * 60 * 1000,
      gcTime: 30 * 60 * 1000,
      retry: 1,
    }
  })

  return { query, term }
}
