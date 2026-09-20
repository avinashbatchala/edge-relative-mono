const EM_DASH = '—'

const istDateTime = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

const istTime = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

const istDate = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
  month: 'short',
  day: '2-digit',
})

const groupedInteger = new Intl.NumberFormat('en-IN', {
  maximumFractionDigits: 0,
})

const compactNumber = new Intl.NumberFormat('en-IN', {
  notation: 'compact',
  maximumFractionDigits: 2,
})

export function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function toNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  const numeric = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

/** Price with fixed 2 decimals, grouped, no currency symbol. */
export function formatPrice(
  value: number | string | null | undefined,
  decimals = 2,
): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return EM_DASH
  }
  return new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(numeric)
}

/** Price prefixed with the rupee sign. */
export function formatInr(
  value: number | string | null | undefined,
  decimals = 2,
): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return EM_DASH
  }
  return `₹${formatPrice(numeric, decimals)}`
}

export function formatSigned(
  value: number | string | null | undefined,
  decimals = 2,
): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return EM_DASH
  }
  const sign = numeric > 0 ? '+' : numeric < 0 ? '−' : ''
  return `${sign}${formatPrice(Math.abs(numeric), decimals)}`
}

export function formatPercent(
  value: number | string | null | undefined,
  decimals = 2,
): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return EM_DASH
  }
  const sign = numeric > 0 ? '+' : numeric < 0 ? '−' : ''
  return `${sign}${formatPrice(Math.abs(numeric), decimals)}%`
}

export function formatQuantity(
  value: number | string | null | undefined,
): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return EM_DASH
  }
  return groupedInteger.format(numeric)
}

export function formatCompact(
  value: number | string | null | undefined,
): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return EM_DASH
  }
  return compactNumber.format(numeric)
}

export function formatIstDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return EM_DASH
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return EM_DASH
  }
  return istDateTime.format(date)
}

export function formatIstTime(iso: string | null | undefined): string {
  if (!iso) {
    return EM_DASH
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return EM_DASH
  }
  return istTime.format(date)
}

export function formatIstDate(iso: string | null | undefined): string {
  if (!iso) {
    return EM_DASH
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return EM_DASH
  }
  return istDate.format(date)
}

/** Factual recency, not a staleness judgement (the backend owns quality). */
export function formatAge(
  iso: string | null | undefined,
  now: number = Date.now(),
): string {
  if (!iso) {
    return EM_DASH
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return EM_DASH
  }
  const seconds = Math.max(0, (now - date.getTime()) / 1000)
  if (seconds < 1) {
    return 'just now'
  }
  if (seconds < 60) {
    return `${seconds.toFixed(seconds < 10 ? 1 : 0)}s ago`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes}m ago`
  }
  const hours = Math.floor(minutes / 60)
  return `${hours}h ago`
}

/** Age from a duration in seconds, for values the backend already expresses as an age. */
export function formatAgeSeconds(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) {
    return EM_DASH
  }
  const value = Math.max(0, seconds)
  if (value < 60) {
    return `${value.toFixed(value < 10 ? 1 : 0)}s`
  }
  const minutes = Math.floor(value / 60)
  if (minutes < 60) {
    return `${minutes}m`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 48) {
    return `${hours}h`
  }
  return `${Math.floor(hours / 24)}d`
}

export interface Spread {
  absolute: number
  bps: number
}

export function computeSpread(
  bid: number | null | undefined,
  ask: number | null | undefined,
): Spread | null {
  const bidValue = toNumber(bid)
  const askValue = toNumber(ask)
  if (
    bidValue === null ||
    askValue === null ||
    bidValue <= 0 ||
    askValue <= 0
  ) {
    return null
  }
  const absolute = askValue - bidValue
  const mid = (askValue + bidValue) / 2
  return { absolute, bps: mid > 0 ? (absolute / mid) * 10_000 : 0 }
}

export function movementClass(
  value: number | string | null | undefined,
): string {
  const numeric = toNumber(value)
  if (numeric === null || numeric === 0) {
    return 'text-muted-foreground'
  }
  return numeric > 0 ? 'text-positive' : 'text-negative'
}
