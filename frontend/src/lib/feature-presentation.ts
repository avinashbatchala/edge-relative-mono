/**
 * Single source of truth for feature presentation: state/quality labels, semantic tones,
 * direction and structure mapping, and per-metric precision. Components must not re-derive
 * enum-to-label or enum-to-colour logic locally.
 */

export interface StateMeta {
  label: string
  glyph: string
  tone: string
  description: string
}

export const FEATURE_STATES = [
  'HEALTHY',
  'WARMING_UP',
  'STALE',
  'DEGRADED',
  'INVALID',
  'UNAVAILABLE',
] as const

export type FeatureState = (typeof FEATURE_STATES)[number]

const STATE_META: Record<FeatureState, StateMeta> = {
  HEALTHY: {
    label: 'Healthy',
    glyph: '●',
    tone: 'border-positive/40 text-positive',
    description: 'Required features are present and trustworthy.',
  },
  WARMING_UP: {
    label: 'Warming up',
    glyph: '◐',
    tone: 'border-amber-500/40 text-amber-600 dark:text-amber-400',
    description: 'Not enough history yet for a finalized value.',
  },
  STALE: {
    label: 'Stale',
    glyph: '◌',
    tone: 'border-amber-500/40 text-amber-600 dark:text-amber-400',
    description: 'The observation is older than the current session.',
  },
  DEGRADED: {
    label: 'Degraded',
    glyph: '▲',
    tone: 'border-orange-500/40 text-orange-600 dark:text-orange-400',
    description: 'A value exists but an input is not fully trustworthy.',
  },
  INVALID: {
    label: 'Invalid',
    glyph: '✕',
    tone: 'border-negative/40 text-negative',
    description: 'A required input or the calculation is invalid.',
  },
  UNAVAILABLE: {
    label: 'Unavailable',
    glyph: '—',
    tone: 'border-muted-foreground/40 text-muted-foreground',
    description: 'No value can be produced for this instrument or metric.',
  },
}

export function featureStateMeta(state: string | null | undefined): StateMeta {
  if (state && state in STATE_META) {
    return STATE_META[state as FeatureState]
  }
  return {
    label: state ?? 'Unknown',
    glyph: '•',
    tone: 'border-muted-foreground/40 text-muted-foreground',
    description: 'Unknown feature state.',
  }
}

export function featureStateRank(state: string): number {
  const index = FEATURE_STATES.indexOf(state as FeatureState)
  return index < 0 ? FEATURE_STATES.length : index
}

/** Canonical quality vocabulary (mirrors the backend feature quality enum). */
export const QUALITY_RANK: Record<string, number> = {
  GOOD: 0,
  CORRECTED: 1,
  DEGRADED: 2,
  SUSPECT: 3,
  STALE: 4,
  INCOMPLETE: 5,
  UNAVAILABLE: 6,
}

export function qualityRank(quality: string): number {
  return QUALITY_RANK[quality] ?? 7
}

export function isTrustworthy(quality: string, availability: string): boolean {
  return (
    (quality === 'GOOD' || quality === 'CORRECTED') && availability === 'VALID'
  )
}

export function isStale(quality: string, availability: string): boolean {
  return availability === 'STALE' || quality === 'STALE'
}

/** Combine quality and availability into the single presented state (mirrors the backend). */
export function presentationState(
  quality: string,
  availability: string,
): FeatureState {
  if (quality === 'UNAVAILABLE' || availability === 'MISSING_INPUT') {
    return 'UNAVAILABLE'
  }
  if (
    availability === 'WARMING_UP' ||
    availability === 'INSUFFICIENT_HISTORY'
  ) {
    return 'WARMING_UP'
  }
  if (availability === 'STALE' || quality === 'STALE') {
    return 'STALE'
  }
  if (availability === 'INVALID') {
    return 'INVALID'
  }
  if (availability !== 'VALID' || !isTrustworthy(quality, availability)) {
    return 'DEGRADED'
  }
  return 'HEALTHY'
}

const AVAILABILITY_LABELS: Record<string, string> = {
  VALID: 'Available',
  WARMING_UP: 'Warming up',
  INSUFFICIENT_HISTORY: 'Insufficient history',
  MISSING_INPUT: 'Missing input',
  STALE: 'Stale',
  INCOMPLETE: 'Incomplete',
  INVALID: 'Invalid',
  NOT_APPLICABLE: 'Not applicable',
}

