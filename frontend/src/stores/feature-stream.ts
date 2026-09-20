import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { FeatureDashboardRow } from '@/api/features'

export type StreamConnection =
  'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed'

export interface FeatureStreamEnvelope {
  type: string
  version: number
  sequence: number
  payload: unknown
}

export type MergeOutcome =
  'snapshot' | 'applied' | 'duplicate' | 'out-of-order' | 'gap' | 'ignored'

const ENVELOPE_VERSION = 1

function rowKey(row: { instrumentId: number; timeframe: string }): string {
  return `${row.instrumentId}:${row.timeframe}`
}

function toRows(payload: unknown): FeatureDashboardRow[] {
  if (Array.isArray(payload)) {
    return payload as FeatureDashboardRow[]
  }
  if (payload && typeof payload === 'object') {
    return [payload] as FeatureDashboardRow[]
  }
  return []
}

/**
 * Consolidated real-time feature state for the dashboard. The server sequence is authoritative:
 * duplicates and older messages are ignored, and a gap flags the stream for an authoritative
 * resync instead of silently rendering a hole.
 */
export const useFeatureStreamStore = defineStore('feature-stream', () => {
  const order = ref<string[]>([])
  const rows = ref<Record<string, FeatureDashboardRow>>({})
  const connection = ref<StreamConnection>('idle')
  const lastSequence = ref<number | null>(null)
  const lastUpdatedAt = ref<string | null>(null)
  const lastErrorMessage = ref<string | null>(null)
  const gapDetected = ref(false)

  const rowList = computed(() =>
    order.value
      .map((key) => rows.value[key])
      .filter((row): row is FeatureDashboardRow => Boolean(row)),
  )
  const isLive = computed(() => connection.value === 'open')

  function setConnection(next: StreamConnection) {
    connection.value = next
  }

  function setError(message: string | null) {
    lastErrorMessage.value = message
  }

  function setAuthoritative(
    nextRows: FeatureDashboardRow[],
    sequence?: number,
  ) {
    const byKey: Record<string, FeatureDashboardRow> = {}
    const nextOrder: string[] = []
    for (const row of nextRows) {
      const key = rowKey(row)
      if (!(key in byKey)) {
        nextOrder.push(key)
      }
      byKey[key] = row
    }
    rows.value = byKey
    order.value = nextOrder
    if (typeof sequence === 'number') {
      lastSequence.value = sequence
    }
    lastUpdatedAt.value = new Date().toISOString()
    gapDetected.value = false
  }

  function mergeRows(nextRows: FeatureDashboardRow[]) {
    const byKey = { ...rows.value }
    const nextOrder = [...order.value]
    for (const row of nextRows) {
      const key = rowKey(row)
      if (!(key in byKey)) {
        nextOrder.push(key)
      }
      byKey[key] = row
    }
    rows.value = byKey
    order.value = nextOrder
    lastUpdatedAt.value = new Date().toISOString()
  }

  function applyEnvelope(envelope: FeatureStreamEnvelope): MergeOutcome {
    if (!envelope || envelope.version !== ENVELOPE_VERSION) {
      return 'ignored'
    }
    if (envelope.type === 'feature.snapshot') {
      setAuthoritative(toRows(envelope.payload), envelope.sequence)
      return 'snapshot'
    }
    if (envelope.type !== 'feature.update') {
      return 'ignored'
    }
    const previous = lastSequence.value
    if (previous !== null && envelope.sequence <= previous) {
      return envelope.sequence === previous ? 'duplicate' : 'out-of-order'
    }
    if (previous !== null && envelope.sequence > previous + 1) {
      gapDetected.value = true
      return 'gap'
    }
    mergeRows(toRows(envelope.payload))
    lastSequence.value = envelope.sequence
    gapDetected.value = false
    return 'applied'
  }

  function reset() {
    order.value = []
    rows.value = {}
    lastSequence.value = null
    lastUpdatedAt.value = null
    gapDetected.value = false
  }

  return {
    order,
    rows,
    connection,
    lastSequence,
    lastUpdatedAt,
    lastErrorMessage,
    gapDetected,
    rowList,
    isLive,
    setConnection,
    setError,
    setAuthoritative,
    mergeRows,
    applyEnvelope,
    reset,
  }
})
