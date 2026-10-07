import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, test } from 'vitest'
import {
  useScannerScreensStore,
  type ScannerScreenState,
} from './scanner-screens'

function state(
  overrides: Partial<ScannerScreenState> = {},
): ScannerScreenState {
  return {
    search: '',
    timeframe: 'all',
    rs: 'all',
    rve: 'all',
    alignment: 'all',
    quality: 'all',
    freshness: 'all',
    minRvol: '',
    visible: {},
    sortKey: 'rrsAbs',
    sortDir: 'desc',
    ...overrides,
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
})

test('saves, overwrites and removes screens', () => {
  const store = useScannerScreensStore()

  store.save('Momentum', state({ rs: 'positive' }))
  expect(store.screens).toHaveLength(1)
  expect(store.get('Momentum')?.rs).toBe('positive')

  store.save('Momentum', state({ rs: 'negative' }))
  expect(store.screens).toHaveLength(1)
  expect(store.get('Momentum')?.rs).toBe('negative')

  store.remove('Momentum')
  expect(store.screens).toHaveLength(0)
  expect(store.get('Momentum')).toBeNull()
})

test('ignores blank names', () => {
  const store = useScannerScreensStore()
  store.save('   ', state())
  expect(store.screens).toHaveLength(0)
})
