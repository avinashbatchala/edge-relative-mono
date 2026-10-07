import { ref, watch } from 'vue'
import { useFeatureStreamStore } from '@/stores/feature-stream'

export type AttentionSeverity = 'INFO' | 'ATTENTION' | 'WARNING'

export interface AttentionEvent {
  id: string
  at: string
  severity: AttentionSeverity
  symbol: string
  message: string
}

interface PreviousState {
  rrsTrendState: string | null
  marketState: string | null
  availability: string
}

/**
 * Derives an attention feed from authoritative feature-stream transitions. The browser has no
 * server-side event feed, so this diffs successive authoritative rows and records meaningful state
 * changes (RRS trend, market structure, data availability). Bounded and deduplicated by identity.
 */
export function useAttentionFeed(limit = 20) {
  const store = useFeatureStreamStore()
  const events = ref<AttentionEvent[]>([])
  const previous = new Map<string, PreviousState>()
  let counter = 0

  function record(
    row: { symbol: string; observationTime: string | null },
    severity: AttentionSeverity,
    message: string,
  ): AttentionEvent {
    counter += 1
    return {
      id: `${row.symbol}:${counter}`,
      at: row.observationTime ?? new Date().toISOString(),
      severity,
      symbol: row.symbol,
      message,
    }
  }

  watch(
    () => store.rowList,
    (rows) => {
      const additions: AttentionEvent[] = []
      for (const row of rows) {
        const key = String(row.instrumentId)
        const prior = previous.get(key)
        if (prior) {
          if (row.rrsTrendState && prior.rrsTrendState !== row.rrsTrendState) {
            additions.push(
              record(
                row,
                'ATTENTION',
                `RRS trend ${prior.rrsTrendState ?? '—'} → ${row.rrsTrendState}`,
              ),
            )
          }
          if (row.marketState && prior.marketState !== row.marketState) {
            additions.push(
              record(
                row,
                'ATTENTION',
                `market ${prior.marketState ?? '—'} → ${row.marketState}`,
              ),
            )
          }
          if (
            prior.availability !== row.availability &&
            row.availability !== 'VALID'
          ) {
            additions.push(
              record(row, 'WARNING', `data ${row.availability.toLowerCase()}`),
            )
          }
        }
        previous.set(key, {
          rrsTrendState: row.rrsTrendState,
          marketState: row.marketState,
          availability: row.availability,
        })
      }
      if (additions.length) {
        events.value = [...additions.reverse(), ...events.value].slice(0, limit)
      }
    },
    { flush: 'post' },
  )

  return { events }
}
