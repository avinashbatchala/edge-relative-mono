import { describe, expect, it } from 'vitest'
import { deriveSystemAlerts, type SystemAlertInput } from '@/lib/system-alerts'

function input(overrides: Partial<SystemAlertInput> = {}): SystemAlertInput {
  return {
    health: 'UP',
    stream: 'open',
    engineStatus: 'UP',
    freshness: 'FRESH',
    persistenceDropped: 0,
    persistenceQueueDepth: 0,
    metricGaps: {},
    instruments: [{ symbol: 'SBIN', state: 'HEALTHY', quality: 'OK' }],
    counters: {
      warmupFailures: 0,
      missingDependencies: 0,
      alignmentFailures: 0,
      qualityDowngrades: 0,
    },
    ...overrides,
  }
}

describe('deriveSystemAlerts', () => {
  it('reports no alerts when everything is healthy', () => {
    expect(deriveSystemAlerts(input())).toEqual([])
  })

  it('flags an unreachable backend before anything else', () => {
    const alerts = deriveSystemAlerts(input({ health: null, stream: 'closed' }))
    expect(alerts[0]?.severity).toBe('BLOCKER')
    expect(alerts.map((a) => a.id)).toContain('stream.closed')
  })

  it('warns on stale data, degraded instruments and metric gaps', () => {
    const alerts = deriveSystemAlerts(
      input({
        freshness: 'STALE',
        instruments: [
          { symbol: 'SBIN', state: 'STALE', quality: 'STALE' },
          { symbol: 'NIFTY', state: 'HEALTHY', quality: 'OK' },
        ],
        metricGaps: { RRS_M5: 1 },
      }),
    )
    const ids = alerts.map((a) => a.id)
    expect(ids).toContain('freshness.stale')
    expect(ids).toContain('instruments.degraded')
    expect(ids).toContain('metric.RRS_M5')
  })

  it('orders blockers before warnings before info', () => {
    const alerts = deriveSystemAlerts(
      input({
        health: null,
        freshness: 'UNKNOWN',
        persistenceQueueDepth: 2,
        counters: {
          warmupFailures: 3,
          missingDependencies: 0,
          alignmentFailures: 0,
          qualityDowngrades: 0,
        },
      }),
    )
    const severities = alerts.map((a) => a.severity)
    expect(severities.indexOf('BLOCKER')).toBeLessThan(
      severities.indexOf('INFO'),
    )
    expect(severities.indexOf('WARNING')).toBeLessThan(
      severities.indexOf('INFO'),
    )
  })
})
