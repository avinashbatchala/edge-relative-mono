import { describe, expect, test } from 'vitest'
import {
  computeSpread,
  formatAge,
  formatCompact,
  formatInr,
  formatIstTime,
  formatPercent,
  formatPrice,
  formatQuantity,
  formatSigned,
  movementClass,
} from './format'

describe('formatPrice', () => {
  test('formats grouped decimals', () => {
    expect(formatPrice(1428.35)).toBe('1,428.35')
    expect(formatPrice(0)).toBe('0.00')
  })

  test('renders unavailable values as an em dash, never NaN', () => {
    expect(formatPrice(null)).toBe('—')
    expect(formatPrice(undefined)).toBe('—')
    expect(formatPrice(Number.NaN)).toBe('—')
  })
})

describe('formatInr', () => {
  test('prefixes the rupee sign', () => {
    expect(formatInr(1428.35)).toBe('₹1,428.35')
    expect(formatInr(null)).toBe('—')
  })
})

describe('directional formatting', () => {
  test('adds signs and uses a true minus glyph', () => {
    expect(formatSigned(20.05)).toBe('+20.05')
    expect(formatSigned(-20.05)).toBe('−20.05')
    expect(formatPercent(1.42)).toBe('+1.42%')
    expect(formatPercent(-1.42)).toBe('−1.42%')
  })

  test('assigns semantic movement classes', () => {
    expect(movementClass(1)).toBe('text-positive')
    expect(movementClass(-1)).toBe('text-negative')
    expect(movementClass(0)).toBe('text-muted-foreground')
    expect(movementClass(null)).toBe('text-muted-foreground')
  })
})

describe('formatQuantity', () => {
  test('groups integers', () => {
    expect(formatQuantity(12420)).toBe('12,420')
    expect(formatQuantity(null)).toBe('—')
  })
})

describe('formatCompact', () => {
  test('produces a compact, non-numeric-garbage value', () => {
    const result = formatCompact(1_234_567)
    expect(result).not.toBe('—')
    expect(result).not.toContain('NaN')
  })
})

describe('IST time formatting', () => {
  test('renders exchange time in Asia/Kolkata regardless of host timezone', () => {
    // 09:02:08 UTC is 14:32:08 IST.
    expect(formatIstTime('2026-09-19T09:02:08Z')).toBe('14:32:08')
  })
})

describe('formatAge', () => {
  test('describes recency factually', () => {
    const now = Date.parse('2026-09-19T09:00:10.000Z')
    expect(formatAge('2026-09-19T09:00:08.800Z', now)).toBe('1.2s ago')
    expect(formatAge('2026-09-19T09:00:00.000Z', now)).toBe('10s ago')
    expect(formatAge('2026-09-19T08:55:00.000Z', now)).toBe('5m ago')
    expect(formatAge(null, now)).toBe('—')
  })
})

describe('computeSpread', () => {
  test('computes absolute and bps spread', () => {
    const spread = computeSpread(100, 101)
    expect(spread).not.toBeNull()
    expect(spread?.absolute).toBeCloseTo(1)
    expect(spread?.bps).toBeCloseTo(99.5, 1)
  })

  test('returns null when a side is missing', () => {
    expect(computeSpread(null, 101)).toBeNull()
    expect(computeSpread(100, null)).toBeNull()
    expect(computeSpread(0, 101)).toBeNull()
  })
})
