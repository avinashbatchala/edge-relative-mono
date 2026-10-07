import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'

/** A snapshot of the scanner board's filter, column and sort state. */
export interface ScannerScreenState {
  search: string
  timeframe: string
  rs: string
  rve: string
  alignment: string
  quality: string
  freshness: string
  minRvol: string
  visible: Record<string, boolean>
  sortKey: string
  sortDir: 'asc' | 'desc'
}

export interface ScannerScreen {
  name: string
  state: ScannerScreenState
}

/** Saved scanner screens, persisted per browser (single-user, localhost). */
export const useScannerScreensStore = defineStore('scanner-screens', () => {
  const screens = useStorage<ScannerScreen[]>('er.scanner.screens', [])

  function save(name: string, state: ScannerScreenState) {
    const trimmed = name.trim()
    if (!trimmed) {
      return
    }
    const index = screens.value.findIndex((s) => s.name === trimmed)
    if (index >= 0) {
      screens.value[index] = { name: trimmed, state }
    } else {
      screens.value.push({ name: trimmed, state })
    }
  }

  function remove(name: string) {
    screens.value = screens.value.filter((s) => s.name !== name)
  }

  function get(name: string): ScannerScreenState | null {
    return screens.value.find((s) => s.name === name)?.state ?? null
  }

  return { screens, save, remove, get }
})