export function availabilityLabel(availability: string): string {
  if (availability in AVAILABILITY_LABELS) {
    return AVAILABILITY_LABELS[availability] as string
  }
  const spaced = availability.replace(/_/g, ' ').toLowerCase()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

export type Direction = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL'

export interface DirectionMeta {
  label: string
  glyph: string
  tone: string
}

const DIRECTION_META: Record<Direction, DirectionMeta> = {
  POSITIVE: { label: 'Positive', glyph: '▲', tone: 'text-positive' },
  NEGATIVE: { label: 'Negative', glyph: '▼', tone: 'text-negative' },
  NEUTRAL: { label: 'Neutral', glyph: '■', tone: 'text-muted-foreground' },
}

export function directionOf(value: number | null | undefined): Direction {
  if (value === null || value === undefined) {
    return 'NEUTRAL'
  }
  if (value > 0) {
    return 'POSITIVE'
  }
  if (value < 0) {
    return 'NEGATIVE'
  }
  return 'NEUTRAL'
}

export function directionMeta(direction: Direction): DirectionMeta {
  return DIRECTION_META[direction]
}

export function directionMetaOf(
  value: number | null | undefined,
): DirectionMeta {
  return DIRECTION_META[directionOf(value)]
}

/** Backend emits POSITIVE/NEGATIVE/NEUTRAL; map to the canonical direction meta. */
export function directionMetaFromState(
  state: string | null | undefined,
): DirectionMeta | null {
  if (state === 'POSITIVE' || state === 'NEGATIVE' || state === 'NEUTRAL') {
    return DIRECTION_META[state]
  }
  return null
}

export interface StructureMeta {
  label: string
  glyph: string
  tone: string
}

export function structureMeta(state: string | null | undefined): StructureMeta {
  switch (state) {
    case 'BULL_STRUCTURE':
      return { label: 'Bull', glyph: '▲', tone: 'text-positive' }
    case 'BEAR_STRUCTURE':
      return { label: 'Bear', glyph: '▼', tone: 'text-negative' }
    case 'MIXED':
      return { label: 'Mixed', glyph: '■', tone: 'text-muted-foreground' }
    default:
      return { label: '—', glyph: '', tone: 'text-muted-foreground' }
  }
}

export type RveState = 'EXPANDING' | 'CONTRACTING' | 'STABLE' | 'UNAVAILABLE'

export function rveStateOf(value: number | null | undefined): RveState {
  if (value === null || value === undefined) {
    return 'UNAVAILABLE'
  }
  if (value > 0) {
    return 'EXPANDING'
  }
  if (value < 0) {
    return 'CONTRACTING'
  }
  return 'STABLE'
}

const RVE_META: Record<RveState, StateMeta> = {
  EXPANDING: {
    label: 'Expanding',
    glyph: '▲',
    tone: 'text-positive',
    description: 'Relative participation is expanding.',
  },
  CONTRACTING: {
    label: 'Contracting',
    glyph: '▼',
    tone: 'text-negative',
    description: 'Relative participation is contracting.',
  },
  STABLE: {
    label: 'Stable',
    glyph: '■',
    tone: 'text-muted-foreground',
    description: 'Relative participation is stable.',
  },
  UNAVAILABLE: {
    label: 'Unavailable',
    glyph: '—',
    tone: 'text-muted-foreground',
    description: 'RVE is not available for this observation.',
  },
}

export function rveMeta(value: number | null | undefined): StateMeta {
  return RVE_META[rveStateOf(value)]
}

/** Consistent decimal precision by metric, so columns do not shift or imply false accuracy. */
const METRIC_DECIMALS: Record<string, number> = {
  LAST_PRICE: 2,
  PRICE_CHANGE: 2,
  PRICE_CHANGE_PERCENT: 2,
  RRS_RAW: 2,
  RRS_FAST: 2,
  RRS_SLOW: 2,
  RRS_PERSISTENCE: 2,
  RVOL_INTERVAL: 2,
  RVOL_CUMULATIVE: 2,
  RVE: 3,
  ATR: 2,
  ATR_PERCENT: 2,
  VWAP_DISTANCE_ATR: 2,
  SECTOR_RRS_RAW: 2,
}

export function metricDecimals(metric: string, fallback = 2): number {
  return METRIC_DECIMALS[metric] ?? fallback
}

const METRIC_LABELS: Record<string, string> = {
  ATR: 'ATR',
  ATR_PERCENT: 'ATR %',
  RRS_RAW: 'Relative strength',
  RRS_FAST: 'RRS fast',
  RRS_SLOW: 'RRS slow',
  RRS_PERSISTENCE: 'RRS persistence',
  RRS_PERCENTILE: 'RRS percentile',
  RRS_TREND_STATE: 'RRS trend',
  RRS_VS_SECTOR_RAW: 'Strength vs sector',
  SECTOR_RRS_RAW: 'Sector strength',
  RVOL_D1: 'Daily relative volume',
  RVOL_INTERVAL: 'Relative volume',
  RVOL_CUMULATIVE: 'Cumulative RVOL',
  RVE: 'Volume expansion',
  VWAP_DISTANCE_ATR: 'VWAP distance',
  MARKET_PRICE_STRUCTURE: 'Market structure',
  SECTOR_PRICE_STRUCTURE: 'Sector structure',
  DAILY_RRS_STATE: 'Daily relative strength',
}

/** Human-readable metric name; never show a raw backend feature key in the UI. */
export function metricLabel(metric: string): string {
  if (metric in METRIC_LABELS) {
    return METRIC_LABELS[metric] as string
  }
  const spaced = metric.replace(/_/g, ' ').toLowerCase()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

/** "RRS_V1@ab12cd" -> "v1"; unknown shapes fall back to the base token. */
export function formatFeatureVersion(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const base = value.includes('@') ? value.slice(0, value.indexOf('@')) : value
  const match = base.match(/_V(\d+)$/)
  return match ? `v${match[1]}` : base
}

export function featureVersionTitle(value: string | null | undefined): string {
  return value ? `Feature definition ${value}` : 'Feature version unavailable'
}
