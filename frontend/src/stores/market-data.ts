import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { BrokerCandleInterval, BrokerInstrument } from '@/api/types'

export type RangeKey = '1D' | '5D' | '1M' | '3M' | '6M' | '1Y' | 'CUSTOM'

/**
 * Client-side workstation preferences only. Server state (quotes, candles, depth) lives in
 * TanStack Query, never here.
 */
export const useMarketDataStore = defineStore('market-data', () => {
  const selectedInstrument = ref<BrokerInstrument | null>(null)
  const interval = ref<BrokerCandleInterval>('FIVE_MINUTE')
  const range = ref<RangeKey>('1D')
  const customStart = ref<string | null>(null)
  const customEnd = ref<string | null>(null)

  function selectInstrument(instrument: BrokerInstrument | null) {
    selectedInstrument.value = instrument
  }

  function setRange(next: RangeKey) {
    range.value = next
  }

  function setCustomRange(start: string | null, end: string | null) {
    customStart.value = start
    customEnd.value = end
    range.value = 'CUSTOM'
  }

  return {
    selectedInstrument,
    interval,
    range,
    customStart,
    customEnd,
    selectInstrument,
    setRange,
    setCustomRange,
  }
})
