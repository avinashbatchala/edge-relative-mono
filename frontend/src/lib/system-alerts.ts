/**
 * Derived system alerts for the Operations workspace. These are computed client-side from the
 * signals that exist today (Actuator health, the feature stream, feature diagnostics and engine
 * counters). There is no server-side alert producer yet, so the UI labels the list as derived and
 * non-authoritative. Alerts never assert trading decisions.
 */

export type AlertSeverity = 'BLOCKER' | 'WARNING' | 'INFO'

export interface SystemAlert {
  id: string
  severity: AlertSeverity
  source: string
  message: string
}

export interface InstrumentHealth {
  symbol: string
  state: string
  quality: string
}

export interface SystemAlertInput {
  health: string | null
  stream: string | null
  engineStatus: string | null
  freshness: string | null
  persistenceDropped: number | null
  persistenceQueueDepth: number | null
  metricGaps: Record<string, number> | null
  instruments: InstrumentHealth[]
  counters: {
    warmupFailures: number
    missingDependencies: number
    alignmentFailures: number
    qualityDowngrades: number
  } | null
}

const SEVERITY_ORDER: Record<AlertSeverity, number> = {
  BLOCKER: 0,
  WARNING: 1,
  INFO: 2,
}

export function deriveSystemAlerts(input: SystemAlertInput): SystemAlert[] {
  const alerts: SystemAlert[] = []

  if (input.health === null) {
    alerts.push({
      id: 'health.offline',
      severity: 'BLOCKER',
      source: 'Backend',
      message: 'Backend health check failed — the API is unreachable.',
    })
  } else if (input.health.toUpperCase() !== 'UP') {
    alerts.push({
      id: 'health.degraded',
      severity: 'BLOCKER',
      source: 'Backend',
      message: `Backend health reports ${input.health}.`,
    })
  }

  if (input.stream === 'closed') {
    alerts.push({
      id: 'stream.closed',
      severity: 'BLOCKER',
      source: 'Feature stream',
      message:
        'Feature stream is offline; authoritatively-derived panels may be stale.',
    })
  } else if (
    input.stream === 'connecting' ||
    input.stream === 'reconnecting' ||
    input.stream === 'idle'
  ) {
    alerts.push({
      id: 'stream.pending',
      severity: 'WARNING',
      source: 'Feature stream',
      message: `Feature stream is ${input.stream}.`,
    })
  }

  if (
    input.engineStatus !== null &&
    input.engineStatus.toUpperCase() !== 'UP'
  ) {
    alerts.push({
      id: 'engine.status',
      severity: 'WARNING',
      source: 'Feature engine',
      message: `Feature engine reports ${input.engineStatus}.`,
    })
  }

  if (input.freshness === 'STALE') {
    alerts.push({
      id: 'freshness.stale',
      severity: 'WARNING',
      source: 'Market data',
      message: 'Feature data is stale against the configured freshness policy.',
    })
  } else if (input.freshness === 'UNKNOWN') {
    alerts.push({
      id: 'freshness.unknown',
      severity: 'INFO',
      source: 'Market data',
      message: 'Data freshness is unknown — no authoritative sample yet.',
    })
  }

  const degraded = input.instruments.filter(
    (instrument) => instrument.state.toUpperCase() !== 'HEALTHY',
  )
  if (degraded.length > 0) {
    alerts.push({
      id: 'instruments.degraded',
      severity: 'WARNING',
      source: 'Watchlist',
      message: `${degraded.length} of ${input.instruments.length} instruments are not healthy.`,
    })
  }

  for (const [metric, count] of Object.entries(input.metricGaps ?? {})) {
    alerts.push({
      id: `metric.${metric}`,
      severity: 'WARNING',
      source: 'Measurements',
      message: `${metric} unavailable for ${count} instrument(s).`,
    })
  }

  if ((input.persistenceDropped ?? 0) > 0) {
    alerts.push({
      id: 'persistence.dropped',
      severity: 'WARNING',
      source: 'Persistence',
      message: `${input.persistenceDropped} feature snapshot(s) dropped — analytical history is incomplete.`,
    })
  }

  if ((input.persistenceQueueDepth ?? 0) > 0) {
    alerts.push({
      id: 'persistence.queue',
      severity: 'INFO',
      source: 'Persistence',
      message: `${input.persistenceQueueDepth} feature snapshot(s) queued for persistence.`,
    })
  }

  if (input.counters) {
    const total =
      input.counters.warmupFailures +
      input.counters.missingDependencies +
      input.counters.alignmentFailures
    if (total > 0) {
      alerts.push({
        id: 'engine.failures',
        severity: 'INFO',
        source: 'Feature engine',
        message: `${total} cumulative calculation failure(s) since process start.`,
      })
    }
    if (input.counters.qualityDowngrades > 0) {
      alerts.push({
        id: 'engine.quality',
        severity: 'INFO',
        source: 'Feature engine',
        message: `${input.counters.qualityDowngrades} quality downgrade(s) since process start.`,
      })
    }
  }

  return alerts.sort(
    (a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity],
  )
}
