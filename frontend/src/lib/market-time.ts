import type { BrokerCandleInterval } from '@/api/types'
import type { RangeKey } from '@/stores/market-data'

export interface IntervalOption {
  value: BrokerCandleInterval
  label: string
}

/** Only intervals supported by the backend BrokerCandleInterval enum. */
export const INTERVAL_OPTIONS: IntervalOption[] = [
  { value: 'ONE_MINUTE', label: '1m' },
  { value: 'FIVE_MINUTE', label: '5m' },
  { value: 'FIFTEEN_MINUTE', label: '15m' },
  { value: 'THIRTY_MINUTE', label: '30m' },
  { value: 'ONE_HOUR', label: '1h' },
  { value: 'ONE_DAY', label: '1D' },
]

export const RANGE_OPTIONS: { value: RangeKey; label: string }[] = [
  { value: '1D', label: '1D' },
  { value: '5D', label: '5D' },
  { value: '1M', label: '1M' },
  { value: '3M', label: '3M' },
  { value: '6M', label: '6M' },
  { value: '1Y', label: '1Y' },
]

const RANGE_DAYS: Record<Exclude<RangeKey, 'CUSTOM'>, number> = {
  '1D': 1,
  '5D': 5,
  '1M': 30,
  '3M': 90,
  '6M': 180,
  '1Y': 365,
}

export interface InstantRange {
  start: string
  end: string
}

const IST_OFFSET = '+05:30'

/** Custom calendar dates are NSE trading dates, interpreted in Asia/Kolkata. */
export function exchangeDateToInstant(date: string, endOfDay: boolean): string {
  const time = endOfDay ? '15:30:00' : '09:15:00'
  return new Date(`${date}T${time}${IST_OFFSET}`).toISOString()
}

export function resolveRange(
  range: RangeKey,
  customStart: string | null,
  customEnd: string | null,
  now: Date = new Date(),
): InstantRange | null {
  if (range === 'CUSTOM') {
    if (!customStart || !customEnd) {
      return null
    }
    return {
      start: exchangeDateToInstant(customStart, false),
      end: exchangeDateToInstant(customEnd, true),
    }
  }
  const days = RANGE_DAYS[range]
  const start = new Date(now.getTime() - days * 24 * 60 * 60 * 1000)
  return { start: start.toISOString(), end: now.toISOString() }
}
