import { expect, test } from 'vitest'
import { useCommandPalette } from './useCommandPalette'

test('exposes a shared open state', () => {
  const palette = useCommandPalette()
  palette.hide()
  expect(palette.open.value).toBe(false)

  palette.toggle()
  expect(palette.open.value).toBe(true)

  palette.hide()
  expect(palette.open.value).toBe(false)
})
