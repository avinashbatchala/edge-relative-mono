import { describe, expect, test } from 'vitest'
import { exchangeDateToInstant, resolveRange } from './market-time'

describe('exchangeDateToInstant', () => {
  test('anchors dates to the NSE session in Asia/Kolkata', () => {
    expect(exchangeDateToInstant('2026-09-19', false)).toBe(
      '2026-09-19T03:45:00.000Z',
    )
    expect(exchangeDateToInstant('2026-09-19', true)).toBe(
      '2026-09-19T10:00:00.000Z',
    )
  })
})

describe('resolveRange', () => {
  const now = new Date('2026-09-19T12:00:00.000Z')

  test('resolves a fixed lookback window', () => {
    const range = resolveRange('1D', null, null, now)
    expect(range?.end).toBe(now.toISOString())
    expect(range?.start).toBe('2026-09-18T12:00:00.000Z')
  })

  test('requires both custom dates', () => {
    expect(resolveRange('CUSTOM', '2026-01-01', null, now)).toBeNull()
    const range = resolveRange('CUSTOM', '2026-01-01', '2026-01-31', now)
    expect(range?.start).toBe('2026-01-01T03:45:00.000Z')
    expect(range?.end).toBe('2026-01-31T10:00:00.000Z')
  })
})
