/**
 * Derived system readiness for the workstation header. This is a client-side aggregation of the
 * signals that actually exist today (Actuator health status, the feature stream connection, feature
 * data freshness, and the exchange session). It is explicitly non-authoritative: until the backend
 * exposes a readiness/mode endpoint, the UI labels this as derived.
 */

export type ReadinessState = 'READY' | 'DEGRADED' | 'BLOCKED' | 'OFFLINE'

export interface ReadinessInput {
  /** Actuator health status, e.g. "UP" / "DOWN"; null when the request failed. */
  health: string | null
  /** Feature stream connection state. */
  stream: string | null
  /** Feature freshness state: FRESH | STALE | UNKNOWN. */
  freshness: string | null
  /** Exchange session context: PRE_OPEN | OPEN | CLOSED | NON_TRADING_DAY. */
  session: string | null
}

export interface Readiness {
  state: ReadinessState
  headline: string
  reasons: string[]
}

export function deriveReadiness(input: ReadinessInput): Readiness {
  const reasons: string[] = []

  if (input.health === null) {
    return {
      state: 'OFFLINE',
      headline: 'Backend unreachable',
      reasons: ['Health check failed'],
    }
  }
  if (input.health.toUpperCase() !== 'UP') {
    reasons.push(`Health reports ${input.health}`)
    return { state: 'BLOCKED', headline: 'System health degraded', reasons }
  }
  if (input.stream === 'closed') {
    reasons.push('Feature stream offline')
    return { state: 'BLOCKED', headline: 'Feature stream offline', reasons }
  }

  const marketClosed =
    input.session === 'CLOSED' || input.session === 'NON_TRADING_DAY'
  if (input.freshness === 'STALE') {
    reasons.push('Market data is stale')
    return { state: 'DEGRADED', headline: 'Market data stale', reasons }
  }
  if (input.freshness === 'UNKNOWN') {
    reasons.push('Data freshness unknown')
    return { state: 'DEGRADED', headline: 'Awaiting market data', reasons }
  }
  if (
    input.stream === 'connecting' ||
    input.stream === 'reconnecting' ||
    input.stream === 'idle'
  ) {
    reasons.push('Feature stream not connected')
    return { state: 'DEGRADED', headline: 'Feature stream connecting', reasons }
  }
  if (marketClosed) {
    return { state: 'READY', headline: 'Ready · market closed', reasons }
  }
  return { state: 'READY', headline: 'Ready', reasons }
}

export const READINESS_TONE: Record<ReadinessState, string> = {
  READY: 'text-positive',
  DEGRADED: 'text-amber-600 dark:text-amber-400',
  BLOCKED: 'text-negative',
  OFFLINE: 'text-negative',
}
