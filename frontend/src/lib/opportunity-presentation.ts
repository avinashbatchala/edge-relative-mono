/** Presentation for opportunity states. Setup qualification, risk outcome, and plan eligibility
 * are deliberately separate: a VALID setup is not an approval. */

export interface OpportunityStateMeta {
  label: string
  glyph: string
  tone: string
  blocking: boolean
}

const NEUTRAL = 'text-muted-foreground'
const AMBER = 'text-amber-600 dark:text-amber-400'
const RED = 'text-negative'
const GREEN = 'text-positive'

const SETUP_STATES: Record<string, OpportunityStateMeta> = {
  NO_SETUP: { label: 'No setup', glyph: '—', tone: NEUTRAL, blocking: false },
  NONE: { label: 'None', glyph: '—', tone: NEUTRAL, blocking: false },
  WATCH: { label: 'Watching', glyph: '◦', tone: NEUTRAL, blocking: false },
  FORMING: { label: 'Forming', glyph: '◔', tone: AMBER, blocking: false },
  NEAR_TRIGGER: {
    label: 'Near trigger',
    glyph: '◑',
    tone: AMBER,
    blocking: false,
  },
  VALID: {
    label: 'Valid (strategy-qualified)',
    glyph: '●',
    tone: GREEN,
    blocking: false,
  },
  INVALIDATED: { label: 'Invalidated', glyph: '✕', tone: RED, blocking: true },
  EXPIRED: { label: 'Expired', glyph: '◌', tone: NEUTRAL, blocking: false },
  MISSED: { label: 'Missed', glyph: '◌', tone: AMBER, blocking: false },
}

export function setupStateMeta(
  state: string | null | undefined,
): OpportunityStateMeta {
  return (
    SETUP_STATES[state ?? ''] ?? {
      label: state ?? 'Unknown',
      glyph: '•',
      tone: NEUTRAL,
      blocking: false,
    }
  )
}

const RISK_STATES: Record<string, OpportunityStateMeta> = {
  RISK_UNAVAILABLE: {
    label: 'Risk evaluation unavailable',
    glyph: '—',
    tone: NEUTRAL,
    blocking: false,
  },
  AWAITING_RISK_EVALUATION: {
    label: 'Awaiting risk evaluation',
    glyph: '◦',
    tone: NEUTRAL,
    blocking: false,
  },
  APPROVE: { label: 'Risk approved', glyph: '●', tone: GREEN, blocking: false },
  REDUCE: {
    label: 'Risk approved (reduced)',
    glyph: '◐',
    tone: AMBER,
    blocking: false,
  },
  REJECT: { label: 'Risk rejected', glyph: '✕', tone: RED, blocking: true },
  HALT_REQUIRED: {
    label: 'Halt required',
    glyph: '⛔',
    tone: RED,
    blocking: true,
  },
}

export function riskStateMeta(
  state: string | null | undefined,
): OpportunityStateMeta {
  return (
    RISK_STATES[state ?? ''] ?? {
      label: state ?? 'Unknown',
      glyph: '•',
      tone: NEUTRAL,
      blocking: false,
    }
  )
}

const PLAN_STATES: Record<string, OpportunityStateMeta> = {
  ELIGIBLE: { label: 'Eligible', glyph: '●', tone: GREEN, blocking: false },
  PENDING: {
    label: 'Pending confirmation',
    glyph: '◦',
    tone: AMBER,
    blocking: false,
  },
  UNKNOWN: {
    label: 'Eligibility unknown',
    glyph: '•',
    tone: NEUTRAL,
    blocking: false,
  },
  EXPIRED: { label: 'Expired', glyph: '◌', tone: NEUTRAL, blocking: false },
  INVALIDATED: { label: 'Invalidated', glyph: '✕', tone: RED, blocking: true },
  CANCELLED: { label: 'Cancelled', glyph: '—', tone: NEUTRAL, blocking: false },
  SUPERSEDED: {
    label: 'Superseded',
    glyph: '—',
    tone: NEUTRAL,
    blocking: false,
  },
}

export function planStateMeta(
  state: string | null | undefined,
): OpportunityStateMeta {
  return (
    PLAN_STATES[state ?? ''] ?? {
      label: state ?? '—',
      glyph: '•',
      tone: NEUTRAL,
      blocking: false,
    }
  )
}

export function directionLabel(direction: string | null | undefined): string {
  return direction === 'LONG' ? 'Long' : direction === 'SHORT' ? 'Short' : '—'
}
