import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, test } from 'vitest'
import { usePreferencesStore } from './preferences'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
})

test('defaults to comfortable density and toggles', () => {
  const prefs = usePreferencesStore()

  expect(prefs.density).toBe('comfortable')
  expect(prefs.isCompact).toBe(false)

  prefs.toggleDensity()

  expect(prefs.density).toBe('compact')
  expect(prefs.isCompact).toBe(true)
})
