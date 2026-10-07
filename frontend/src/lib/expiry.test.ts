import { expect, test } from 'vitest'
import { nearestExpiry } from './expiry'

test('returns the nearest expiry on or after today', () => {
  const today = new Date('2026-10-07T00:00:00')
  const dates = ['2026-10-30', '2026-09-25', '2026-11-27']
  expect(nearestExpiry(dates, today)).toBe('2026-10-30')
})

test('includes an expiry that is exactly today', () => {
  const today = new Date('2026-10-30T00:00:00')
  expect(nearestExpiry(['2026-10-30', '2026-11-27'], today)).toBe('2026-10-30')
})

test('falls back to the latest when all expiries are in the past', () => {
  const today = new Date('2026-10-07T00:00:00')
  expect(nearestExpiry(['2026-08-28', '2026-07-31'], today)).toBe('2026-08-28')
})

test('handles empty input', () => {
  expect(nearestExpiry([], new Date())).toBe('')
})
